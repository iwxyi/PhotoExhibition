package com.photoexhibition.service;

import com.photoexhibition.dto.AlbumMoveResult;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PhotoAIScoringRepository;
import com.photoexhibition.repository.PhotoAssignmentRepository;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlbumMoveService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final FaceRepository faceRepository;
    private final PhotoAIScoringRepository photoAIScoringRepository;
    private final PhotoAssignmentRepository photoAssignmentRepository;
    private final UserPathService userPathService;

    /**
     * 获取分类列表（base-path下的一级目录）
     */
    public List<Map<String, String>> getCategories(UserAccount currentUser) {
        Path base = getScopedRoot(currentUser);
        List<Map<String, String>> categories = new ArrayList<>();
        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(Files::isDirectory)
                  .filter(p -> !p.getFileName().toString().startsWith("."))
                  .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                  .forEach(p -> {
                      Map<String, String> cat = new HashMap<>();
                      cat.put("name", p.getFileName().toString());
                      cat.put("path", toClientPath(p));
                      categories.add(cat);
                  });
        } catch (IOException e) {
            log.error("列出分类目录失败", e);
        }
        return categories;
    }

    /**
     * 获取相册的同级目录（同一父目录下的其他目录，用于合并至同级）
     */
    public List<Map<String, String>> getSiblingDirectories(UserAccount currentUser, Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));
        assertAlbumAccessible(currentUser, album);
        Path albumPath = resolveAlbumPath(album);
        ensureWithinScope(albumPath, currentUser);
        Path parentPath = albumPath.getParent();

        List<Map<String, String>> dirs = new ArrayList<>();
        if (parentPath == null || !Files.isDirectory(parentPath)) return dirs;

        try (Stream<Path> stream = Files.list(parentPath)) {
            stream.filter(Files::isDirectory)
                  .filter(p -> !p.getFileName().toString().startsWith("."))
                  .filter(p -> !p.equals(albumPath)) // 排除自身
                  .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                  .forEach(p -> {
                      Map<String, String> dir = new HashMap<>();
                      dir.put("name", p.getFileName().toString());
                      dir.put("path", toClientPath(p));
                      dirs.add(dir);
                  });
        } catch (IOException e) {
            log.error("列出同级目录失败: {}", parentPath, e);
        }
        return dirs;
    }

    /**
     * 合并相册到同级目录（将源相册的所有照片移动到目标目录）
     */
    @Transactional
    public Map<String, Object> mergeAlbum(UserAccount currentUser, Long albumId, String targetPathStr) {
        Map<String, Object> result = new HashMap<>();

        Album sourceAlbum = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));
        assertAlbumAccessible(currentUser, sourceAlbum);

        Path sourcePath = resolveAlbumPath(sourceAlbum);
        Path targetPath = resolveRequestedPathWithinScope(targetPathStr, currentUser);

        // 验证
        if (!Files.isDirectory(sourcePath)) {
            result.put("success", false);
            result.put("message", "源目录不存在: " + toClientPath(sourcePath));
            return result;
        }
        if (!Files.isDirectory(targetPath)) {
            result.put("success", false);
            result.put("message", "目标目录不存在: " + toClientPath(targetPath));
            return result;
        }
        if (sourcePath.equals(targetPath)) {
            result.put("success", false);
            result.put("message", "源路径与目标路径相同");
            return result;
        }

        // 获取源相册的所有照片
        List<Photo> sourcePhotos = loadAllPhotosByAlbumId(albumId);
        int photoCount = sourcePhotos.size();

        if (photoCount == 0) {
            result.put("success", false);
            result.put("message", "源相册中没有照片");
            return result;
        }

        try {
            // 0. 标准化路径（统一使用 / 分隔符）
            String normalizedSourcePath = sourcePath.toString().replace("\\", "/");
            String normalizedTargetPath = targetPath.toString().replace("\\", "/");

            // 先获取目标相册（用于更新照片的albumId）
            Album targetAlbum = findAccessibleAlbumByNormalizedPath(currentUser, normalizedTargetPath).orElse(null);
            Long targetAlbumId = targetAlbum != null ? targetAlbum.getId() : null;

            // 1. 将源相册的所有文件移动到目标目录
            moveDirectoryContents(sourcePath, targetPath);

            // 2. 删除空的源目录
            Files.deleteIfExists(sourcePath);

            // 3. 更新数据库中的路径（从源路径改为目标路径）
            String oldPrefix = normalizedSourcePath;
            String newPrefix = normalizedTargetPath;

            for (Photo p : sourcePhotos) {
                p.setOriginalPath(rewriteStoredPathForMerge(p.getOriginalPath(), sourcePath, targetPath, p.getUserId()));
                p.setPathHash(computeSha256(p.getOriginalPath()));
                p.setThumbnailPath(rewriteStoredPathForMerge(p.getThumbnailPath(), sourcePath, targetPath, p.getUserId()));
                p.setWebpPath(rewriteStoredPathForMerge(p.getWebpPath(), sourcePath, targetPath, p.getUserId()));
                p.setSmallThumbPath(rewriteStoredPathForMerge(p.getSmallThumbPath(), sourcePath, targetPath, p.getUserId()));
                p.setMediumThumbPath(rewriteStoredPathForMerge(p.getMediumThumbPath(), sourcePath, targetPath, p.getUserId()));
                p.setLargeThumbPath(rewriteStoredPathForMerge(p.getLargeThumbPath(), sourcePath, targetPath, p.getUserId()));
                p.setBackgroundRemovedPath(rewriteStoredPathForMerge(p.getBackgroundRemovedPath(), sourcePath, targetPath, p.getUserId()));
                // 更新照片的albumId到目标相册
                if (targetAlbumId != null) {
                    p.setAlbumId(targetAlbumId);
                }
            }
            photoRepository.saveAll(sourcePhotos);
            log.info("合并相册: 更新了 {} 张照片路径和albumId", photoCount);

            // 4. 删除源相册记录（照片已移走，保留空相册记录没意义）
            albumRepository.delete(sourceAlbum);
            log.info("合并相册: 删除了源相册记录 {}", albumId);

            // 5. 如果找到目标相册，更新其 photoCount 并重新获取
            if (targetAlbum != null) {
                targetAlbum = albumRepository.findById(targetAlbumId).orElse(null);
                if (targetAlbum != null) {
                    // 更新目标相册的照片数量（加上合并过来的照片数量）
                    // 需要减去因文件名冲突被覆盖的照片数量
                    int mergedPhotoCount = Math.toIntExact(photoRepository.countByAlbumId(targetAlbumId));
                    targetAlbum.setPhotoCount(mergedPhotoCount);
                    albumRepository.save(targetAlbum);
                    log.info("合并相册: 更新目标相册 {} 的照片数量为 {}", targetAlbumId, mergedPhotoCount);
                }
            }

            result.put("success", true);
            result.put("message", String.format("已合并 %d 张照片到 %s", photoCount, targetPath.getFileName()));
            result.put("sourceAlbumId", albumId); // 源相册ID，用于前端移除
            if (targetAlbum != null) {
                result.put("targetAlbumId", targetAlbum.getId()); // 目标相册ID，用于前端刷新
                result.put("targetAlbumPhotoCount", targetAlbum.getPhotoCount());
            }

        } catch (IOException e) {
            log.error("合并相册失败", e);
            result.put("success", false);
            result.put("message", "合并失败: " + sanitizeErrorMessage(e.getMessage()));
        }

        return result;
    }

    /**
     * 获取相册的下一级子目录（文件系统中的子目录，不只是数据库中有记录的）
     */
    public List<Map<String, String>> getChildDirectories(UserAccount currentUser, Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));
        assertAlbumAccessible(currentUser, album);
        Path albumPath = resolveAlbumPath(album);
        ensureWithinScope(albumPath, currentUser);
        List<Map<String, String>> dirs = new ArrayList<>();
        if (!Files.isDirectory(albumPath)) return dirs;
        try (Stream<Path> stream = Files.list(albumPath)) {
            stream.filter(Files::isDirectory)
                  .filter(p -> !p.getFileName().toString().startsWith("."))
                  .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                  .forEach(p -> {
                      Map<String, String> dir = new HashMap<>();
                      dir.put("name", p.getFileName().toString());
                      dir.put("path", toClientPath(p));
                      dirs.add(dir);
                  });
        } catch (IOException e) {
            log.error("列出子目录失败: {}", albumPath, e);
        }
        return dirs;
    }

    /**
     * 列出指定路径的子目录（用于路径选择器）
     */
    public Map<String, Object> listDirectories(UserAccount currentUser, String dirPath) {
        Map<String, Object> result = new HashMap<>();
        Path dir;
        if (dirPath == null || dirPath.isEmpty()) {
            dir = getScopedRoot(currentUser);
        } else {
            dir = resolveRequestedPathWithinScope(dirPath, currentUser);
        }
        result.put("currentPath", toClientPath(dir));
        Path scopedRoot = getScopedRoot(currentUser);
        result.put("parent", dir.getParent() != null && dir.getParent().startsWith(scopedRoot) ? toClientPath(dir.getParent()) : null);

        List<Map<String, String>> dirs = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isDirectory)
                      .filter(p -> !p.getFileName().toString().startsWith("."))
                      .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                      .forEach(p -> {
                          Map<String, String> d = new HashMap<>();
                          d.put("name", p.getFileName().toString());
                          d.put("path", toClientPath(p));
                          dirs.add(d);
                      });
            } catch (IOException e) {
                log.error("列出目录失败: {}", dir, e);
            }
        }
        result.put("directories", dirs);
        return result;
    }

    /**
     * 检查移动是否有冲突（预检）
     */
    public AlbumMoveResult checkMove(UserAccount currentUser, Long albumId, String targetParentPath) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));
        assertAlbumAccessible(currentUser, album);
        return checkConflict(currentUser, album, targetParentPath);
    }

    /**
     * 执行移动相册操作
     */
    @Transactional
    public AlbumMoveResult moveAlbum(UserAccount currentUser, Long albumId, String targetParentPath, String conflictResolution) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));
        assertAlbumAccessible(currentUser, album);

        Path sourcePath = resolveAlbumPath(album);
        Path targetParent = resolveRequestedPathWithinScope(targetParentPath, currentUser);
        Path targetPath = targetParent.resolve(sourcePath.getFileName());

        // 验证
        if (!Files.isDirectory(sourcePath)) {
            return errorResult("源目录不存在: " + toClientPath(sourcePath));
        }
        if (sourcePath.equals(targetPath)) {
            return errorResult("源路径与目标路径相同");
        }
        if (targetPath.startsWith(sourcePath)) {
            return errorResult("不能移动到自身的子目录中");
        }

        // 检查冲突
        if (Files.exists(targetPath)) {
            if (conflictResolution == null || conflictResolution.isEmpty()) {
                return checkConflict(currentUser, album, targetParentPath);
            }

            switch (conflictResolution) {
                case "overwrite":
                    return executeOverwriteMove(currentUser, album, sourcePath, targetPath);
                case "rename":
                    String newName = findUniqueDirectoryName(targetParent, sourcePath.getFileName().toString());
                    targetPath = targetParent.resolve(newName);
                    break;
                default:
                    return errorResult("未知的冲突处理方式: " + conflictResolution);
            }
        }

        return executeMove(currentUser, album, sourcePath, targetPath);
    }

    // ======================== 核心移动逻辑 ========================

    private AlbumMoveResult executeMove(UserAccount currentUser, Album album, Path sourcePath, Path targetPath) {
        String oldPrefix = sourcePath.toString();
        String newPrefix = targetPath.toString();

        try {
            // 1. 创建目标父目录
            Files.createDirectories(targetPath.getParent());

            // 2. 物理移动目录
            Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                moveDirectoryRecursive(sourcePath, targetPath);
            } catch (IOException ex) {
                return errorResult("移动目录失败: " + sanitizeErrorMessage(ex.getMessage()));
            }
        } catch (IOException e) {
            return errorResult("移动目录失败: " + sanitizeErrorMessage(e.getMessage()));
        }

        // 3. 更新数据库中所有相关路径
        updateAllPaths(currentUser, oldPrefix, newPrefix);

        // 4. 返回结果
        AlbumMoveResult result = new AlbumMoveResult();
        result.setSuccess(true);
        result.setMessage("相册移动成功");
        // 重新加载相册
        albumRepository.findById(album.getId()).ifPresent(a -> {
            // 简单返回成功，不需要完整DTO
        });
        return result;
    }

    private AlbumMoveResult executeOverwriteMove(UserAccount currentUser, Album album, Path sourcePath, Path targetPath) {
        String oldPrefix = sourcePath.toString();
        String newPrefix = targetPath.toString();

        // 1. 清理目标位置已有的数据
        cleanupTargetAlbumData(currentUser, targetPath.toString());

        // 2. 删除目标目录的内容（但保留目录本身）
        try {
            deleteDirectoryContents(targetPath);
        } catch (IOException e) {
            return errorResult("清理目标目录失败: " + sanitizeErrorMessage(e.getMessage()));
        }

        // 3. 将源目录内容移动到目标目录
        try {
            moveDirectoryContents(sourcePath, targetPath);
            // 删除空的源目录
            Files.deleteIfExists(sourcePath);
        } catch (IOException e) {
            return errorResult("移动目录内容失败: " + sanitizeErrorMessage(e.getMessage()));
        }

        // 4. 更新数据库中所有相关路径
        updateAllPaths(currentUser, oldPrefix, newPrefix);

        AlbumMoveResult result = new AlbumMoveResult();
        result.setSuccess(true);
        result.setMessage("相册已覆盖移动成功");
        return result;
    }

    // ======================== 路径更新 ========================

    private void updateAllPaths(UserAccount currentUser, String oldPrefix, String newPrefix) {
        // 标准化前缀
        String normalizedOldPrefix = oldPrefix.replace("\\", "/");
        Path oldRoot = Paths.get(oldPrefix).toAbsolutePath().normalize();
        Path newRoot = Paths.get(newPrefix).toAbsolutePath().normalize();
        Long managedUserId = userPathService.extractUserIdFromPath(oldRoot.toString());

        // 更新相册路径和pathHash（使用标准化路径比较）
        List<Album> allAlbums = listAccessibleAlbumsByPrefix(currentUser, normalizedOldPrefix, managedUserId);
        List<Album> albumsToUpdate = new ArrayList<>();
        for (Album a : allAlbums) {
            if (a.getPath() == null || a.getPath().isBlank()) {
                continue;
            }
            String rewrittenPath = rewriteStoredPathForMove(a.getPath(), oldRoot, newRoot, a.getUserId());
            if (!Objects.equals(rewrittenPath, a.getPath())) {
                a.setPath(rewrittenPath);
                a.setPathHash(computeSha256(rewrittenPath));
                albumsToUpdate.add(a);
            }
        }
        if (!albumsToUpdate.isEmpty()) {
            albumRepository.saveAll(albumsToUpdate);
            log.info("更新了 {} 个相册路径", albumsToUpdate.size());
        }

        // 更新照片的所有路径字段和pathHash（使用标准化路径比较）
        // 需要查询所有相册，然后检查哪些照片的路径需要更新
        List<Photo> allPhotos = listAccessiblePhotosByPrefix(currentUser, normalizedOldPrefix, managedUserId);
        List<Photo> photosToUpdate = new ArrayList<>();
        for (Photo p : allPhotos) {
            boolean needsUpdate = false;
            if (p.getOriginalPath() != null) {
                String rewrittenOriginalPath = rewriteStoredPathForMove(p.getOriginalPath(), oldRoot, newRoot, p.getUserId());
                if (!Objects.equals(rewrittenOriginalPath, p.getOriginalPath())) {
                    needsUpdate = true;
                    p.setOriginalPath(rewrittenOriginalPath);
                    p.setPathHash(computeSha256(p.getOriginalPath()));
                }
            }
            String rewrittenThumbnailPath = rewriteStoredPathForMove(p.getThumbnailPath(), oldRoot, newRoot, p.getUserId());
            String rewrittenWebpPath = rewriteStoredPathForMove(p.getWebpPath(), oldRoot, newRoot, p.getUserId());
            String rewrittenSmallThumbPath = rewriteStoredPathForMove(p.getSmallThumbPath(), oldRoot, newRoot, p.getUserId());
            String rewrittenMediumThumbPath = rewriteStoredPathForMove(p.getMediumThumbPath(), oldRoot, newRoot, p.getUserId());
            String rewrittenLargeThumbPath = rewriteStoredPathForMove(p.getLargeThumbPath(), oldRoot, newRoot, p.getUserId());
            String rewrittenBackgroundRemovedPath = rewriteStoredPathForMove(p.getBackgroundRemovedPath(), oldRoot, newRoot, p.getUserId());

            if (needsUpdate ||
                !Objects.equals(rewrittenThumbnailPath, p.getThumbnailPath()) ||
                !Objects.equals(rewrittenWebpPath, p.getWebpPath()) ||
                !Objects.equals(rewrittenSmallThumbPath, p.getSmallThumbPath()) ||
                !Objects.equals(rewrittenMediumThumbPath, p.getMediumThumbPath()) ||
                !Objects.equals(rewrittenLargeThumbPath, p.getLargeThumbPath()) ||
                !Objects.equals(rewrittenBackgroundRemovedPath, p.getBackgroundRemovedPath())) {
                p.setThumbnailPath(rewrittenThumbnailPath);
                p.setWebpPath(rewrittenWebpPath);
                p.setSmallThumbPath(rewrittenSmallThumbPath);
                p.setMediumThumbPath(rewrittenMediumThumbPath);
                p.setLargeThumbPath(rewrittenLargeThumbPath);
                p.setBackgroundRemovedPath(rewrittenBackgroundRemovedPath);
                photosToUpdate.add(p);
            }
        }
        if (!photosToUpdate.isEmpty()) {
            photoRepository.saveAll(photosToUpdate);
            log.info("更新了 {} 张照片路径", photosToUpdate.size());
        }
    }

    // ======================== 目标数据清理（覆盖时） ========================

    /**
     * 清理目标路径下的所有相册和照片数据（包括人脸、标签、AI评分等关联）
     */
    private void cleanupTargetAlbumData(UserAccount currentUser, String targetPathStr) {
        // 标准化路径
        String normalizedTargetPath = targetPathStr.replace("\\", "/");

        // 使用标准化路径匹配
        List<Album> allAlbums = listAccessibleAlbumsByPrefix(currentUser, normalizedTargetPath);
        List<Album> targetAlbums = new ArrayList<>();
        for (Album a : allAlbums) {
            if (a.getPath() != null) {
                String normalizedDbPath = a.getPath().replace("\\", "/");
                if (normalizedDbPath.startsWith(normalizedTargetPath)) {
                    targetAlbums.add(a);
                }
            }
        }

        if (targetAlbums.isEmpty()) return;

        List<Long> albumIds = targetAlbums.stream().map(Album::getId).collect(Collectors.toList());
        log.info("覆盖移动 - 需要清理 {} 个目标相册", albumIds.size());

        for (Long albumId : albumIds) {
            List<Photo> photos = loadAllPhotosByAlbumId(albumId);

            for (Photo photo : photos) {
                // 删除人脸记录
                List<Face> faces = faceRepository.findByPhotoId(photo.getId());
                if (!faces.isEmpty()) {
                    faceRepository.deleteAll(faces);
                }
                // 删除照片指派记录
                photoAssignmentRepository.deleteByPhotoId(photo.getId());
                // 删除AI评分记录
                photoAIScoringRepository.findByPhotoId(photo.getId())
                        .ifPresent(photoAIScoringRepository::delete);
            }

            // 批量删除照片记录
            if (!photos.isEmpty()) {
                photoRepository.deleteAll(photos);
                log.info("覆盖移动 - 删除了相册 {} 的 {} 张照片及其关联", albumId, photos.size());
            }
        }

        // 删除目标相册记录
        albumRepository.deleteAll(targetAlbums);
        log.info("覆盖移动 - 删除了 {} 个目标相册记录", targetAlbums.size());
    }

    // ======================== 冲突检查 ========================

    private AlbumMoveResult checkConflict(UserAccount currentUser, Album album, String targetParentPath) {
        Path sourcePath = resolveAlbumPath(album);
        Path targetParent = resolveRequestedPathWithinScope(targetParentPath, currentUser);
        Path targetPath = targetParent.resolve(sourcePath.getFileName());

        AlbumMoveResult result = new AlbumMoveResult();

        if (!Files.exists(targetPath)) {
            result.setSuccess(true);
            result.setConflict(false);
            result.setMessage("可以移动，无冲突");
            return result;
        }

        // 有同名目录冲突
        result.setSuccess(false);
        result.setConflict(true);
        result.setConflictType("same_name_folder");
        result.setConflictPath(toClientPath(targetPath));

        // 列出冲突目录下的文件
        List<String> files = new ArrayList<>();
        int photoCount = 0;
        try (Stream<Path> stream = Files.walk(targetPath)) {
            List<Path> allFiles = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            for (Path f : allFiles) {
                if (!f.getFileName().toString().startsWith(".")) {
                    files.add(targetPath.relativize(f).toString());
                    String lower = f.getFileName().toString().toLowerCase();
                    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                        lower.endsWith(".heic") || lower.endsWith(".raw")) {
                        photoCount++;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("遍历冲突目录失败: {}", targetPath, e);
        }

        result.setConflictFiles(files);
        result.setConflictPhotoCount(photoCount);
        result.setMessage(String.format("目标位置已存在同名文件夹 \"%s\"，包含 %d 个文件（%d 张图片）",
                sourcePath.getFileName(), files.size(), photoCount));
        result.setSuggestedNewName(findUniqueDirectoryName(targetParent, sourcePath.getFileName().toString()));

        return result;
    }

    // ======================== 工具方法 ========================

    private String findUniqueDirectoryName(Path parent, String baseName) {
        String name = baseName;
        int counter = 2;
        while (Files.exists(parent.resolve(name))) {
            name = baseName + " (" + counter + ")";
            counter++;
        }
        return name;
    }

    private String replacePrefix(String path, String oldPrefix, String newPrefix) {
        if (path == null) return null;

        // 标准化路径分隔符（统一用 / 比较）
        String normalizedPath = path.replace("\\", "/");
        String normalizedOld = oldPrefix.replace("\\", "/");
        String normalizedNew = newPrefix.replace("\\", "/");

        if (normalizedPath.startsWith(normalizedOld)) {
            // 保持原始分隔符风格
            String suffix = path.substring(oldPrefix.length());
            return newPrefix + suffix;
        }
        return path;
    }

    private String rewriteStoredPathForMove(String currentPath, Path oldRoot, Path newRoot, Long userId) {
        if (currentPath == null || currentPath.isBlank() || oldRoot == null || newRoot == null) {
            return currentPath;
        }
        try {
            Path currentAbsolute = resolveStoredPath(currentPath);
            if (!currentAbsolute.startsWith(oldRoot)) {
                return currentPath;
            }
            Path relative = oldRoot.relativize(currentAbsolute);
            Path targetAbsolute = newRoot.resolve(relative).normalize();
            return toStoredPath(targetAbsolute, userId);
        } catch (IOException e) {
            return currentPath;
        }
    }

    private String rewriteStoredPathForMerge(String currentPath, Path sourceAlbumPath, Path targetAlbumPath, Long userId) {
        return rewriteStoredPathForMove(currentPath, sourceAlbumPath, targetAlbumPath, userId);
    }

    private String toStoredPath(Path path, Long userId) {
        if (path == null) {
            return null;
        }
        String absolute = path.toAbsolutePath().normalize().toString();
        return userPathService.tryBuildStoragePathReference(absolute, userId).orElse(absolute);
    }

    private Path resolveStoredPath(String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IOException("路径为空");
        }
        try {
            return userPathService.resolveStoredPhotoPath(path);
        } catch (Exception e) {
            throw new IOException("解析存储路径失败: " + path, e);
        }
    }

    private String computeSha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void moveDirectoryRecursive(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.move(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void moveDirectoryContents(Path source, Path target) throws IOException {
        try (Stream<Path> entries = Files.list(source)) {
            for (Path entry : entries.collect(Collectors.toList())) {
                Path dest = target.resolve(entry.getFileName());
                if (Files.isDirectory(entry)) {
                    moveDirectoryRecursive(entry, dest);
                } else {
                    Files.move(entry, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void deleteDirectoryContents(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (!d.equals(dir)) {
                    Files.deleteIfExists(d);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private AlbumMoveResult errorResult(String message) {
        AlbumMoveResult result = new AlbumMoveResult();
        result.setSuccess(false);
        result.setMessage(message);
        return result;
    }

    private Path resolveBasePath() {
        return userPathService.resolvePhotoBasePath();
    }

    private Path resolveAlbumPath(Album album) {
        if (album.getPath() == null || album.getPath().isBlank()) {
            throw new RuntimeException("相册路径不存在");
        }
        try {
            return userPathService.resolveStoredPhotoPath(album.getPath());
        } catch (Exception e) {
            return Paths.get(album.getPath()).toAbsolutePath().normalize();
        }
    }

    private Path getScopedRoot(UserAccount currentUser) {
        if (currentUser != null && currentUser.getRole() == UserRole.SUPER_ADMIN) {
            return resolveBasePath();
        }
        return userPathService.getScopedPhotoRoot(currentUser).toAbsolutePath().normalize();
    }

    private String toClientPath(Path path) {
        if (path == null) {
            return null;
        }
        return userPathService.toDisplayPath(path.toAbsolutePath().normalize().toString(), true);
    }

    private Path resolveRequestedPathWithinScope(String requestedPath, UserAccount currentUser) {
        Path candidate = userPathService.resolveScopedPath(requestedPath, currentUser);
        // 保持对旧版/测试替身 UserPathService 的兼容；生产实现始终走统一解析器。
        if (candidate == null) {
            Path scopedRoot = getScopedRoot(currentUser);
            if (requestedPath == null || requestedPath.isBlank()) {
                return scopedRoot;
            }
            Path raw = Paths.get(requestedPath.trim());
            if (!raw.isAbsolute()) {
                Path relative = raw.normalize();
                if (currentUser != null) {
                    relative = userPathService.stripLeadingUserSegment(relative, currentUser.getId());
                }
                candidate = scopedRoot.resolve(relative);
            } else {
                candidate = raw;
            }
            candidate = candidate.toAbsolutePath().normalize();
        }
        ensureWithinScope(candidate, currentUser);
        return candidate;
    }

    private void ensureWithinScope(Path candidate, UserAccount currentUser) {
        Path scopedRoot = getScopedRoot(currentUser);
        if (!candidate.startsWith(scopedRoot)) {
            throw new RuntimeException("路径超出当前用户可操作范围");
        }
    }

    private void assertAlbumAccessible(UserAccount currentUser, Album album) {
        if (!isAlbumAccessible(currentUser, album)) {
            throw new RuntimeException("无权操作该相册");
        }
        ensureWithinScope(resolveAlbumPath(album), currentUser);
    }

    private boolean isAlbumAccessible(UserAccount currentUser, Album album) {
        if (album == null) {
            return false;
        }
        if (currentUser == null || currentUser.getRole() == UserRole.SUPER_ADMIN) {
            return true;
        }
        if (Objects.equals(album.getUserId(), currentUser.getId())) {
            return true;
        }
        try {
            return resolveAlbumPath(album).startsWith(getScopedRoot(currentUser));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isPhotoAccessible(UserAccount currentUser, Photo photo) {
        if (photo == null) {
            return false;
        }
        if (currentUser == null || currentUser.getRole() == UserRole.SUPER_ADMIN) {
            return true;
        }
        if (Objects.equals(photo.getUserId(), currentUser.getId())) {
            return true;
        }
        try {
            Path absolute = resolveStoredPath(photo.getOriginalPath());
            return absolute.startsWith(getScopedRoot(currentUser));
        } catch (Exception e) {
            return false;
        }
    }

    private List<Album> listAccessibleAlbums(UserAccount currentUser) {
        Long userId = currentUser == null || currentUser.getRole() == UserRole.SUPER_ADMIN
            ? null
            : currentUser.getId();
        return loadAlbumsByUserId(userId);
    }

    private List<Photo> listAccessiblePhotos(UserAccount currentUser) {
        Long userId = currentUser == null || currentUser.getRole() == UserRole.SUPER_ADMIN
            ? null
            : currentUser.getId();
        return loadPhotosByUserId(userId);
    }

    private List<Photo> loadAllPhotosByAlbumId(Long albumId) {
        List<Photo> photos = new ArrayList<>();
        int pageNumber = 0;
        org.springframework.data.domain.Page<Photo> page;
        do {
            page = photoRepository.findByAlbumId(albumId, PageRequest.of(pageNumber, 200));
            photos.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return photos;
    }

    private List<Album> loadAlbumsByUserId(Long userId) {
        List<Album> albums = new ArrayList<>();
        int pageNumber = 0;
        org.springframework.data.domain.Page<Album> page;
        do {
            org.springframework.data.domain.Pageable pageable = PageRequest.of(pageNumber, 200);
            page = userId == null ? albumRepository.findAll(pageable) : albumRepository.findByUserId(userId, pageable);
            albums.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return albums;
    }

    private List<Photo> loadPhotosByUserId(Long userId) {
        List<Photo> photos = new ArrayList<>();
        int pageNumber = 0;
        org.springframework.data.domain.Page<Photo> page;
        do {
            org.springframework.data.domain.Pageable pageable = PageRequest.of(pageNumber, 200);
            page = userId == null ? photoRepository.findAll(pageable) : photoRepository.findByUserId(userId, pageable);
            photos.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return photos;
    }

    private List<Album> listAccessibleAlbumsByPrefix(UserAccount currentUser, String normalizedPathPrefix) {
        Long userId = currentUser == null || currentUser.getRole() == UserRole.SUPER_ADMIN ? null : currentUser.getId();
        return listAccessibleAlbumsByPrefix(currentUser, normalizedPathPrefix, userId);
    }

    private List<Album> listAccessibleAlbumsByPrefix(UserAccount currentUser,
                                                     String normalizedPathPrefix,
                                                     Long managedUserId) {
        if (normalizedPathPrefix == null || normalizedPathPrefix.isBlank()) {
            return listAccessibleAlbums(currentUser);
        }
        LinkedHashSet<Album> matched = new LinkedHashSet<>();
        for (String candidatePrefix : buildManagedPathCandidates(normalizedPathPrefix, managedUserId)) {
            if (currentUser == null || currentUser.getRole() == UserRole.SUPER_ADMIN) {
                matched.addAll(albumRepository.findByPathStartingWithNormalized(candidatePrefix));
            } else {
                matched.addAll(albumRepository.findByUserIdAndPathStartingWithNormalized(currentUser.getId(), candidatePrefix));
            }
        }
        return new ArrayList<>(matched);
    }

    private List<Photo> listAccessiblePhotosByPrefix(UserAccount currentUser, String normalizedPathPrefix) {
        Long userId = currentUser == null || currentUser.getRole() == UserRole.SUPER_ADMIN ? null : currentUser.getId();
        return listAccessiblePhotosByPrefix(currentUser, normalizedPathPrefix, userId);
    }

    private List<Photo> listAccessiblePhotosByPrefix(UserAccount currentUser,
                                                     String normalizedPathPrefix,
                                                     Long managedUserId) {
        if (normalizedPathPrefix == null || normalizedPathPrefix.isBlank()) {
            return listAccessiblePhotos(currentUser);
        }
        LinkedHashSet<Photo> matched = new LinkedHashSet<>();
        for (String candidatePrefix : buildManagedPathCandidates(normalizedPathPrefix, managedUserId)) {
            if (currentUser == null || currentUser.getRole() == UserRole.SUPER_ADMIN) {
                matched.addAll(photoRepository.findByOriginalPathStartingWith(candidatePrefix));
            } else {
                matched.addAll(photoRepository.findByUserIdAndOriginalPathStartingWith(currentUser.getId(), candidatePrefix));
            }
        }
        return new ArrayList<>(matched);
    }

    private Optional<Album> findAccessibleAlbumByNormalizedPath(UserAccount currentUser, String normalizedPath) {
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return Optional.empty();
        }
        Long managedUserId = userPathService.extractUserIdFromPath(normalizedPath);
        LinkedHashSet<String> exactCandidates = buildManagedPathCandidates(normalizedPath, managedUserId);
        return listAccessibleAlbumsByPrefix(currentUser, normalizedPath, managedUserId).stream()
            .filter(album -> album.getPath() != null && exactCandidates.contains(album.getPath().replace("\\", "/")))
            .findFirst();
    }

    private LinkedHashSet<String> buildManagedPathCandidates(String normalizedPath, Long managedUserId) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return candidates;
        }
        String clean = normalizedPath.replace("\\", "/");
        candidates.add(clean);
        userPathService.tryBuildStoragePathReference(clean, managedUserId).ifPresent(ref ->
            candidates.add(ref.replace("\\", "/"))
        );
        return candidates;
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "系统异常";
        }
        return userPathService.sanitizeVisibleText(message);
    }
}
