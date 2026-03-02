package com.photoexhibition.service;

import com.photoexhibition.dto.AlbumMoveResult;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PhotoAIScoringRepository;
import com.photoexhibition.repository.PhotoAssignmentRepository;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${photo.scan.base-path}")
    private String basePath;

    /**
     * 获取分类列表（base-path下的一级目录）
     */
    public List<Map<String, String>> getCategories() {
        Path base = resolveBasePath();
        List<Map<String, String>> categories = new ArrayList<>();
        try (Stream<Path> stream = Files.list(base)) {
            stream.filter(Files::isDirectory)
                  .filter(p -> !p.getFileName().toString().startsWith("."))
                  .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                  .forEach(p -> {
                      Map<String, String> cat = new HashMap<>();
                      cat.put("name", p.getFileName().toString());
                      cat.put("path", p.toString());
                      categories.add(cat);
                  });
        } catch (IOException e) {
            log.error("列出分类目录失败", e);
        }
        return categories;
    }

    /**
     * 获取相册的下一级子目录（文件系统中的子目录，不只是数据库中有记录的）
     */
    public List<Map<String, String>> getChildDirectories(Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));
        Path albumPath = Paths.get(album.getPath()).toAbsolutePath().normalize();
        List<Map<String, String>> dirs = new ArrayList<>();
        if (!Files.isDirectory(albumPath)) return dirs;
        try (Stream<Path> stream = Files.list(albumPath)) {
            stream.filter(Files::isDirectory)
                  .filter(p -> !p.getFileName().toString().startsWith("."))
                  .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                  .forEach(p -> {
                      Map<String, String> dir = new HashMap<>();
                      dir.put("name", p.getFileName().toString());
                      dir.put("path", p.toString());
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
    public Map<String, Object> listDirectories(String dirPath) {
        Map<String, Object> result = new HashMap<>();
        Path dir;
        if (dirPath == null || dirPath.isEmpty()) {
            dir = resolveBasePath();
        } else {
            dir = Paths.get(dirPath).toAbsolutePath().normalize();
        }
        result.put("currentPath", dir.toString());
        result.put("parent", dir.getParent() != null ? dir.getParent().toString() : null);

        List<Map<String, String>> dirs = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> stream = Files.list(dir)) {
                stream.filter(Files::isDirectory)
                      .filter(p -> !p.getFileName().toString().startsWith("."))
                      .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                      .forEach(p -> {
                          Map<String, String> d = new HashMap<>();
                          d.put("name", p.getFileName().toString());
                          d.put("path", p.toString());
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
    public AlbumMoveResult checkMove(Long albumId, String targetParentPath) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));
        return checkConflict(album, targetParentPath);
    }

    /**
     * 执行移动相册操作
     */
    @Transactional
    public AlbumMoveResult moveAlbum(Long albumId, String targetParentPath, String conflictResolution) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));

        Path sourcePath = Paths.get(album.getPath()).toAbsolutePath().normalize();
        Path targetParent = Paths.get(targetParentPath).toAbsolutePath().normalize();
        Path targetPath = targetParent.resolve(sourcePath.getFileName());

        // 验证
        if (!Files.isDirectory(sourcePath)) {
            return errorResult("源目录不存在: " + sourcePath);
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
                return checkConflict(album, targetParentPath);
            }

            switch (conflictResolution) {
                case "overwrite":
                    return executeOverwriteMove(album, sourcePath, targetPath);
                case "rename":
                    String newName = findUniqueDirectoryName(targetParent, sourcePath.getFileName().toString());
                    targetPath = targetParent.resolve(newName);
                    break;
                default:
                    return errorResult("未知的冲突处理方式: " + conflictResolution);
            }
        }

        return executeMove(album, sourcePath, targetPath);
    }

    // ======================== 核心移动逻辑 ========================

    private AlbumMoveResult executeMove(Album album, Path sourcePath, Path targetPath) {
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
                return errorResult("移动目录失败: " + ex.getMessage());
            }
        } catch (IOException e) {
            return errorResult("移动目录失败: " + e.getMessage());
        }

        // 3. 更新数据库中所有相关路径
        updateAllPaths(oldPrefix, newPrefix);

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

    private AlbumMoveResult executeOverwriteMove(Album album, Path sourcePath, Path targetPath) {
        String oldPrefix = sourcePath.toString();
        String newPrefix = targetPath.toString();

        // 1. 清理目标位置已有的数据
        cleanupTargetAlbumData(targetPath.toString());

        // 2. 删除目标目录的内容（但保留目录本身）
        try {
            deleteDirectoryContents(targetPath);
        } catch (IOException e) {
            return errorResult("清理目标目录失败: " + e.getMessage());
        }

        // 3. 将源目录内容移动到目标目录
        try {
            moveDirectoryContents(sourcePath, targetPath);
            // 删除空的源目录
            Files.deleteIfExists(sourcePath);
        } catch (IOException e) {
            return errorResult("移动目录内容失败: " + e.getMessage());
        }

        // 4. 更新数据库中所有相关路径
        updateAllPaths(oldPrefix, newPrefix);

        AlbumMoveResult result = new AlbumMoveResult();
        result.setSuccess(true);
        result.setMessage("相册已覆盖移动成功");
        return result;
    }

    // ======================== 路径更新 ========================

    private void updateAllPaths(String oldPrefix, String newPrefix) {
        // 更新相册路径和pathHash
        List<Album> albums = albumRepository.findByPathStartingWith(oldPrefix);
        for (Album a : albums) {
            String newPath = replacePrefix(a.getPath(), oldPrefix, newPrefix);
            a.setPath(newPath);
            a.setPathHash(computeSha256(newPath));
        }
        if (!albums.isEmpty()) {
            albumRepository.saveAll(albums);
            log.info("更新了 {} 个相册路径", albums.size());
        }

        // 更新照片的所有路径字段和pathHash
        List<Photo> photos = photoRepository.findByOriginalPathStartingWith(oldPrefix);
        for (Photo p : photos) {
            p.setOriginalPath(replacePrefix(p.getOriginalPath(), oldPrefix, newPrefix));
            p.setPathHash(computeSha256(p.getOriginalPath()));
            p.setThumbnailPath(replacePrefix(p.getThumbnailPath(), oldPrefix, newPrefix));
            p.setWebpPath(replacePrefix(p.getWebpPath(), oldPrefix, newPrefix));
            p.setSmallThumbPath(replacePrefix(p.getSmallThumbPath(), oldPrefix, newPrefix));
            p.setMediumThumbPath(replacePrefix(p.getMediumThumbPath(), oldPrefix, newPrefix));
            p.setLargeThumbPath(replacePrefix(p.getLargeThumbPath(), oldPrefix, newPrefix));
            p.setBackgroundRemovedPath(replacePrefix(p.getBackgroundRemovedPath(), oldPrefix, newPrefix));
        }
        if (!photos.isEmpty()) {
            photoRepository.saveAll(photos);
            log.info("更新了 {} 张照片路径", photos.size());
        }
    }

    // ======================== 目标数据清理（覆盖时） ========================

    /**
     * 清理目标路径下的所有相册和照片数据（包括人脸、标签、AI评分等关联）
     */
    private void cleanupTargetAlbumData(String targetPathStr) {
        List<Album> targetAlbums = albumRepository.findByPathStartingWith(targetPathStr);
        if (targetAlbums.isEmpty()) return;

        List<Long> albumIds = targetAlbums.stream().map(Album::getId).collect(Collectors.toList());
        log.info("覆盖移动 - 需要清理 {} 个目标相册", albumIds.size());

        for (Long albumId : albumIds) {
            List<Photo> photos = photoRepository.findByAlbumId(albumId,
                    PageRequest.of(0, Integer.MAX_VALUE)).getContent();

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

    private AlbumMoveResult checkConflict(Album album, String targetParentPath) {
        Path sourcePath = Paths.get(album.getPath()).toAbsolutePath().normalize();
        Path targetParent = Paths.get(targetParentPath).toAbsolutePath().normalize();
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
        result.setConflictPath(targetPath.toString());

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
        if (path.startsWith(oldPrefix)) {
            return newPrefix + path.substring(oldPrefix.length());
        }
        return path;
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
        Path base = Paths.get(basePath);
        if (!base.isAbsolute()) {
            String projectRoot = System.getProperty("user.dir");
            if (projectRoot.endsWith("backend")) {
                projectRoot = Paths.get(projectRoot).getParent().toString();
            }
            String clean = basePath.startsWith("./") ? basePath.substring(2) : basePath;
            base = Paths.get(projectRoot, clean).toAbsolutePath().normalize();
        }
        return base.toAbsolutePath().normalize();
    }
}
