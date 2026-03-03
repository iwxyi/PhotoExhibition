package com.photoexhibition.service;

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

    @Value("${photo.scan.base-path}")
    private String basePath;

    /**
     * Get available move targets for photos in an album.
     * Returns parent directory and sibling directories (other albums at the same level).
     */
    public Map<String, Object> getMoveTargets(Long albumId) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));

        Path albumPath = Paths.get(album.getPath()).toAbsolutePath().normalize();
        Path parentDir = albumPath.getParent();
        Map<String, Object> result = new HashMap<>();

        // Parent directory info
        if (parentDir != null) {
            Path grandParent = parentDir.getParent();
            if (grandParent != null) {
                result.put("parentDir", Map.of(
                        "name", parentDir.getFileName().toString(),
                        "path", parentDir.toString()
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
                          dir.put("path", p.toString());
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
                          dir.put("path", p.toString());
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
     * Handles: physical file move, DB path updates, album creation/removal.
     */
    @Transactional
    public Map<String, Object> movePhotos(List<Long> photoIds, String targetDirPath) {
        Map<String, Object> result = new HashMap<>();

        if (photoIds == null || photoIds.isEmpty()) {
            result.put("success", false);
            result.put("message", "未选择照片");
            return result;
        }

        Path targetDir = Paths.get(targetDirPath).toAbsolutePath().normalize();

        // Create target directory if it doesn't exist
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "创建目标目录失败: " + e.getMessage());
            return result;
        }

        List<Photo> photos = photoRepository.findAllById(photoIds);
        if (photos.isEmpty()) {
            result.put("success", false);
            result.put("message", "未找到指定照片");
            return result;
        }

        // Track source albums for later cleanup
        Set<Long> sourceAlbumIds = photos.stream()
                .map(Photo::getAlbumId)
                .collect(Collectors.toSet());

        // Find or create target album
        Album targetAlbum = findOrCreateAlbumForPath(targetDir.toString());

        int movedCount = 0;
        List<String> errors = new ArrayList<>();

        for (Photo photo : photos) {
            try {
                movePhotoToDir(photo, targetDir, targetAlbum);
                movedCount++;
            } catch (Exception e) {
                log.error("移动照片失败: {} -> {}", photo.getOriginalPath(), targetDir, e);
                errors.add(photo.getFilename() + ": " + e.getMessage());
            }
        }

        // Save all updated photos
        photoRepository.saveAll(photos);

        // Update target album photo count
        long targetCount = photoRepository.countByAlbumId(targetAlbum.getId());
        targetAlbum.setPhotoCount((int) targetCount);
        albumRepository.save(targetAlbum);

        // Update source album photo counts, remove empty albums
        List<Long> removedAlbumIds = new ArrayList<>();
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
    public Map<String, Object> deletePhotos(List<Long> photoIds) {
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
                log.error("删除照片失败: {}", photo.getOriginalPath(), e);
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

    // ======================== Internal methods ========================

    private void movePhotoToDir(Photo photo, Path targetDir, Album targetAlbum) throws IOException {
        Path sourceFile = Paths.get(photo.getOriginalPath()).toAbsolutePath().normalize();
        if (!Files.exists(sourceFile)) {
            throw new IOException("源文件不存在: " + sourceFile);
        }

        // Determine target file path (handle name conflicts)
        String filename = sourceFile.getFileName().toString();
        Path targetFile = targetDir.resolve(filename);
        if (Files.exists(targetFile) && !targetFile.equals(sourceFile)) {
            filename = findUniqueFilename(targetDir, filename);
            targetFile = targetDir.resolve(filename);
        }

        // Physical move
        Files.move(sourceFile, targetFile, StandardCopyOption.ATOMIC_MOVE);

        // Update photo paths
        String oldOriginal = photo.getOriginalPath();
        String newOriginal = targetFile.toString();

        // For thumbnail paths, we need to update the prefix
        // Thumbnails are stored in .thumbnails subdirectory relative to the photo
        Path oldParent = Paths.get(oldOriginal).getParent();
        Path newParent = targetFile.getParent();

        photo.setOriginalPath(newOriginal);
        photo.setPathHash(computeSha256(newOriginal));
        photo.setFilename(filename);
        photo.setAlbumId(targetAlbum.getId());

        // Update thumbnail paths (replace parent directory prefix)
        if (oldParent != null && newParent != null) {
            String oldPrefix = oldParent.toString();
            String newPrefix = newParent.toString();
            photo.setThumbnailPath(replacePrefix(photo.getThumbnailPath(), oldPrefix, newPrefix));
            photo.setWebpPath(replacePrefix(photo.getWebpPath(), oldPrefix, newPrefix));
            photo.setSmallThumbPath(replacePrefix(photo.getSmallThumbPath(), oldPrefix, newPrefix));
            photo.setMediumThumbPath(replacePrefix(photo.getMediumThumbPath(), oldPrefix, newPrefix));
            photo.setLargeThumbPath(replacePrefix(photo.getLargeThumbPath(), oldPrefix, newPrefix));
            photo.setBackgroundRemovedPath(replacePrefix(photo.getBackgroundRemovedPath(), oldPrefix, newPrefix));

            // Move thumbnail files too
            moveThumbnailFiles(photo, oldPrefix, newPrefix);
        }
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
            Path oldFile = Paths.get(oldThumbPath);
            Path newFile = Paths.get(thumbPath);
            if (Files.exists(oldFile) && !oldFile.equals(newFile)) {
                try {
                    Files.createDirectories(newFile.getParent());
                    Files.move(oldFile, newFile, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    log.warn("移动缩略图失败: {} -> {}: {}", oldFile, newFile, e.getMessage());
                }
            }
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
        try {
            Files.deleteIfExists(Paths.get(path));
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

    private Album findOrCreateAlbumForPath(String dirPath) {
        String normalizedPath = dirPath.replace("\\", "/");
        Optional<Album> existing = albumRepository.findByPath(normalizedPath);
        if (existing.isEmpty()) {
            existing = albumRepository.findByPath(dirPath);
        }
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new album
        Path dir = Paths.get(dirPath);
        Album album = new Album();
        album.setName(dir.getFileName().toString());
        album.setPath(normalizedPath);
        album.setPathHash(computeSha256(normalizedPath));
        album.setPhotoCount(0);

        // Parse date from directory name for sorting
        album.setAlbumNameDate(parseDateFromAlbumPath(normalizedPath));

        return albumRepository.save(album);
    }

    private LocalDateTime parseDateFromAlbumPath(String path) {
        String dirName = Paths.get(path).getFileName().toString();
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
