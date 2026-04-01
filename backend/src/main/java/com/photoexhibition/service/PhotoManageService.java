package com.photoexhibition.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoManageService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final FaceRepository faceRepository;
    private final PhotoAIScoringRepository photoAIScoringRepository;
    private final PhotoAssignmentRepository photoAssignmentRepository;
    private final UserPathService userPathService;

    @Value("${photo.scan.base-path}")
    private String basePath;

    /**
     * Get available move targets for photos in an album.
     * Returns parent directory and sibling directories (other albums at the same level).
     */
    public Map<String, Object> getMoveTargets(UserAccount currentUser, Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));
        ensureAlbumAccess(currentUser, album);

        Path albumPath = resolveLocalDirectoryPath(album.getPath());
        Path parentDir = albumPath.getParent();
        Map<String, Object> result = new HashMap<>();

        // Parent directory info
        if (parentDir != null) {
            Path grandParent = parentDir.getParent();
            if (grandParent != null) {
                result.put("parentDir", Map.of(
                        "name", parentDir.getFileName().toString(),
                        "path", toClientPath(parentDir)
                ));
            }
        }

        // Sibling directories (other dirs at the same level as this album)
        List<Map<String, String>> siblings = new ArrayList<>();
        if (parentDir != null && Files.isDirectory(parentDir)) {
            try (Stream<Path> stream = Files.list(parentDir)) {
                stream.filter(Files::isDirectory)
                      .filter(p -> !p.equals(albumPath))
                      .filter(p -> !p.getFileName().toString().startsWith("."))
                      .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                      .forEach(p -> {
                          Map<String, String> dir = new HashMap<>();
                          dir.put("name", p.getFileName().toString());
                          dir.put("path", toClientPath(p));
                          siblings.add(dir);
                      });
            } catch (IOException e) {
                log.error("列出同级目录失败: {}", parentDir, e);
            }
        }
        result.put("siblingDirs", siblings);

        // Child directories (subdirs inside this album)
        List<Map<String, String>> children = new ArrayList<>();
        if (Files.isDirectory(albumPath)) {
            try (Stream<Path> stream = Files.list(albumPath)) {
                stream.filter(Files::isDirectory)
                      .filter(p -> !p.getFileName().toString().startsWith("."))
                      .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                      .forEach(p -> {
                          Map<String, String> dir = new HashMap<>();
                          dir.put("name", p.getFileName().toString());
                          dir.put("path", toClientPath(p));
                          children.add(dir);
                      });
            } catch (IOException e) {
                log.error("列出子目录失败: {}", albumPath, e);
            }
        }
        result.put("childDirs", children);

        return result;
    }

    /**
     * Move photos to a target directory.
     * @param conflictResolution null = detect conflicts first; "overwrite" / "rename" / "skip"
     */
    @Transactional
    public Map<String, Object> movePhotos(UserAccount currentUser, List<Long> photoIds, String targetDirPath, String conflictResolution) {
        Map<String, Object> result = new HashMap<>();

        if (photoIds == null || photoIds.isEmpty()) {
            result.put("success", false);
            result.put("message", "未选择照片");
            return result;
        }

        Path targetDir = resolveLocalDirectoryPath(targetDirPath);

        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "创建目标目录失败: " + toClientPath(e.getMessage()));
            return result;
        }

        List<Photo> photos = photoRepository.findAllById(photoIds);
        if (photos.isEmpty()) {
            result.put("success", false);
            result.put("message", "未找到指定照片");
            return result;
        }
        ensurePhotoAccess(currentUser, photos);

        // Detect filename conflicts
        List<String> conflictFiles = new ArrayList<>();
        for (Photo photo : photos) {
            try {
                Path sourceFile = resolveStoredPath(photo.getOriginalPath());
                Path targetFile = targetDir.resolve(sourceFile.getFileName());
                if (Files.exists(targetFile) && !targetFile.equals(sourceFile)) {
                    conflictFiles.add(photo.getFilename());
                }
            } catch (IOException e) {
                log.warn("检测移动冲突时解析照片路径失败: photoId={}, path={}", photo.getId(), toClientPath(photo.getOriginalPath()), e);
            }
        }

        // If conflicts exist and no resolution specified, return conflict info
        if (!conflictFiles.isEmpty() && (conflictResolution == null || conflictResolution.isEmpty())) {
            result.put("success", false);
            result.put("conflict", true);
            result.put("conflictFiles", conflictFiles);
            String fileList = conflictFiles.size() > 3
                    ? conflictFiles.subList(0, 3).stream().collect(Collectors.joining("、")) + " 等"
                    : String.join("、", conflictFiles);
            result.put("message", String.format("目标目录已存在 %d 个同名文件：%s",
                    conflictFiles.size(), fileList));
            return result;
        }

        // Track source albums for later cleanup
        Set<Long> sourceAlbumIds = photos.stream()
                .map(Photo::getAlbumId)
                .collect(Collectors.toSet());

        Long ownerUserId = resolveOwnerUserId(currentUser, photos);
        Album targetAlbum = findOrCreateAlbumForPath(targetDir.toString(), ownerUserId);

        int movedCount = 0;
        List<String> errors = new ArrayList<>();

        for (Photo photo : photos) {
            try {
                movePhotoToDir(photo, targetDir, targetAlbum, conflictResolution);
                movedCount++;
            } catch (Exception e) {
                log.error("移动照片失败: {} -> {}", toClientPath(photo.getOriginalPath()), toClientPath(targetDir), e);
                errors.add(photo.getFilename() + ": " + e.getMessage());
            }
        }

        photoRepository.saveAll(photos);

        // Update target album photo count
        long targetCount = photoRepository.countByAlbumId(targetAlbum.getId());
        targetAlbum.setPhotoCount((int) targetCount);
        albumRepository.save(targetAlbum);

        // Update source album photo counts
        for (Long sourceAlbumId : sourceAlbumIds) {
            if (sourceAlbumId.equals(targetAlbum.getId())) continue;
            albumRepository.findById(sourceAlbumId).ifPresent(sourceAlbum -> {
                long count = photoRepository.countByAlbumId(sourceAlbum.getId());
                sourceAlbum.setPhotoCount((int) count);
                albumRepository.save(sourceAlbum);
            });
        }

        result.put("success", true);
        result.put("movedCount", movedCount);
        result.put("targetAlbumId", targetAlbum.getId());
        result.put("message", String.format("成功移动 %d 张照片", movedCount));
        if (!errors.isEmpty()) {
            result.put("errors", errors);
            result.put("message", String.format("移动了 %d 张照片，%d 张失败", movedCount, errors.size()));
        }
        return result;
    }

    /**
     * Batch delete photos (files + DB records).
     */
    @Transactional
    public Map<String, Object> deletePhotos(UserAccount currentUser, List<Long> photoIds) {
        Map<String, Object> result = new HashMap<>();

        if (photoIds == null || photoIds.isEmpty()) {
            result.put("success", false);
            result.put("message", "未选择照片");
            return result;
        }

        List<Photo> photos = photoRepository.findAllById(photoIds);
        if (photos.isEmpty()) {
            result.put("success", false);
            result.put("message", "未找到指定照片");
            return result;
        }
        ensurePhotoAccess(currentUser, photos);

        Set<Long> affectedAlbumIds = photos.stream()
                .map(Photo::getAlbumId)
                .collect(Collectors.toSet());

        int deletedCount = 0;
        List<String> errors = new ArrayList<>();

        for (Photo photo : photos) {
            try {
                deletePhotoFiles(photo);
                deletePhotoDbRecords(photo);
                deletedCount++;
            } catch (Exception e) {
                log.error("删除照片失败: {}", toClientPath(photo.getOriginalPath()), e);
                errors.add(photo.getFilename() + ": " + e.getMessage());
            }
        }

        // Update album photo counts
        for (Long albumId : affectedAlbumIds) {
            albumRepository.findById(albumId).ifPresent(album -> {
                long count = photoRepository.countByAlbumId(album.getId());
                album.setPhotoCount((int) count);
                albumRepository.save(album);
            });
        }

        result.put("success", true);
        result.put("deletedCount", deletedCount);
        result.put("message", String.format("成功删除 %d 张照片", deletedCount));
        if (!errors.isEmpty()) {
            result.put("errors", errors);
        }
        return result;
    }

    /**
     * 批量删除照片（返回删除数量，用于批量操作接口）
     */
    @Transactional
    public int deletePhotosReturningCount(List<Long> photoIds) {
        if (photoIds == null || photoIds.isEmpty()) {
            return 0;
        }

        List<Photo> photos = photoRepository.findAllById(photoIds);
        if (photos.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Photo photo : photos) {
            try {
                deletePhotoFiles(photo);
                deletePhotoDbRecords(photo);
                count++;
            } catch (Exception e) {
                log.error("删除照片失败: {}", toClientPath(photo.getOriginalPath()), e);
            }
        }

        // Update album photo counts
        Set<Long> affectedAlbumIds = photos.stream()
                .map(Photo::getAlbumId)
                .collect(Collectors.toSet());
        for (Long albumId : affectedAlbumIds) {
            albumRepository.findById(albumId).ifPresent(album -> {
                long c = photoRepository.countByAlbumId(album.getId());
                album.setPhotoCount((int) c);
                albumRepository.save(album);
            });
        }

        return count;
    }

    // ======================== Internal methods ========================

    private void movePhotoToDir(Photo photo, Path targetDir, Album targetAlbum, String conflictResolution) throws IOException {
        Path sourceFile = resolveStoredPath(photo.getOriginalPath());
        if (!Files.exists(sourceFile)) {
            throw new IOException("源文件不存在: " + toClientPath(sourceFile));
        }

        String filename = sourceFile.getFileName().toString();
        Path targetFile = targetDir.resolve(filename);

        if (Files.exists(targetFile) && !targetFile.equals(sourceFile)) {
            if ("overwrite".equals(conflictResolution)) {
                cleanupOverwrittenPhoto(toStoredPath(targetFile, photo.getUserId()));
                Files.deleteIfExists(targetFile);
            } else if ("rename".equals(conflictResolution)) {
                filename = findUniqueFilename(targetDir, filename);
                targetFile = targetDir.resolve(filename);
            } else {
                throw new IOException("同名文件冲突: " + filename);
            }
        }

        try {
            Files.move(sourceFile, targetFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }

        String oldThumbnailPath = photo.getThumbnailPath();
        String oldWebpPath = photo.getWebpPath();
        String oldSmallThumbPath = photo.getSmallThumbPath();
        String oldMediumThumbPath = photo.getMediumThumbPath();
        String oldLargeThumbPath = photo.getLargeThumbPath();
        String oldBackgroundRemovedPath = photo.getBackgroundRemovedPath();

        Path oldParent = sourceFile.getParent();
        Path newParent = targetFile.getParent();

        String storedOriginalPath = toStoredPath(targetFile, photo.getUserId());
        photo.setOriginalPath(storedOriginalPath);
        photo.setPathHash(computeSha256(storedOriginalPath));
        photo.setFilename(filename);
        photo.setAlbumId(targetAlbum.getId());

        if (oldParent != null && newParent != null) {
            String newThumbnailPath = rewriteStoredPathForMove(oldThumbnailPath, oldParent, newParent, photo.getUserId());
            String newWebpPath = rewriteStoredPathForMove(oldWebpPath, oldParent, newParent, photo.getUserId());
            String newSmallThumbPath = rewriteStoredPathForMove(oldSmallThumbPath, oldParent, newParent, photo.getUserId());
            String newMediumThumbPath = rewriteStoredPathForMove(oldMediumThumbPath, oldParent, newParent, photo.getUserId());
            String newLargeThumbPath = rewriteStoredPathForMove(oldLargeThumbPath, oldParent, newParent, photo.getUserId());
            String newBackgroundRemovedPath = rewriteStoredPathForMove(oldBackgroundRemovedPath, oldParent, newParent, photo.getUserId());

            photo.setThumbnailPath(newThumbnailPath);
            photo.setWebpPath(newWebpPath);
            photo.setSmallThumbPath(newSmallThumbPath);
            photo.setMediumThumbPath(newMediumThumbPath);
            photo.setLargeThumbPath(newLargeThumbPath);
            photo.setBackgroundRemovedPath(newBackgroundRemovedPath);

            moveStoredFile(oldThumbnailPath, newThumbnailPath);
            moveStoredFile(oldWebpPath, newWebpPath);
            moveStoredFile(oldSmallThumbPath, newSmallThumbPath);
            moveStoredFile(oldMediumThumbPath, newMediumThumbPath);
            moveStoredFile(oldLargeThumbPath, newLargeThumbPath);
            moveStoredFile(oldBackgroundRemovedPath, newBackgroundRemovedPath);
        }
    }

    /**
     * Clean up an existing photo record that is about to be overwritten.
     * Deletes its DB records (faces, assignments, AI scoring) and thumbnail files.
     */
    private void cleanupOverwrittenPhoto(String originalPath) {
        Optional<Photo> existing = findPhotoByOriginalPath(originalPath);
        if (existing.isEmpty()) return;

        Photo victim = existing.get();
        log.info("覆盖移动 - 清理被覆盖照片: id={}, path={}", victim.getId(), originalPath);

        // Delete thumbnail files of the overwritten photo
        deleteFileIfExists(victim.getThumbnailPath());
        deleteFileIfExists(victim.getWebpPath());
        deleteFileIfExists(victim.getSmallThumbPath());
        deleteFileIfExists(victim.getMediumThumbPath());
        deleteFileIfExists(victim.getLargeThumbPath());
        deleteFileIfExists(victim.getBackgroundRemovedPath());

        // Delete faces
        List<Face> faces = faceRepository.findByPhotoId(victim.getId());
        if (!faces.isEmpty()) {
            log.info("覆盖移动 - 删除被覆盖照片的 {} 个人脸记录", faces.size());
            faceRepository.deleteAll(faces);
        }
        // Delete assignments
        photoAssignmentRepository.deleteByPhotoId(victim.getId());
        // Delete AI scoring
        photoAIScoringRepository.findByPhotoId(victim.getId())
                .ifPresent(photoAIScoringRepository::delete);
        // Delete the photo record
        photoRepository.delete(victim);
        photoRepository.flush();
    }

    private Optional<Photo> findPhotoByOriginalPath(String originalPath) {
        if (originalPath == null || originalPath.isBlank()) {
            return Optional.empty();
        }
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(originalPath);
        Long userId = userPathService.extractUserIdFromPath(originalPath);
        userPathService.tryBuildStoragePathReference(originalPath, userId).ifPresent(candidates::add);

        for (String candidate : candidates) {
            try {
                Optional<Photo> matched = photoRepository.findByOriginalPath(candidate);
                if (matched.isPresent()) {
                    return matched;
                }
            } catch (Exception ignored) {
                List<Photo> photos = photoRepository.findAllByOriginalPath(candidate);
                if (!photos.isEmpty()) {
                    return photos.stream().max(Comparator.comparingLong(Photo::getId));
                }
            }
        }
        return Optional.empty();
    }

    private void moveThumbnailFiles(Photo photo, String oldPrefix, String newPrefix) {
        // Move each thumbnail file if it exists
        String[] thumbPaths = {
            photo.getThumbnailPath(), photo.getWebpPath(),
            photo.getSmallThumbPath(), photo.getMediumThumbPath(),
            photo.getLargeThumbPath(), photo.getBackgroundRemovedPath()
        };
        for (String thumbPath : thumbPaths) {
            if (thumbPath == null) continue;
            // The old path would have been with oldPrefix, so compute it
            String oldThumbPath = replacePrefix(thumbPath, newPrefix, oldPrefix);
            if (oldThumbPath == null) continue;
            Optional<Path> oldFileOpt = tryResolveMutableLocalPath(oldThumbPath);
            Optional<Path> newFileOpt = tryResolveMutableLocalPath(thumbPath);
            if (oldFileOpt.isEmpty() || newFileOpt.isEmpty()) {
                continue;
            }
            Path oldFile = oldFileOpt.get();
            Path newFile = newFileOpt.get();
            if (Files.exists(oldFile) && !oldFile.equals(newFile)) {
                try {
                    Files.createDirectories(newFile.getParent());
                    Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    log.warn("移动缩略图失败: {} -> {}: {}", toClientPath(oldFile), toClientPath(newFile), e.getMessage());
                }
            }
        }
    }

    private String toStoredPath(Path path, Long userId) {
        String absolute = path.toAbsolutePath().normalize().toString();
        return userPathService.tryBuildStoragePathReference(absolute, userId).orElse(absolute);
    }

    private String toClientPath(Path path) {
        if (path == null) {
            return null;
        }
        return userPathService.toDisplayPath(path.toAbsolutePath().normalize().toString(), true);
    }

    private String toClientPath(String storedOrAbsolutePath) {
        if (storedOrAbsolutePath == null || storedOrAbsolutePath.isBlank()) {
            return storedOrAbsolutePath;
        }
        try {
            return userPathService.toDisplayPath(storedOrAbsolutePath, true);
        } catch (Exception e) {
            String normalized = storedOrAbsolutePath.replace('\\', '/');
            int index = normalized.lastIndexOf('/');
            return index >= 0 ? normalized.substring(index + 1) : normalized;
        }
    }

    private String rewriteStoredPathForMove(String currentPath, Path oldParent, Path newParent, Long userId) {
        if (currentPath == null || currentPath.isBlank() || oldParent == null || newParent == null) {
            return currentPath;
        }
        try {
            Path currentAbsolute = resolveStoredPath(currentPath);
            if (!currentAbsolute.startsWith(oldParent)) {
                return currentPath;
            }
            Path relative = oldParent.relativize(currentAbsolute);
            Path targetAbsolute = newParent.resolve(relative).normalize();
            return toStoredPath(targetAbsolute, userId);
        } catch (IOException e) {
            return currentPath;
        }
    }

    private void moveStoredFile(String oldStoredPath, String newStoredPath) {
        if (oldStoredPath == null || newStoredPath == null || oldStoredPath.equals(newStoredPath)) {
            return;
        }
        Optional<Path> oldFileOpt = tryResolveMutableLocalPath(oldStoredPath);
        Optional<Path> newFileOpt = tryResolveMutableLocalPath(newStoredPath);
        if (oldFileOpt.isEmpty() || newFileOpt.isEmpty()) {
            return;
        }
        Path oldFile = oldFileOpt.get();
        Path newFile = newFileOpt.get();
        if (!Files.exists(oldFile) || oldFile.equals(newFile)) {
            return;
        }
        try {
            Files.createDirectories(newFile.getParent());
            Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("移动派生文件失败: {} -> {}: {}", oldFile, newFile, e.getMessage());
        }
    }

    private void deletePhotoFiles(Photo photo) {
        // Delete original file
        deleteFileIfExists(photo.getOriginalPath());
        // Delete thumbnails
        deleteFileIfExists(photo.getThumbnailPath());
        deleteFileIfExists(photo.getWebpPath());
        deleteFileIfExists(photo.getSmallThumbPath());
        deleteFileIfExists(photo.getMediumThumbPath());
        deleteFileIfExists(photo.getLargeThumbPath());
        deleteFileIfExists(photo.getBackgroundRemovedPath());
    }

    private void deleteFileIfExists(String path) {
        if (path == null) return;
        Optional<Path> localPath = tryResolveMutableLocalPath(path);
        if (localPath.isEmpty()) {
            log.debug("跳过非本地文件删除: {}", path);
            return;
        }
        try {
            Files.deleteIfExists(localPath.get());
        } catch (IOException e) {
            log.warn("删除文件失败: {}: {}", path, e.getMessage());
        }
    }

    private void deletePhotoDbRecords(Photo photo) {
        // Delete faces
        List<Face> faces = faceRepository.findByPhotoId(photo.getId());
        if (!faces.isEmpty()) {
            faceRepository.deleteAll(faces);
        }
        // Delete assignments
        photoAssignmentRepository.deleteByPhotoId(photo.getId());
        // Delete AI scoring
        photoAIScoringRepository.findByPhotoId(photo.getId())
                .ifPresent(photoAIScoringRepository::delete);
        // Delete photo
        photoRepository.delete(photo);
    }

    /**
     * 批量隐藏照片
     */
    @Transactional
    public int hidePhotos(List<Long> photoIds) {
        int count = 0;
        for (Long photoId : photoIds) {
            Optional<Photo> opt = photoRepository.findById(photoId);
            if (opt.isPresent()) {
                Photo photo = opt.get();
                if (photo.getIsHidden() == null || !photo.getIsHidden()) {
                    photo.setIsHidden(true);
                    photoRepository.save(photo);
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 批量显示照片（取消隐藏）
     */
    @Transactional
    public int showPhotos(List<Long> photoIds) {
        int count = 0;
        for (Long photoId : photoIds) {
            Optional<Photo> opt = photoRepository.findById(photoId);
            if (opt.isPresent()) {
                Photo photo = opt.get();
                if (photo.getIsHidden() != null && photo.getIsHidden()) {
                    photo.setIsHidden(false);
                    photoRepository.save(photo);
                    count++;
                }
            }
        }
        return count;
    }

    private void deletePhotoFile(Photo photo) throws IOException {
        // Delete original file
        if (photo.getOriginalPath() != null) {
            Path originalPath = resolveStoredPath(photo.getOriginalPath());
            if (Files.exists(originalPath)) {
                Files.delete(originalPath);
            }
        }
        // Delete thumbnails
        if (photo.getThumbnailPath() != null) {
            Files.deleteIfExists(resolveStoredPath(photo.getThumbnailPath()));
        }
        if (photo.getSmallThumbPath() != null) {
            Files.deleteIfExists(resolveStoredPath(photo.getSmallThumbPath()));
        }
        if (photo.getMediumThumbPath() != null) {
            Files.deleteIfExists(resolveStoredPath(photo.getMediumThumbPath()));
        }
        if (photo.getLargeThumbPath() != null) {
            Files.deleteIfExists(resolveStoredPath(photo.getLargeThumbPath()));
        }
        if (photo.getWebpPath() != null) {
            Files.deleteIfExists(resolveStoredPath(photo.getWebpPath()));
        }
        if (photo.getBackgroundRemovedPath() != null) {
            Files.deleteIfExists(resolveStoredPath(photo.getBackgroundRemovedPath()));
        }
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

    private Path resolveLocalDirectoryPath(String path) {
        if (path == null || path.isBlank()) {
            throw new RuntimeException("目录路径为空");
        }
        Optional<Path> localPath = tryResolveMutableLocalPath(path);
        if (localPath.isPresent()) {
            return localPath.get();
        }
        if (userPathService.isStoragePathReference(path)) {
            throw new RuntimeException("当前目录引用指向非本地存储，无法执行本地文件操作");
        }
        return Paths.get(path).toAbsolutePath().normalize();
    }

    private Album findOrCreateAlbumForPath(String dirPath, Long userId) {
        String normalizedPath = null;
        String storedPath;
        String albumName;

        if (userPathService.isStoragePathReference(dirPath)) {
            storedPath = dirPath.replace("\\", "/");
            albumName = deriveAlbumNameFromStoredPath(storedPath);
            Optional<Path> localDirectory = userPathService.tryResolveLocalStoredPhotoPath(storedPath);
            if (localDirectory.isPresent()) {
                normalizedPath = localDirectory.get().toAbsolutePath().normalize().toString().replace("\\", "/");
            }
        } else {
            Path directory = Paths.get(dirPath).toAbsolutePath().normalize();
            normalizedPath = directory.toString().replace("\\", "/");
            storedPath = toStoredPath(directory, userId);
            albumName = directory.getFileName().toString();
        }

        Optional<Album> existing = findAlbumByManagedPath(storedPath, normalizedPath, dirPath);
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new album
        Album album = new Album();
        album.setName(albumName);
        album.setPath(storedPath);
        album.setPathHash(computeSha256(storedPath));
        album.setPhotoCount(0);
        album.setUserId(userId);

        // Parse date from directory name for sorting
        album.setAlbumNameDate(parseDateFromAlbumPath(storedPath));

        return albumRepository.save(album);
    }

    private Optional<Album> findAlbumByManagedPath(String storedPath, String normalizedPath, String originalPath) {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        if (storedPath != null && !storedPath.isBlank()) {
            candidates.add(storedPath);
        }
        if (normalizedPath != null && !normalizedPath.isBlank()) {
            candidates.add(normalizedPath);
        }
        if (originalPath != null && !originalPath.isBlank()) {
            candidates.add(originalPath);
        }
        for (String candidate : candidates) {
            Optional<Album> existing = albumRepository.findByPath(candidate);
            if (existing.isPresent()) {
                return existing;
            }
            String pathHash = computeSha256(candidate);
            if (pathHash != null) {
                existing = albumRepository.findByPathHash(pathHash);
                if (existing.isPresent()) {
                    return existing;
                }
            }
        }
        return Optional.empty();
    }

    private String deriveAlbumNameFromStoredPath(String storedPath) {
        String tenantRelativePath = userPathService.extractTenantRelativePhotoPath(storedPath);
        if (tenantRelativePath == null || tenantRelativePath.isBlank()) {
            return storedPath;
        }
        Path relative = Paths.get(tenantRelativePath).normalize();
        if (relative.getFileName() == null) {
            return tenantRelativePath;
        }
        return relative.getFileName().toString();
    }

    private Optional<Path> tryResolveMutableLocalPath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        if (userPathService.isStoragePathReference(path)) {
            return userPathService.tryResolveLocalStoredPhotoPath(path);
        }
        try {
            return Optional.of(Paths.get(path).toAbsolutePath().normalize());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void ensureAlbumAccess(UserAccount currentUser, Album album) {
        if (currentUser == null || album == null) {
            throw new RuntimeException("无权访问相册");
        }
        if (currentUser.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }
        if (!Objects.equals(album.getUserId(), currentUser.getId())) {
            throw new RuntimeException("无权访问其他用户的相册");
        }
    }

    private void ensurePhotoAccess(UserAccount currentUser, List<Photo> photos) {
        if (currentUser == null) {
            throw new RuntimeException("未登录");
        }
        if (currentUser.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }
        boolean hasForeignPhoto = photos.stream().anyMatch(photo -> !Objects.equals(photo.getUserId(), currentUser.getId()));
        if (hasForeignPhoto) {
            throw new RuntimeException("无权操作其他用户的照片");
        }
    }

    private Long resolveOwnerUserId(UserAccount currentUser, List<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return currentUser == null ? null : currentUser.getId();
        }
        Long ownerUserId = photos.stream()
            .map(Photo::getUserId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(currentUser == null ? null : currentUser.getId());
        if (currentUser != null && currentUser.getRole() != UserRole.SUPER_ADMIN && !Objects.equals(ownerUserId, currentUser.getId())) {
            throw new RuntimeException("无权操作其他用户的照片");
        }
        return ownerUserId;
    }

    private LocalDateTime parseDateFromAlbumPath(String path) {
        String tenantRelative = userPathService.extractTenantRelativePhotoPath(path);
        String normalizedPath = tenantRelative == null || tenantRelative.isBlank()
            ? path
            : tenantRelative;
        String dirName = Paths.get(normalizedPath).getFileName().toString();
        Pattern datePattern = Pattern.compile("(\\d{4})[.\\-/](\\d{1,2})[.\\-/](\\d{1,2})");
        Matcher matcher = datePattern.matcher(dirName);
        if (matcher.find()) {
            try {
                int year = Integer.parseInt(matcher.group(1));
                int month = Integer.parseInt(matcher.group(2));
                int day = Integer.parseInt(matcher.group(3));
                return LocalDateTime.of(year, month, day, 0, 0);
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    private String findUniqueFilename(Path dir, String filename) {
        String baseName = filename;
        String extension = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = filename.substring(0, dotIndex);
            extension = filename.substring(dotIndex);
        }
        int counter = 2;
        String newName = baseName + " (" + counter + ")" + extension;
        while (Files.exists(dir.resolve(newName))) {
            counter++;
            newName = baseName + " (" + counter + ")" + extension;
        }
        return newName;
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
}
