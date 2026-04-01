package com.photoexhibition.service;

import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.service.PhotoScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final PhotoScanService photoScanService;
    private final ScanTaskService scanTaskService;
    private final PlatformTransactionManager transactionManager;
    private final UserStorageService userStorageService;
    private final UserPathService userPathService;
    private final StorageProviderService storageProviderService;
    private final StorageUploadService storageUploadService;

    private final ScheduledExecutorService uploadProcessExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentLinkedQueue<UploadScanRequest> pendingUploadScans = new ConcurrentLinkedQueue<>();
    private volatile ScheduledFuture<?> uploadProcessFuture;

    private static class UploadScanRequest {
        private final UserAccount user;
        private final Path rootPath;
        private final Long storageProviderId;

        private UploadScanRequest(UserAccount user, Path rootPath, Long storageProviderId) {
            this.user = user;
            this.rootPath = rootPath;
            this.storageProviderId = storageProviderId;
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class UploadResult {
        private int saved;
        private boolean scanQueued;
        private String scanMessage;
        private Long storageProviderId;
        private String storageProviderName;
        private String storageProviderType;
    }

    /**
     * 移动/重命名文件夹，同时更新数据库中的相册/照片路径
     */
    @Transactional
    public void moveFolder(String sourcePath, String targetPath) throws Exception {
        Path source = resolvePath(sourcePath);
        Path target = resolvePath(targetPath);

        if (!Files.exists(source)) {
            throw new IllegalArgumentException("源目录不存在: " + source);
        }
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("源路径不是目录: " + source);
        }
        if (Files.exists(target)) {
            throw new IllegalArgumentException("目标已存在: " + target);
        }

        // 先移动文件系统
        Files.createDirectories(target.getParent());
        Files.move(source, target);

        // 标准化路径前缀
        String oldPrefix = source.toString().replace("\\", "/");
        Path oldRoot = source.toAbsolutePath().normalize();
        Path newRoot = target.toAbsolutePath().normalize();

        // 更新相册路径（使用标准化路径匹配）
        List<Album> allAlbums = findAlbumsInSameScope(source, oldPrefix);
        List<Album> albumsToUpdate = new ArrayList<>();
        for (Album a : allAlbums) {
            if (a.getPath() == null || a.getPath().isBlank()) {
                continue;
            }
            String rewrittenPath = rewriteStoredPathForDirectoryMove(a.getPath(), oldRoot, newRoot, a.getUserId());
            if (!java.util.Objects.equals(rewrittenPath, a.getPath())) {
                a.setPath(rewrittenPath);
                a.setPathHash(computePathHash(rewrittenPath));
                albumsToUpdate.add(a);
            }
        }
        albumRepository.saveAll(albumsToUpdate);

        // 更新照片路径
        List<Photo> allPhotos = findPhotosInSameScope(source, oldPrefix);
        List<Photo> photosToUpdate = new ArrayList<>();
        for (Photo p : allPhotos) {
            String rewrittenOriginalPath = rewriteStoredPathForDirectoryMove(p.getOriginalPath(), oldRoot, newRoot, p.getUserId());
            String rewrittenThumbnailPath = rewriteStoredPathForDirectoryMove(p.getThumbnailPath(), oldRoot, newRoot, p.getUserId());
            String rewrittenWebpPath = rewriteStoredPathForDirectoryMove(p.getWebpPath(), oldRoot, newRoot, p.getUserId());
            String rewrittenSmallThumbPath = rewriteStoredPathForDirectoryMove(p.getSmallThumbPath(), oldRoot, newRoot, p.getUserId());
            String rewrittenMediumThumbPath = rewriteStoredPathForDirectoryMove(p.getMediumThumbPath(), oldRoot, newRoot, p.getUserId());
            String rewrittenLargeThumbPath = rewriteStoredPathForDirectoryMove(p.getLargeThumbPath(), oldRoot, newRoot, p.getUserId());
            String rewrittenBackgroundRemovedPath = rewriteStoredPathForDirectoryMove(p.getBackgroundRemovedPath(), oldRoot, newRoot, p.getUserId());

            if (!java.util.Objects.equals(rewrittenOriginalPath, p.getOriginalPath()) ||
                !java.util.Objects.equals(rewrittenThumbnailPath, p.getThumbnailPath()) ||
                !java.util.Objects.equals(rewrittenWebpPath, p.getWebpPath()) ||
                !java.util.Objects.equals(rewrittenSmallThumbPath, p.getSmallThumbPath()) ||
                !java.util.Objects.equals(rewrittenMediumThumbPath, p.getMediumThumbPath()) ||
                !java.util.Objects.equals(rewrittenLargeThumbPath, p.getLargeThumbPath()) ||
                !java.util.Objects.equals(rewrittenBackgroundRemovedPath, p.getBackgroundRemovedPath())) {
                p.setOriginalPath(rewrittenOriginalPath);
                p.setPathHash(computePathHash(rewrittenOriginalPath));
                p.setThumbnailPath(rewrittenThumbnailPath);
                p.setWebpPath(rewrittenWebpPath);
                p.setSmallThumbPath(rewrittenSmallThumbPath);
                p.setMediumThumbPath(rewrittenMediumThumbPath);
                p.setLargeThumbPath(rewrittenLargeThumbPath);
                p.setBackgroundRemovedPath(rewrittenBackgroundRemovedPath);
                photosToUpdate.add(p);
            }
        }
        photoRepository.saveAll(photosToUpdate);
    }

    /**
     * 删除目录，并删除其下关联的相册/照片记录
     */
    @Transactional
    public void deleteFolder(String folderPath) throws Exception {
        Path dir = resolvePath(folderPath);
        if (!Files.exists(dir)) {
            throw new IllegalArgumentException("目录不存在: " + dir);
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("路径不是目录: " + dir);
        }

        // 标准化路径前缀
        String prefix = dir.toString().replace("\\", "/");
        Long scopedUserId = extractScopedUserId(dir);
        LinkedHashSet<String> prefixCandidates = buildManagedPathCandidates(prefix, scopedUserId);

        // 找到匹配的相册（使用标准化路径匹配）
        List<Album> allAlbums = findAlbumsInSameScope(dir, prefix);
        List<Album> albumsToDelete = new ArrayList<>();
        for (Album a : allAlbums) {
            if (a.getPath() != null) {
                String normalizedPath = a.getPath().replace("\\", "/");
                if (prefixCandidates.stream().anyMatch(normalizedPath::startsWith)) {
                    albumsToDelete.add(a);
                }
            }
        }

        List<Long> albumIds = new ArrayList<>();
        for (Album a : albumsToDelete) {
            albumIds.add(a.getId());
        }
        if (!albumIds.isEmpty()) {
            photoRepository.deleteByAlbumIdIn(albumIds);
            albumRepository.deleteAll(albumsToDelete);
        }

        // 删除文件系统目录
        deleteDirectoryRecursive(dir);
    }

    /**
     * 列出指定目录下一级子目录
     */
    public List<String> listDirectories(String folderPath) throws Exception {
        Path dir = resolvePath(folderPath);
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream
                .filter(Files::isDirectory)
                .map(path -> toClientBrowserPath(path, null))
                .collect(Collectors.toList());
        }
    }

    public List<String> listDirectories(String folderPath, Path scopedRoot) throws Exception {
        Path dir = resolveScopedLocalPath(folderPath, scopedRoot);
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream
                .filter(Files::isDirectory)
                .map(path -> toClientBrowserPath(path, scopedRoot))
                .collect(Collectors.toList());
        }
    }

    /**
     * 列出指定目录下的文件和文件夹（用于文件浏览器）
     */
    public Map<String, Object> listFilesAndDirectories(String folderPath) throws Exception {
        Path dir = resolvePath(folderPath);
        return buildLocalDirectoryListing(folderPath, dir, null);
    }

    public Map<String, Object> listFilesAndDirectories(String folderPath, Path scopedRoot) throws Exception {
        Path dir = resolveScopedLocalPath(folderPath, scopedRoot);
        return buildLocalDirectoryListing(folderPath, dir, scopedRoot);
    }

    private Map<String, Object> buildLocalDirectoryListing(String folderPath, Path dir, Path scopedRoot) throws Exception {
        Map<String, Object> result = new HashMap<>();
        
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            result.put("path", normalizeBrowserPath(folderPath, scopedRoot));
            result.put("parent", null);
            result.put("directories", List.of());
            result.put("files", List.of());
            return result;
        }

        List<Map<String, Object>> directories = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();

        try (var stream = Files.list(dir)) {
            stream.forEach(path -> {
                try {
                    String fileName = path.getFileName().toString();
                    if (!Files.exists(path)) {
                        // 可能是失效的软链接，直接跳过
                        return;
                    }
                    boolean isDir = Files.isDirectory(path);
                    // 跳过以"."开头的隐藏文件和文件夹
                    if (fileName.startsWith(".")) {
                        return;
                    }
                    
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", fileName);
                    item.put("path", toClientBrowserPath(path, scopedRoot));
                    item.put("isDirectory", isDir);
                    
                    if (isDir) {
                        // 查找对应的相册，获取封面图片信息
                        Optional<Album> albumOpt = findAlbumByDirectoryPath(path);
                        if (albumOpt.isPresent()) {
                            Album album = albumOpt.get();
                            item.put("photoCount", album.getPhotoCount() != null ? album.getPhotoCount() : 0);
                            
                            // 获取封面图片（最多3张，排除隐藏）
                            List<Photo> photos = photoRepository.findByAlbumIdAndIsHiddenFalse(album.getId(),
                                org.springframework.data.domain.PageRequest.of(0, 20)).getContent();
                            
                            if (!photos.isEmpty()) {
                                // 查找竖图和横图
                                Photo verticalPhoto = null;
                                List<Photo> horizontalPhotos = new ArrayList<>(2);
                                
                                for (Photo p : photos) {
                                    if (p.getHeight() != null && p.getWidth() != null && p.getHeight() > p.getWidth()) {
                                        if (verticalPhoto == null) {
                                            verticalPhoto = p;
                                            continue;
                                        }
                                    }
                                    if (p.getHeight() != null && p.getWidth() != null && p.getWidth() > p.getHeight()) {
                                        if (horizontalPhotos.size() < 2) {
                                            horizontalPhotos.add(p);
                                        }
                                    }
                                    if (verticalPhoto != null && horizontalPhotos.size() >= 2) {
                                        break;
                                    }
                                }
                                
                                if (verticalPhoto == null) {
                                    verticalPhoto = photos.get(0);
                                }
                                
                                if (horizontalPhotos.size() < 2) {
                                    for (Photo p : photos) {
                                        if (horizontalPhotos.size() >= 2) break;
                                        if (p.getId().equals(verticalPhoto.getId())) continue;
                                        boolean alreadyUsed = horizontalPhotos.stream().anyMatch(h -> h.getId().equals(p.getId()));
                                        if (!alreadyUsed) {
                                            horizontalPhotos.add(p);
                                        }
                                    }
                                }
                                
                                // 转换为DTO格式
                                item.put("leftVertical", convertPhotoToMap(verticalPhoto));
                                if (horizontalPhotos.size() > 0) {
                                    item.put("rightTop", convertPhotoToMap(horizontalPhotos.get(0)));
                                }
                                if (horizontalPhotos.size() > 1) {
                                    item.put("rightBottom", convertPhotoToMap(horizontalPhotos.get(1)));
                                }
                            }
                        } else {
                            item.put("photoCount", 0);
                        }
                        directories.add(item);
                    } else {
                        item.put("size", Files.size(path));
                        item.put("lastModified", Files.getLastModifiedTime(path).toMillis());
                        
                        // 检查是否是图片文件，如果是则查找对应的Photo记录获取缩略图
                        String lowerName = fileName.toLowerCase();
                        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || 
                            lowerName.endsWith(".png") || lowerName.endsWith(".heic") ||
                            lowerName.endsWith(".raw") || lowerName.endsWith(".cr2") ||
                            lowerName.endsWith(".nef") || lowerName.endsWith(".arw")) {
                            Optional<Photo> photoOpt = findPhotoByPath(path);
                            if (photoOpt.isPresent()) {
                                Photo photo = photoOpt.get();
                                item.put("thumbnail", convertPhotoToMap(photo));
                            }
                        }
                        
                        files.add(item);
                    }
                } catch (Exception e) {
                    log.warn("读取文件信息失败: {}", path, e);
                }
            });
        }

        // 排序：文件夹在前，按名称排序
        directories.sort((a, b) -> {
            String nameA = (String) a.get("name");
            String nameB = (String) b.get("name");
            return nameA.compareToIgnoreCase(nameB);
        });
        
        files.sort((a, b) -> {
            String nameA = (String) a.get("name");
            String nameB = (String) b.get("name");
            return nameA.compareToIgnoreCase(nameB);
        });

        Path normalizedRoot = scopedRoot == null ? null : scopedRoot.toAbsolutePath().normalize();
        Path parent = dir.getParent();
        result.put("path", toClientBrowserPath(dir, scopedRoot));
        result.put("parent", parent != null && (normalizedRoot == null || !parent.equals(normalizedRoot))
            ? toClientBrowserPath(parent, scopedRoot)
            : null);
        result.put("directories", directories);
        result.put("files", files);
        
        return result;
    }

    /**
     * 创建文件夹
     */
    public void createDirectory(String folderPath) throws Exception {
        Path dir = resolvePath(folderPath);
        if (dir == null) {
            throw new IllegalArgumentException("路径无效: " + folderPath);
        }
        if (Files.exists(dir)) {
            throw new IllegalArgumentException("目录已存在: " + dir);
        }
        Files.createDirectories(dir);
    }

    public Map<String, Object> listFilesAndDirectories(String folderPath,
                                                       com.photoexhibition.entity.StorageProvider provider,
                                                       UserAccount user,
                                                       Path scopedRoot) throws Exception {
        if (isLocalBackedProvider(provider)) {
            return listFilesAndDirectories(folderPath, scopedRoot);
        }
        Path relative = toScopedRelativePath(folderPath, scopedRoot);
        return storageUploadService.listDirectory(provider, user, relative);
    }

    public void createDirectory(String folderPath,
                                com.photoexhibition.entity.StorageProvider provider,
                                UserAccount user,
                                Path scopedRoot) throws Exception {
        if (isLocalBackedProvider(provider)) {
            createDirectory(resolveScopedLocalPath(folderPath, scopedRoot).toString());
            return;
        }
        Path relative = toScopedRelativePath(folderPath, scopedRoot);
        storageUploadService.createDirectory(provider, user, relative);
    }

    /**
     * 重命名文件夹或文件
     */
    public void renameItem(String sourcePath, String newName) throws Exception {
        Path source = resolvePath(sourcePath);
        if (source == null || !Files.exists(source)) {
            throw new IllegalArgumentException("源路径不存在: " + toRelativePath(sourcePath));
        }
        
        Path target = source.getParent().resolve(newName);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("目标名称已存在: " + newName);
        }
        
        Files.move(source, target);
        
        // 如果是目录，更新数据库中的路径
        if (Files.isDirectory(target)) {
            // 标准化路径前缀
            String oldPrefix = source.toString().replace("\\", "/");
            Path oldRoot = source.toAbsolutePath().normalize();
            Path newRoot = target.toAbsolutePath().normalize();

            // 更新相册路径
            List<Album> allAlbums = findAlbumsInSameScope(source, oldPrefix);
            List<Album> albumsToUpdate = new ArrayList<>();
            for (Album a : allAlbums) {
                if (a.getPath() == null || a.getPath().isBlank()) {
                    continue;
                }
                String rewrittenPath = rewriteStoredPathForDirectoryMove(a.getPath(), oldRoot, newRoot, a.getUserId());
                if (!java.util.Objects.equals(rewrittenPath, a.getPath())) {
                    a.setPath(rewrittenPath);
                    a.setPathHash(computePathHash(rewrittenPath));
                    albumsToUpdate.add(a);
                }
            }
            albumRepository.saveAll(albumsToUpdate);

            // 更新照片路径
            List<Photo> allPhotos = findPhotosInSameScope(source, oldPrefix);
            List<Photo> photosToUpdate = new ArrayList<>();
            for (Photo p : allPhotos) {
                String rewrittenOriginalPath = rewriteStoredPathForDirectoryMove(p.getOriginalPath(), oldRoot, newRoot, p.getUserId());
                String rewrittenThumbnailPath = rewriteStoredPathForDirectoryMove(p.getThumbnailPath(), oldRoot, newRoot, p.getUserId());
                String rewrittenWebpPath = rewriteStoredPathForDirectoryMove(p.getWebpPath(), oldRoot, newRoot, p.getUserId());
                String rewrittenSmallThumbPath = rewriteStoredPathForDirectoryMove(p.getSmallThumbPath(), oldRoot, newRoot, p.getUserId());
                String rewrittenMediumThumbPath = rewriteStoredPathForDirectoryMove(p.getMediumThumbPath(), oldRoot, newRoot, p.getUserId());
                String rewrittenLargeThumbPath = rewriteStoredPathForDirectoryMove(p.getLargeThumbPath(), oldRoot, newRoot, p.getUserId());
                String rewrittenBackgroundRemovedPath = rewriteStoredPathForDirectoryMove(p.getBackgroundRemovedPath(), oldRoot, newRoot, p.getUserId());

                if (!java.util.Objects.equals(rewrittenOriginalPath, p.getOriginalPath()) ||
                    !java.util.Objects.equals(rewrittenThumbnailPath, p.getThumbnailPath()) ||
                    !java.util.Objects.equals(rewrittenWebpPath, p.getWebpPath()) ||
                    !java.util.Objects.equals(rewrittenSmallThumbPath, p.getSmallThumbPath()) ||
                    !java.util.Objects.equals(rewrittenMediumThumbPath, p.getMediumThumbPath()) ||
                    !java.util.Objects.equals(rewrittenLargeThumbPath, p.getLargeThumbPath()) ||
                    !java.util.Objects.equals(rewrittenBackgroundRemovedPath, p.getBackgroundRemovedPath())) {
                    p.setOriginalPath(rewrittenOriginalPath);
                    p.setPathHash(computePathHash(rewrittenOriginalPath));
                    p.setThumbnailPath(rewrittenThumbnailPath);
                    p.setWebpPath(rewrittenWebpPath);
                    p.setSmallThumbPath(rewrittenSmallThumbPath);
                    p.setMediumThumbPath(rewrittenMediumThumbPath);
                    p.setLargeThumbPath(rewrittenLargeThumbPath);
                    p.setBackgroundRemovedPath(rewrittenBackgroundRemovedPath);
                    photosToUpdate.add(p);
                }
            }
            photoRepository.saveAll(photosToUpdate);
        }
    }

    public void renameItem(String sourcePath,
                           String newName,
                           com.photoexhibition.entity.StorageProvider provider,
                           UserAccount user,
                           Path scopedRoot) throws Exception {
        if (isLocalBackedProvider(provider)) {
            renameItem(resolveScopedLocalPath(sourcePath, scopedRoot).toString(), newName);
            return;
        }
        Path sourceRelative = toScopedRelativePath(sourcePath, scopedRoot);
        Path parentRelative = sourceRelative.getParent() == null ? Path.of("") : sourceRelative.getParent();
        Path targetRelative = parentRelative.resolve(newName).normalize();
        if (startsWithParentTraversal(targetRelative)) {
            throw new IllegalArgumentException("目标名称非法");
        }
        storageUploadService.movePath(provider, user, sourceRelative, targetRelative);
    }

    /**
     * 批量移动文件或文件夹
     */
    @Transactional
    public void moveItems(List<String> paths, String targetDir) throws Exception {
        if (paths == null || paths.isEmpty()) return;
        Path target = Paths.get(targetDir).toAbsolutePath().normalize();
        Files.createDirectories(target);
        for (String p : paths) {
            Path src = Paths.get(p).toAbsolutePath().normalize();
            if (Files.isDirectory(src)) {
                Path dst = target.resolve(src.getFileName());
                moveFolder(src.toString(), dst.toString());
            } else {
                moveSingleFile(src, target.resolve(src.getFileName()));
            }
        }
    }

    public void moveItems(List<String> paths,
                          String targetDir,
                          com.photoexhibition.entity.StorageProvider provider,
                          UserAccount user,
                          Path scopedRoot) throws Exception {
        if (isLocalBackedProvider(provider)) {
            moveItems(
                paths.stream().map(path -> resolveScopedLocalPath(path, scopedRoot).toString()).collect(Collectors.toList()),
                resolveScopedLocalPath(targetDir, scopedRoot).toString()
            );
            return;
        }
        if (paths == null || paths.isEmpty()) return;
        Path targetRelative = toScopedRelativePath(targetDir, scopedRoot);
        for (String path : paths) {
            Path sourceRelative = toScopedRelativePath(path, scopedRoot);
            Path fileName = sourceRelative.getFileName();
            if (fileName == null) {
                throw new IllegalArgumentException("源路径非法: " + path);
            }
            storageUploadService.movePath(provider, user, sourceRelative, targetRelative.resolve(fileName).normalize());
        }
    }

    /**
     * 批量删除文件或文件夹
     */
    @Transactional
    public void deleteItems(List<String> paths) throws Exception {
        if (paths == null || paths.isEmpty()) return;
        for (String p : paths) {
            Path path = Paths.get(p).toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                deleteFolder(path.toString());
            } else {
                deleteFileWithDb(path);
            }
        }
    }

    public void deleteItems(List<String> paths,
                            com.photoexhibition.entity.StorageProvider provider,
                            UserAccount user,
                            Path scopedRoot) throws Exception {
        if (isLocalBackedProvider(provider)) {
            deleteItems(paths.stream().map(path -> resolveScopedLocalPath(path, scopedRoot).toString()).collect(Collectors.toList()));
            return;
        }
        if (paths == null || paths.isEmpty()) return;
        for (String path : paths) {
            storageUploadService.deletePath(provider, user, toScopedRelativePath(path, scopedRoot));
        }
    }

    public String resolvePreviewUrl(String filePath,
                                    com.photoexhibition.entity.StorageProvider provider,
                                    UserAccount user,
                                    Path scopedRoot) throws Exception {
        if (isLocalBackedProvider(provider)) {
            throw new IllegalArgumentException("本地存储无需通过远端预览接口打开");
        }
        Path relative = toScopedRelativePath(filePath, scopedRoot);
        return storageUploadService.resolvePreviewUrl(provider, user, relative);
    }

    private boolean isLocalBackedProvider(com.photoexhibition.entity.StorageProvider provider) {
        return provider != null && (provider.getType() == com.photoexhibition.entity.StorageType.LOCAL
            || provider.getType() == com.photoexhibition.entity.StorageType.SFTP
            || provider.getType() == com.photoexhibition.entity.StorageType.SMB
            || provider.getType() == com.photoexhibition.entity.StorageType.NFS);
    }

    public StorageUploadService.DownloadedFile downloadPreviewFile(String filePath,
                                                                   com.photoexhibition.entity.StorageProvider provider,
                                                                   UserAccount user,
                                                                   Path scopedRoot) throws Exception {
        Path relative = toScopedRelativePath(filePath, scopedRoot);
        return storageUploadService.downloadFile(provider, user, relative);
    }

    /**
     * 将相对路径转换为项目根目录下的绝对路径，若已是绝对路径则直接返回
     */
    private Path resolvePath(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) return null;
        if (userPathService.isStoragePathReference(pathStr)) {
            try {
                return userPathService.resolveStoredPhotoPath(pathStr);
            } catch (Exception e) {
                throw new IllegalArgumentException("无法解析存储路径: " + pathStr, e);
            }
        }
        Path path = Paths.get(pathStr);
        if (!path.isAbsolute()) {
            String clean = pathStr.startsWith("./") ? pathStr.substring(2) : pathStr;
            path = resolveBasePath().resolve(clean).toAbsolutePath().normalize();
        } else {
            path = path.toAbsolutePath().normalize();
        }
        return path;
    }

    private void deleteDirectoryRecursive(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
            .sorted((a, b) -> b.compareTo(a)) // 先删除子文件
            .forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception e) {
                    log.warn("删除文件失败: {}", p, e);
                }
            });
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

    private Path toScopedRelativePath(String absolutePath, Path scopedRoot) {
        if (scopedRoot == null) {
            return Path.of("");
        }
        Path requested = Path.of(absolutePath).toAbsolutePath().normalize();
        Path normalizedRoot = scopedRoot.toAbsolutePath().normalize();
        if (!requested.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("路径超出当前用户可操作范围");
        }
        return normalizedRoot.relativize(requested);
    }

    private String normalizeBrowserPath(String path, Path scopedRoot) {
        if (path == null || path.isBlank()) {
            return "";
        }
        try {
            return toClientBrowserPath(resolveScopedLocalPath(path, scopedRoot), scopedRoot);
        } catch (Exception ignored) {
            Path candidate = Path.of(path.trim());
            if (candidate.isAbsolute()) {
                return toClientBrowserPath(candidate, scopedRoot);
            }
            String normalized = path.replace("\\", "/");
            return normalized.startsWith("/") ? normalized : "/" + normalized;
        }
    }

    private String toClientBrowserPath(Path path, Path scopedRoot) {
        if (path == null) {
            return null;
        }
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (scopedRoot != null) {
            Path normalizedRoot = scopedRoot.toAbsolutePath().normalize();
            if (normalizedPath.startsWith(normalizedRoot)) {
                Path relative = normalizedRoot.relativize(normalizedPath);
                String normalized = relative.toString().replace("\\", "/");
                return normalized.isEmpty() ? "/" : "/" + normalized;
            }
        }
        return toRelativePath(normalizedPath.toString());
    }

    private Path resolveScopedLocalPath(String requestedPath, Path scopedRoot) {
        if (scopedRoot == null) {
            return resolvePath(requestedPath);
        }
        if (requestedPath == null || requestedPath.isBlank()) {
            return scopedRoot.toAbsolutePath().normalize();
        }
        Path candidate = Path.of(requestedPath.trim());
        if (!candidate.isAbsolute()) {
            String clean = requestedPath.startsWith("./") ? requestedPath.substring(2) : requestedPath;
            Path relative = Path.of(clean).normalize();
            Long scopedUserId = userPathService.extractUserIdFromPath(scopedRoot.toAbsolutePath().normalize().toString());
            if (scopedUserId != null) {
                relative = userPathService.stripLeadingUserSegment(relative, scopedUserId);
            }
            candidate = scopedRoot.resolve(relative);
        }
        candidate = candidate.toAbsolutePath().normalize();
        Path normalizedRoot = scopedRoot.toAbsolutePath().normalize();
        if (!candidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("路径超出当前用户可操作范围");
        }
        return candidate;
    }

    private List<Album> findAlbumsInSameScope(Path scopedPath) {
        return findAlbumsInSameScope(scopedPath, null);
    }

    private List<Album> findAlbumsInSameScope(Path scopedPath, String normalizedPathPrefix) {
        Long userId = extractScopedUserId(scopedPath);
        if (normalizedPathPrefix != null && !normalizedPathPrefix.isBlank()) {
            LinkedHashSet<Album> matched = new LinkedHashSet<>();
            for (String candidatePrefix : buildManagedPathCandidates(normalizedPathPrefix, userId)) {
                if (userId == null) {
                    matched.addAll(albumRepository.findByPathStartingWithNormalized(candidatePrefix));
                } else {
                    matched.addAll(albumRepository.findByUserIdAndPathStartingWithNormalized(userId, candidatePrefix));
                }
            }
            return new ArrayList<>(matched);
        }
        return loadAlbumsByScopedUserId(userId);
    }

    private List<Photo> findPhotosInSameScope(Path scopedPath) {
        return findPhotosInSameScope(scopedPath, null);
    }

    private List<Photo> findPhotosInSameScope(Path scopedPath, String normalizedPathPrefix) {
        Long userId = extractScopedUserId(scopedPath);
        if (normalizedPathPrefix != null && !normalizedPathPrefix.isBlank()) {
            LinkedHashSet<Photo> matched = new LinkedHashSet<>();
            for (String candidatePrefix : buildManagedPathCandidates(normalizedPathPrefix, userId)) {
                if (userId == null) {
                    matched.addAll(photoRepository.findByOriginalPathStartingWith(candidatePrefix));
                } else {
                    matched.addAll(photoRepository.findByUserIdAndOriginalPathStartingWith(userId, candidatePrefix));
                }
            }
            return new ArrayList<>(matched);
        }
        return loadPhotosByScopedUserId(userId);
    }

    private LinkedHashSet<String> buildManagedPathCandidates(String normalizedPath, Long userId) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (normalizedPath == null || normalizedPath.isBlank()) {
            return candidates;
        }
        String clean = normalizedPath.replace("\\", "/");
        candidates.add(clean);
        userPathService.tryBuildStoragePathReference(clean, userId)
            .ifPresent(reference -> candidates.add(reference.replace("\\", "/")));
        return candidates;
    }

    private List<Album> loadAlbumsByScopedUserId(Long userId) {
        List<Album> albums = new ArrayList<>();
        int pageNumber = 0;
        org.springframework.data.domain.Page<Album> page;
        do {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(pageNumber, 200);
            page = userId == null ? albumRepository.findAll(pageable) : albumRepository.findByUserId(userId, pageable);
            albums.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return albums;
    }

    private List<Photo> loadPhotosByScopedUserId(Long userId) {
        List<Photo> photos = new ArrayList<>();
        int pageNumber = 0;
        org.springframework.data.domain.Page<Photo> page;
        do {
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(pageNumber, 200);
            page = userId == null ? photoRepository.findAll(pageable) : photoRepository.findByUserId(userId, pageable);
            photos.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return photos;
    }

    private Long extractScopedUserId(Path path) {
        if (path == null) {
            return null;
        }
        return userPathService.extractUserIdFromPath(path.toAbsolutePath().normalize().toString());
    }

    /**
     * 将Photo转换为Map（用于API返回）
     */
    private Map<String, Object> convertPhotoToMap(Photo photo) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", photo.getId());
        map.put("originalPath", toRelativePath(photo.getOriginalPath()));
        map.put("thumbnailPath", toRelativePath(photo.getThumbnailPath()));
        map.put("webpPath", toRelativePath(photo.getWebpPath()));
        map.put("width", photo.getWidth());
        map.put("height", photo.getHeight());
        return map;
    }

    /**
     * 上传文件/文件夹（仅保存文件到磁盘，处理延迟到上传全部完成后执行）。
     * 处理采用 3 秒防抖：连续多批上传时，计时器不断重置，
     * 直到最后一批上传后 3 秒才统一执行图片处理（缩略图/EXIF/人脸等）。
     */
    public UploadResult uploadFiles(List<MultipartFile> files, String targetDir, List<String> relativePaths, UserAccount user, Long storageProviderId) throws Exception {
        if (files == null || files.isEmpty()) {
            return UploadResult.builder()
                .saved(0)
                .scanQueued(false)
                .scanMessage("没有需要上传的文件")
                .build();
        }
        var uploadProvider = storageProviderService.resolveUploadProvider(user, storageProviderId);
        var browserContext = storageProviderService.resolveBrowserStorage(user, uploadProvider.getId());
        var capabilitySummary = storageProviderService.describeProviderCapabilities(uploadProvider, user);
        Path targetRelativeDirectory = resolveUploadTargetRelativeDirectory(targetDir, user, uploadProvider.getId());
        Path localTarget = uploadProvider.getType() == com.photoexhibition.entity.StorageType.LOCAL
            ? storageUploadService.resolveLocalDirectoryPath(uploadProvider, user, targetRelativeDirectory)
            : null;
        Path providerScanRoot = browserContext.getScopedRoot().resolve(targetRelativeDirectory).normalize();
        if (localTarget != null) {
            Files.createDirectories(localTarget);
        }
        List<Path> destinations = new ArrayList<>();
        List<Long> deltas = new ArrayList<>();
        long totalDelta = 0L;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile mf = files.get(i);
            String rel = (relativePaths != null && relativePaths.size() > i) ? relativePaths.get(i) : mf.getOriginalFilename();
            if (rel == null || rel.isEmpty()) continue;
            Path relativeDestination = targetRelativeDirectory.resolve(rel).normalize();
            if (relativeDestination.isAbsolute() || startsWithParentTraversal(relativeDestination)) {
                throw new IllegalArgumentException("上传目标路径非法: " + rel);
            }
            long existingSize = storageUploadService.resolveExistingSize(uploadProvider, user, relativeDestination);
            long delta = mf.getSize() - existingSize;
            destinations.add(relativeDestination);
            deltas.add(delta);
            totalDelta += delta;
        }

        userStorageService.ensureQuotaAvailable(user, totalDelta);

        int saved = 0;
        long appliedDelta = 0L;
        try {
            for (int i = 0; i < files.size(); i++) {
                MultipartFile mf = files.get(i);
                String rel = (relativePaths != null && relativePaths.size() > i) ? relativePaths.get(i) : mf.getOriginalFilename();
                if (rel == null || rel.isEmpty()) continue;
                Path dest = destinations.get(saved);
                storageUploadService.storeFile(uploadProvider, user, dest, mf);
                appliedDelta += deltas.get(saved);
                saved++;
            }
        } catch (Exception e) {
            if (user != null && appliedDelta != 0L) {
                userStorageService.adjustStorageUsage(user.getId(), appliedDelta);
            }
            throw e;
        }

        if (user != null && appliedDelta != 0L) {
            userStorageService.adjustStorageUsage(user.getId(), appliedDelta);
        }

        boolean scanSupported = Boolean.TRUE.equals((Boolean) capabilitySummary.get("scanSupported"));
        String supportMessage = capabilitySummary.get("supportMessage") == null ? null : String.valueOf(capabilitySummary.get("supportMessage"));
        boolean scanQueued = false;
        String scanMessage;
        if (scanSupported) {
            scheduleDeferredProcessing(user, localTarget != null ? localTarget : providerScanRoot, uploadProvider.getId());
            scanQueued = true;
            scanMessage = "上传完成，已加入扫描队列";
        } else {
            scanMessage = supportMessage == null || supportMessage.isBlank()
                ? "上传完成，但当前存储暂不支持自动扫描，请稍后手动触发或迁移到可扫描存储"
                : "上传完成，但未加入自动扫描：" + supportMessage;
        }

        return UploadResult.builder()
            .saved(saved)
            .scanQueued(scanQueued)
            .scanMessage(scanMessage)
            .storageProviderId(uploadProvider.getId())
            .storageProviderName(uploadProvider.getName())
            .storageProviderType(uploadProvider.getType() == null ? null : uploadProvider.getType().name())
            .build();
    }

    private Path resolveUploadTargetRelativeDirectory(String targetDir, UserAccount user, Long storageProviderId) {
        StorageProviderService.BrowserStorageContext browserContext = storageProviderService.resolveBrowserStorage(user, storageProviderId);
        Path scopedRoot = browserContext.getScopedRoot();
        if (targetDir == null || targetDir.isBlank()) {
            return Path.of("");
        }
        Path requested = Path.of(targetDir.trim());
        if (!requested.isAbsolute()) {
            String clean = targetDir.startsWith("./") ? targetDir.substring(2) : targetDir;
            Path relative = Path.of(clean).normalize();
            Long scopedUserId = user != null ? user.getId() : userPathService.extractUserIdFromPath(scopedRoot.toAbsolutePath().normalize().toString());
            if (scopedUserId != null) {
                relative = userPathService.stripLeadingUserSegment(relative, scopedUserId);
            }
            requested = scopedRoot.resolve(relative);
        }
        requested = requested.toAbsolutePath().normalize();
        if (!requested.startsWith(scopedRoot)) {
            throw new IllegalArgumentException("路径超出当前用户可上传范围");
        }
        return scopedRoot.relativize(requested);
    }

    private boolean startsWithParentTraversal(Path path) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 防抖调度：上一批上传后 3 秒内若无新批次，则开始后台处理。
     */
    private void scheduleDeferredProcessing(UserAccount user, Path baseTarget, Long storageProviderId) {
        pendingUploadScans.add(new UploadScanRequest(user, baseTarget, storageProviderId));
        if (uploadProcessFuture != null && !uploadProcessFuture.isDone()) {
            uploadProcessFuture.cancel(false);
        }
        uploadProcessFuture = uploadProcessExecutor.schedule(this::processQueuedUploads, 3, TimeUnit.SECONDS);
    }

    /**
     * 后台线程：逐个处理待处理的上传文件，每个文件单独事务。
     */
    private void processQueuedUploads() {
        Map<String, UploadScanRequest> toProcess = new LinkedHashMap<>();
        UploadScanRequest request;
        while ((request = pendingUploadScans.poll()) != null) {
            Long userId = request.user != null ? request.user.getId() : null;
            String key = (userId != null ? userId : "global")
                + ":"
                + (request.storageProviderId != null ? request.storageProviderId : "default")
                + ":"
                + request.rootPath.toAbsolutePath().normalize();
            toProcess.put(key, request);
        }
        if (toProcess.isEmpty()) return;

        log.info("开始为 {} 个上传目录创建扫描任务...", toProcess.size());
        int processed = 0;
        for (UploadScanRequest upload : toProcess.values()) {
            try {
                scanTaskService.enqueueUploadScan(upload.user, upload.rootPath.toString(), upload.storageProviderId);
                processed++;
            } catch (Exception e) {
                log.warn("创建上传扫描任务失败: {}", upload.rootPath, e);
            }
        }
        log.info("上传扫描任务创建完成: {} / {} 个目录", processed, toProcess.size());
    }

    private void moveSingleFile(Path source, Path target) throws Exception {
        if (!Files.exists(source) || Files.isDirectory(source)) return;
        Files.createDirectories(target.getParent());
        Files.move(source, target);
        syncFileMove(source, target);
    }

    private void deleteFileWithDb(Path filePath) throws Exception {
        String abs = filePath.toAbsolutePath().normalize().toString();
        if (Files.exists(filePath)) {
            Files.deleteIfExists(filePath);
        }
        if (isUnderBase(abs)) {
            findPhotoByPath(filePath).ifPresent(photo -> {
                Long albumId = photo.getAlbumId();
                photoRepository.delete(photo);
                updateAlbumCount(albumId);
            });
        }
    }

    private void syncFileCreate(Path filePath) {
        String abs = filePath.toAbsolutePath().normalize().toString();
        if (!isUnderBase(abs) || Files.isDirectory(filePath)) return;
        try {
            Path albumPath = filePath.getParent();
            Album album = findOrCreateAlbum(albumPath);
            photoScanService.processPhotoFile(filePath.toFile(), album, false);
            updateAlbumCount(album.getId());
        } catch (Exception e) {
            log.warn("同步创建文件失败: {}", filePath, e);
        }
    }

    private void syncFileMove(Path source, Path target) {
        String oldAbs = source.toAbsolutePath().normalize().toString();
        String newAbs = target.toAbsolutePath().normalize().toString();
        if (!isUnderBase(oldAbs) && !isUnderBase(newAbs)) return;
        findPhotoByPath(source).ifPresent(photo -> {
            Long oldAlbumId = photo.getAlbumId();
            Path newAlbumPath = target.getParent();
            Album newAlbum = findOrCreateAlbum(newAlbumPath);
            photo.setAlbumId(newAlbum.getId());
            photo.setOriginalPath(resolveStoredPathForDb(target, photo.getUserId()));
            if (photo.getThumbnailPath() != null) {
                photo.setThumbnailPath(resolveSiblingStoredPath(photo.getThumbnailPath(), source, target));
            }
            if (photo.getWebpPath() != null) {
                photo.setWebpPath(resolveSiblingStoredPath(photo.getWebpPath(), source, target));
            }
            photoRepository.save(photo);
            updateAlbumCount(oldAlbumId);
            updateAlbumCount(newAlbum.getId());
        });
    }

    private Album findOrCreateAlbum(Path albumPath) {
        String absolutePath = albumPath.toAbsolutePath().normalize().toString();
        Long userId = userPathService.extractUserIdFromPath(absolutePath);
        String storedPath = resolveStoredPathForDb(albumPath, userId);
        return findAlbumByDirectoryPath(albumPath)
            .orElseGet(() -> {
            Album a = new Album();
            a.setName(albumPath.getFileName().toString());
            a.setPath(storedPath);
            a.setPathHash(computePathHash(storedPath));
            a.setUserId(userId);
            a.setPhotoCount(0);
            return albumRepository.save(a);
        });
    }

    private boolean isUnderBase(String absPath) {
        try {
            Path normalized = Paths.get(absPath).toAbsolutePath().normalize();
            if (normalized.startsWith(resolveBasePath())) {
                return true;
            }
            Long userId = userPathService.extractUserIdFromPath(normalized.toString());
            return userPathService.tryBuildStoragePathReference(normalized.toString(), userId).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    private void updateAlbumCount(Long albumId) {
        if (albumId == null) return;
        albumRepository.findById(albumId).ifPresent(a -> {
            int count = photoRepository.countByAlbumId(albumId).intValue();
            a.setPhotoCount(count);
            albumRepository.save(a);
        });
    }

    private Optional<Photo> findPhotoByPath(Path path) {
        if (path == null) {
            return Optional.empty();
        }
        for (String candidate : buildPathLookupCandidates(path)) {
            try {
                Optional<Photo> matched = photoRepository.findByOriginalPath(candidate);
                if (matched.isPresent()) {
                    return matched;
                }
            } catch (Exception ignored) {
                List<Photo> photos = photoRepository.findAllByOriginalPath(candidate);
                if (!photos.isEmpty()) {
                    return photos.stream().max(java.util.Comparator.comparingLong(Photo::getId));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Album> findAlbumByDirectoryPath(Path path) {
        if (path == null) {
            return Optional.empty();
        }
        for (String candidate : buildPathLookupCandidates(path)) {
            Optional<Album> matched = albumRepository.findByPath(candidate);
            if (matched.isPresent()) {
                return matched;
            }
            String pathHash = computePathHash(candidate);
            if (pathHash != null) {
                matched = albumRepository.findByPathHash(pathHash);
                if (matched.isPresent()) {
                    return matched;
                }
            }
        }
        return Optional.empty();
    }

    private List<String> buildPathLookupCandidates(Path path) {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        String absolute = path.toAbsolutePath().normalize().toString();
        candidates.add(absolute);
        Long userId = userPathService.extractUserIdFromPath(absolute);
        userPathService.tryBuildStoragePathReference(absolute, userId).ifPresent(candidates::add);
        return new ArrayList<>(candidates);
    }

    private String resolveStoredPathForDb(Path path, Long userId) {
        String absolute = path.toAbsolutePath().normalize().toString();
        return userPathService.tryBuildStoragePathReference(absolute, userId).orElse(absolute);
    }

    private String resolveSiblingStoredPath(String currentStoredPath, Path oldPath, Path newPath) {
        if (currentStoredPath == null || currentStoredPath.isBlank()) {
            return currentStoredPath;
        }
        try {
            Path resolvedCurrent = userPathService.resolveStoredPhotoPath(currentStoredPath);
            Path oldAbsolute = oldPath.toAbsolutePath().normalize();
            if (!resolvedCurrent.startsWith(oldAbsolute.getParent())) {
                return currentStoredPath;
            }
            Path relative = oldAbsolute.getParent().relativize(resolvedCurrent);
            Path newAbsolute = newPath.toAbsolutePath().normalize().getParent().resolve(relative).normalize();
            Long userId = userPathService.parseStoragePathReference(currentStoredPath) != null
                ? userPathService.parseStoragePathReference(currentStoredPath).getUserId()
                : userPathService.extractUserIdFromPath(newAbsolute.toString());
            return userPathService.tryBuildStoragePathReference(newAbsolute.toString(), userId).orElse(newAbsolute.toString());
        } catch (Exception e) {
            String oldAbs = oldPath.toAbsolutePath().normalize().toString();
            String newAbs = newPath.toAbsolutePath().normalize().toString();
            return replacePrefix(currentStoredPath, oldAbs, newAbs);
        }
    }

    private String rewriteStoredPathForDirectoryMove(String currentPath, Path oldRoot, Path newRoot, Long userId) {
        if (currentPath == null || currentPath.isBlank() || oldRoot == null || newRoot == null) {
            return currentPath;
        }
        try {
            Path currentAbsolute = userPathService.resolveStoredPhotoPath(currentPath);
            if (!currentAbsolute.startsWith(oldRoot)) {
                return currentPath;
            }
            Path relative = oldRoot.relativize(currentAbsolute);
            Path targetAbsolute = newRoot.resolve(relative).normalize();
            return userPathService.tryBuildStoragePathReference(targetAbsolute.toString(), userId).orElse(targetAbsolute.toString());
        } catch (Exception e) {
            return currentPath;
        }
    }

    private String computePathHash(String path) {
        if (path == null) {
            return null;
        }
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(path.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将绝对路径转换为以资源映射可访问的相对路径
     */
    private String toRelativePath(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return null;
        }
        String displayPath = userPathService.toDisplayPath(absolutePath, true);
        if (!absolutePath.equals(displayPath)) {
            return displayPath;
        }
        String normalized = absolutePath.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private Path resolveBasePath() {
        return userPathService.resolvePhotoBasePath();
    }
}
