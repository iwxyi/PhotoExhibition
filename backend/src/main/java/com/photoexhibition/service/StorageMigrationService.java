package com.photoexhibition.service;

import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.CommentRepository;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PhotoAIScoringRepository;
import com.photoexhibition.repository.PhotoAssignmentRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.StorageProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageMigrationService {

    private final StorageProviderRepository storageProviderRepository;
    private final StorageUploadService storageUploadService;
    private final UserPathService userPathService;
    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final FaceRepository faceRepository;
    private final PhotoAssignmentRepository photoAssignmentRepository;
    private final PhotoAIScoringRepository photoAIScoringRepository;
    private final CommentRepository commentRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> previewMigration(Map<String, Object> request) {
        StorageProvider sourceProvider = requireProvider(parseLong(request.get("sourceProviderId"), "sourceProviderId"), "源存储提供者不存在");
        StorageProvider targetProvider = requireProvider(parseLong(request.get("targetProviderId"), "targetProviderId"), "目标存储提供者不存在");
        Path sourcePath = normalizeRelativePath(request.get("sourcePath"));
        Path targetPath = normalizeRelativePath(request.get("targetPath"));
        TreeStats sourceStats = scanTree(sourceProvider, sourcePath);
        TreeStats targetStats = scanTree(targetProvider, targetPath);
        MatchSet matches = findMatches(sourceProvider, sourcePath);

        LinkedHashMap<String, Object> resp = new LinkedHashMap<>();
        resp.put("source", buildPathSummary(sourceProvider, sourcePath, sourceStats));
        resp.put("target", buildPathSummary(targetProvider, targetPath, targetStats));
        resp.put("database", buildMatchSummary(matches));
        resp.put("targetNotEmpty", targetStats.hasContent());
        resp.put("canExecute", sourceStats.hasContent() && !Objects.equals(composeLocationKey(sourceProvider, sourcePath), composeLocationKey(targetProvider, targetPath)));
        resp.put("message", sourceStats.hasContent() ? "已完成迁移预检查" : "源目录为空，暂无可迁移内容");
        return resp;
    }

    @Transactional
    public Map<String, Object> executeMigration(Map<String, Object> request) throws Exception {
        StorageProvider sourceProvider = requireProvider(parseLong(request.get("sourceProviderId"), "sourceProviderId"), "源存储提供者不存在");
        StorageProvider targetProvider = requireProvider(parseLong(request.get("targetProviderId"), "targetProviderId"), "目标存储提供者不存在");
        Path sourcePath = normalizeRelativePath(request.get("sourcePath"));
        Path targetPath = normalizeRelativePath(request.get("targetPath"));
        boolean clearTarget = parseBoolean(request.get("clearTarget"));

        if (Objects.equals(composeLocationKey(sourceProvider, sourcePath), composeLocationKey(targetProvider, targetPath))) {
            throw new IllegalArgumentException("源目录和目标目录不能相同");
        }
        if (sourceProvider.getId().equals(targetProvider.getId())) {
            if (sourcePath.getNameCount() == 0 || targetPath.getNameCount() == 0) {
                throw new IllegalArgumentException("同一存储内迁移时，源目录和目标目录都不能是根目录");
            }
            if (startsWithPath(targetPath, sourcePath) || startsWithPath(sourcePath, targetPath)) {
                throw new IllegalArgumentException("同一存储内迁移时，源目录和目标目录不能互相包含");
            }
        }

        TreeStats sourceStats = scanTree(sourceProvider, sourcePath);
        if (!sourceStats.hasContent()) {
            throw new IllegalArgumentException("源目录为空，暂无可迁移内容");
        }

        TreeStats targetStats = scanTree(targetProvider, targetPath);
        if (targetStats.hasContent() && !clearTarget) {
            throw new IllegalArgumentException("目标目录非空，请先勾选清空目标目录");
        }
        if (targetStats.hasContent()) {
            clearDirectoryInternal(targetProvider, targetPath);
        }

        MatchSet matches = findMatches(sourceProvider, sourcePath);
        copyTree(sourceProvider, sourcePath, targetProvider, targetPath);
        if (sourcePath.getNameCount() > 0 || !sourceProvider.getId().equals(targetProvider.getId())) {
            storageUploadService.deletePath(sourceProvider, null, sourcePath);
        }

        int updatedAlbumCount = rewriteAlbumPaths(matches.albums, sourceProvider, sourcePath, targetProvider, targetPath);
        int updatedPhotoCount = rewritePhotoPaths(matches.photos, sourceProvider, sourcePath, targetProvider, targetPath);

        LinkedHashMap<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("source", buildPathSummary(sourceProvider, sourcePath, sourceStats));
        resp.put("target", buildPathSummary(targetProvider, targetPath, scanTree(targetProvider, targetPath)));
        resp.put("database", buildMatchSummary(matches));
        resp.put("rewrittenAlbumCount", updatedAlbumCount);
        resp.put("rewrittenPhotoCount", updatedPhotoCount);
        resp.put("clearedTarget", targetStats.hasContent() && clearTarget);
        resp.put("executedAt", LocalDateTime.now());
        resp.put("message", "存储迁移已完成");
        return resp;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> previewCleanup(Map<String, Object> request) {
        StorageProvider provider = requireProvider(parseLong(request.get("providerId"), "providerId"), "存储提供者不存在");
        Path targetPath = normalizeRelativePath(request.get("path"));
        TreeStats stats = scanTree(provider, targetPath);
        MatchSet matches = findMatches(provider, targetPath);

        LinkedHashMap<String, Object> resp = new LinkedHashMap<>();
        resp.put("target", buildPathSummary(provider, targetPath, stats));
        resp.put("database", buildMatchSummary(matches));
        resp.put("hasContent", stats.hasContent() || !matches.isEmpty());
        resp.put("message", stats.hasContent() || !matches.isEmpty() ? "已完成清理预检查" : "目标目录为空");
        return resp;
    }

    @Transactional
    public Map<String, Object> executeCleanup(Map<String, Object> request) throws Exception {
        StorageProvider provider = requireProvider(parseLong(request.get("providerId"), "providerId"), "存储提供者不存在");
        Path targetPath = normalizeRelativePath(request.get("path"));
        TreeStats beforeStats = scanTree(provider, targetPath);
        MatchSet matches = findMatches(provider, targetPath);
        clearDirectoryInternal(provider, targetPath);

        LinkedHashMap<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("target", buildPathSummary(provider, targetPath, beforeStats));
        resp.put("database", buildMatchSummary(matches));
        resp.put("executedAt", LocalDateTime.now());
        resp.put("message", "目录清理已完成");
        return resp;
    }

    private void clearDirectoryInternal(StorageProvider provider, Path targetPath) throws Exception {
        MatchSet matches = findMatches(provider, targetPath);
        deleteMatchedRecords(matches);
        storageUploadService.deletePath(provider, null, targetPath);
        storageUploadService.createDirectory(provider, null, targetPath);
    }

    private void deleteMatchedRecords(MatchSet matches) {
        if (matches.photos.isEmpty() && matches.albums.isEmpty()) {
            return;
        }
        List<Long> albumIds = matches.albums.stream()
            .map(Album::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        for (Photo photo : matches.photos) {
            if (photo.getId() == null) {
                continue;
            }
            faceRepository.deleteByPhotoId(photo.getId());
            photoAssignmentRepository.deleteByPhotoId(photo.getId());
            photoAIScoringRepository.deleteByPhotoId(photo.getId());
        }
        if (!matches.photos.isEmpty()) {
            photoRepository.deleteAll(matches.photos);
        }
        if (!albumIds.isEmpty()) {
            commentRepository.deleteByAlbumIdIn(albumIds);
            albumRepository.deleteAll(matches.albums);
        }
    }

    private int rewriteAlbumPaths(List<Album> albums,
                                  StorageProvider sourceProvider,
                                  Path sourcePath,
                                  StorageProvider targetProvider,
                                  Path targetPath) {
        int updated = 0;
        for (Album album : albums) {
            String rewritten = rewriteStoredPath(album.getPath(), album.getUserId(), sourceProvider, sourcePath, targetProvider, targetPath);
            if (!Objects.equals(rewritten, album.getPath())) {
                album.setPath(rewritten);
                album.setPathHash(computePathHash(rewritten));
                updated++;
            }
        }
        if (updated > 0) {
            albumRepository.saveAll(albums);
        }
        return updated;
    }

    private int rewritePhotoPaths(List<Photo> photos,
                                  StorageProvider sourceProvider,
                                  Path sourcePath,
                                  StorageProvider targetProvider,
                                  Path targetPath) {
        int updated = 0;
        for (Photo photo : photos) {
            String originalPath = rewriteStoredPath(photo.getOriginalPath(), photo.getUserId(), sourceProvider, sourcePath, targetProvider, targetPath);
            String thumbnailPath = rewriteStoredPath(photo.getThumbnailPath(), photo.getUserId(), sourceProvider, sourcePath, targetProvider, targetPath);
            String webpPath = rewriteStoredPath(photo.getWebpPath(), photo.getUserId(), sourceProvider, sourcePath, targetProvider, targetPath);
            String smallThumbPath = rewriteStoredPath(photo.getSmallThumbPath(), photo.getUserId(), sourceProvider, sourcePath, targetProvider, targetPath);
            String mediumThumbPath = rewriteStoredPath(photo.getMediumThumbPath(), photo.getUserId(), sourceProvider, sourcePath, targetProvider, targetPath);
            String largeThumbPath = rewriteStoredPath(photo.getLargeThumbPath(), photo.getUserId(), sourceProvider, sourcePath, targetProvider, targetPath);
            String backgroundRemovedPath = rewriteStoredPath(photo.getBackgroundRemovedPath(), photo.getUserId(), sourceProvider, sourcePath, targetProvider, targetPath);
            if (!Objects.equals(originalPath, photo.getOriginalPath())
                || !Objects.equals(thumbnailPath, photo.getThumbnailPath())
                || !Objects.equals(webpPath, photo.getWebpPath())
                || !Objects.equals(smallThumbPath, photo.getSmallThumbPath())
                || !Objects.equals(mediumThumbPath, photo.getMediumThumbPath())
                || !Objects.equals(largeThumbPath, photo.getLargeThumbPath())
                || !Objects.equals(backgroundRemovedPath, photo.getBackgroundRemovedPath())) {
                photo.setOriginalPath(originalPath);
                photo.setPathHash(computePathHash(originalPath));
                photo.setThumbnailPath(thumbnailPath);
                photo.setWebpPath(webpPath);
                photo.setSmallThumbPath(smallThumbPath);
                photo.setMediumThumbPath(mediumThumbPath);
                photo.setLargeThumbPath(largeThumbPath);
                photo.setBackgroundRemovedPath(backgroundRemovedPath);
                updated++;
            }
        }
        if (updated > 0) {
            photoRepository.saveAll(photos);
        }
        return updated;
    }

    private String rewriteStoredPath(String currentPath,
                                     Long ownerUserId,
                                     StorageProvider sourceProvider,
                                     Path sourcePath,
                                     StorageProvider targetProvider,
                                     Path targetPath) {
        StoredPathDescriptor descriptor = describeStoredPath(currentPath);
        if (descriptor == null || !descriptor.providerId.equals(sourceProvider.getId()) || !startsWithPath(descriptor.fullRelativePath, sourcePath)) {
            return currentPath;
        }
        Path remainder = relativizeSafe(sourcePath, descriptor.fullRelativePath);
        Path targetFullPath = targetPath.resolve(remainder).normalize();
        return buildStoredPath(targetProvider, ownerUserId, targetFullPath);
    }

    private String buildStoredPath(StorageProvider provider, Long ownerUserId, Path fullRelativePath) {
        Path normalized = fullRelativePath == null ? Paths.get("") : fullRelativePath.normalize();
        if (ownerUserId != null && normalized.getNameCount() > 0 && normalized.getName(0).toString().equals(String.valueOf(ownerUserId))) {
            Path rest = normalized.getNameCount() == 1 ? Paths.get("") : normalized.subpath(1, normalized.getNameCount());
            return userPathService.buildStoragePathReference(provider.getId(), ownerUserId, rest.toString());
        }
        return userPathService.buildStoragePathReference(provider.getId(), null, normalized.toString());
    }

    private MatchSet findMatches(StorageProvider provider, Path sourcePath) {
        List<Album> matchedAlbums = new ArrayList<>();
        for (Album album : albumRepository.findAll()) {
            StoredPathDescriptor descriptor = describeStoredPath(album.getPath());
            if (descriptor != null && descriptor.providerId.equals(provider.getId()) && startsWithPath(descriptor.fullRelativePath, sourcePath)) {
                matchedAlbums.add(album);
            }
        }

        List<Photo> matchedPhotos = new ArrayList<>();
        for (Photo photo : photoRepository.findAll()) {
            if (photoMatches(photo, provider.getId(), sourcePath)) {
                matchedPhotos.add(photo);
            }
        }
        return new MatchSet(matchedAlbums, matchedPhotos);
    }

    private boolean photoMatches(Photo photo, Long providerId, Path sourcePath) {
        return pathMatches(photo.getOriginalPath(), providerId, sourcePath)
            || pathMatches(photo.getThumbnailPath(), providerId, sourcePath)
            || pathMatches(photo.getWebpPath(), providerId, sourcePath)
            || pathMatches(photo.getSmallThumbPath(), providerId, sourcePath)
            || pathMatches(photo.getMediumThumbPath(), providerId, sourcePath)
            || pathMatches(photo.getLargeThumbPath(), providerId, sourcePath)
            || pathMatches(photo.getBackgroundRemovedPath(), providerId, sourcePath);
    }

    private boolean pathMatches(String rawPath, Long providerId, Path sourcePath) {
        StoredPathDescriptor descriptor = describeStoredPath(rawPath);
        return descriptor != null && descriptor.providerId.equals(providerId) && startsWithPath(descriptor.fullRelativePath, sourcePath);
    }

    private StoredPathDescriptor describeStoredPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        UserPathService.StoragePathReference reference = userPathService.parseStoragePathReference(rawPath);
        if (reference != null) {
            Path fullRelative = reference.getRelativePath();
            if (reference.getUserId() != null) {
                fullRelative = Paths.get(String.valueOf(reference.getUserId())).resolve(fullRelative).normalize();
            }
            return new StoredPathDescriptor(reference.getStorageProviderId(), reference.getUserId(), fullRelative);
        }
        Path absolutePath;
        try {
            absolutePath = Paths.get(rawPath).toAbsolutePath().normalize();
        } catch (Exception ignored) {
            return null;
        }
        StorageProvider matchedProvider = null;
        Path matchedBase = null;
        for (StorageProvider provider : storageProviderRepository.findAllByOrderByPriorityAscIdAsc()) {
            if (provider.getType() != com.photoexhibition.entity.StorageType.LOCAL) {
                continue;
            }
            Path base = userPathService.resolveStorageProviderBaseDirectory(provider);
            if (base != null && absolutePath.startsWith(base) && (matchedBase == null || base.getNameCount() > matchedBase.getNameCount())) {
                matchedProvider = provider;
                matchedBase = base;
            }
        }
        if (matchedProvider == null || matchedBase == null) {
            return null;
        }
        return new StoredPathDescriptor(matchedProvider.getId(), null, matchedBase.relativize(absolutePath).normalize());
    }

    private void copyTree(StorageProvider sourceProvider, Path sourcePath, StorageProvider targetProvider, Path targetPath) throws Exception {
        storageUploadService.createDirectory(targetProvider, null, targetPath);
        Map<String, Object> listing = storageUploadService.listDirectory(sourceProvider, null, sourcePath);
        List<Map<String, Object>> directories = castItemList(listing.get("directories"));
        for (Map<String, Object> directory : directories) {
            String name = String.valueOf(directory.get("name"));
            copyTree(sourceProvider, sourcePath.resolve(name).normalize(), targetProvider, targetPath.resolve(name).normalize());
        }
        List<Map<String, Object>> files = castItemList(listing.get("files"));
        for (Map<String, Object> file : files) {
            String name = String.valueOf(file.get("name"));
            Path sourceFile = sourcePath.resolve(name).normalize();
            Path targetFile = targetPath.resolve(name).normalize();
            storageUploadService.storeDownloadedFile(
                targetProvider,
                null,
                targetFile,
                storageUploadService.downloadFile(sourceProvider, null, sourceFile)
            );
        }
    }

    private TreeStats scanTree(StorageProvider provider, Path rootPath) {
        try {
            Map<String, Object> listing = storageUploadService.listDirectory(provider, null, rootPath);
            TreeStats stats = new TreeStats();
            List<Map<String, Object>> directories = castItemList(listing.get("directories"));
            List<Map<String, Object>> files = castItemList(listing.get("files"));
            stats.directoryCount += directories.size();
            stats.fileCount += files.size();
            for (Map<String, Object> file : files) {
                Object size = file.get("size");
                if (size instanceof Number) {
                    stats.totalBytes += ((Number) size).longValue();
                }
                if (stats.samples.size() < 6) {
                    stats.samples.add(String.valueOf(file.get("name")));
                }
            }
            for (Map<String, Object> directory : directories) {
                if (stats.samples.size() < 6) {
                    stats.samples.add(String.valueOf(directory.get("name")) + "/");
                }
                String name = String.valueOf(directory.get("name"));
                stats.merge(scanTree(provider, rootPath.resolve(name).normalize()));
            }
            return stats;
        } catch (Exception e) {
            log.warn("扫描目录失败 provider={} path={}: {}", provider.getId(), rootPath, e.getMessage());
            return new TreeStats();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castItemList(Object value) {
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?>) {
                    result.add((Map<String, Object>) item);
                }
            }
            return result;
        }
        return List.of();
    }

    private Map<String, Object> buildPathSummary(StorageProvider provider, Path path, TreeStats stats) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("providerId", provider.getId());
        summary.put("providerName", provider.getName());
        summary.put("path", toDisplayPath(path));
        summary.put("directoryCount", stats.directoryCount);
        summary.put("fileCount", stats.fileCount);
        summary.put("totalBytes", stats.totalBytes);
        summary.put("samples", stats.samples);
        return summary;
    }

    private Map<String, Object> buildMatchSummary(MatchSet matches) {
        LinkedHashMap<String, Object> summary = new LinkedHashMap<>();
        summary.put("albumCount", matches.albums.size());
        summary.put("photoCount", matches.photos.size());
        summary.put("albumSamples", sampleAlbumPaths(matches.albums));
        summary.put("photoSamples", samplePhotoPaths(matches.photos));
        return summary;
    }

    private List<String> sampleAlbumPaths(Collection<Album> albums) {
        List<String> samples = new ArrayList<>();
        for (Album album : albums) {
            if (samples.size() >= 6) {
                break;
            }
            samples.add(userPathService.toDisplayPath(album.getPath(), true));
        }
        return samples;
    }

    private List<String> samplePhotoPaths(Collection<Photo> photos) {
        List<String> samples = new ArrayList<>();
        for (Photo photo : photos) {
            if (samples.size() >= 6) {
                break;
            }
            samples.add(userPathService.toDisplayPath(photo.getOriginalPath(), true));
        }
        return samples;
    }

    private String toDisplayPath(Path path) {
        if (path == null || path.getNameCount() == 0) {
            return "/";
        }
        return "/" + path.toString().replace('\\', '/');
    }

    private String composeLocationKey(StorageProvider provider, Path path) {
        return provider.getId() + ":" + path.normalize();
    }

    private Path normalizeRelativePath(Object value) {
        String raw = value == null ? "" : String.valueOf(value).trim();
        if (raw.isBlank() || "/".equals(raw)) {
            return Paths.get("");
        }
        String normalized = raw.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            return Paths.get("");
        }
        Path path = Paths.get(normalized).normalize();
        if (path.startsWith("..")) {
            throw new IllegalArgumentException("目录不能越过存储根路径");
        }
        return path;
    }

    private boolean startsWithPath(Path path, Path prefix) {
        Path normalizedPrefix = prefix == null ? Paths.get("") : prefix.normalize();
        if (normalizedPrefix.getNameCount() == 0) {
            return true;
        }
        return path != null && path.normalize().startsWith(normalizedPrefix);
    }

    private Path relativizeSafe(Path parent, Path child) {
        Path normalizedParent = parent == null ? Paths.get("") : parent.normalize();
        Path normalizedChild = child == null ? Paths.get("") : child.normalize();
        if (normalizedParent.getNameCount() == 0) {
            return normalizedChild;
        }
        return normalizedParent.relativize(normalizedChild);
    }

    private StorageProvider requireProvider(Long providerId, String message) {
        if (providerId == null) {
            throw new IllegalArgumentException(message);
        }
        return storageProviderRepository.findById(providerId)
            .orElseThrow(() -> new IllegalArgumentException(message));
    }

    private Long parseLong(Object value, String field) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " 格式不正确");
        }
    }

    private boolean parseBoolean(Object value) {
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String computePathHash(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(path.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("生成路径哈希失败");
        }
    }

    private static final class StoredPathDescriptor {
        private final Long providerId;
        private final Long userId;
        private final Path fullRelativePath;

        private StoredPathDescriptor(Long providerId, Long userId, Path fullRelativePath) {
            this.providerId = providerId;
            this.userId = userId;
            this.fullRelativePath = fullRelativePath == null ? Paths.get("") : fullRelativePath.normalize();
        }
    }

    private static final class MatchSet {
        private final List<Album> albums;
        private final List<Photo> photos;

        private MatchSet(List<Album> albums, List<Photo> photos) {
            this.albums = albums;
            this.photos = photos;
        }

        private boolean isEmpty() {
            return albums.isEmpty() && photos.isEmpty();
        }
    }

    private static final class TreeStats {
        private int directoryCount;
        private int fileCount;
        private long totalBytes;
        private final List<String> samples = new ArrayList<>();

        private void merge(TreeStats other) {
            if (other == null) {
                return;
            }
            this.directoryCount += other.directoryCount;
            this.fileCount += other.fileCount;
            this.totalBytes += other.totalBytes;
            for (String sample : other.samples) {
                if (this.samples.size() >= 6) {
                    break;
                }
                this.samples.add(sample);
            }
        }

        private boolean hasContent() {
            return directoryCount > 0 || fileCount > 0;
        }
    }
}
