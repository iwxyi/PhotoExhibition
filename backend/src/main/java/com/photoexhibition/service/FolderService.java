package com.photoexhibition.service;

import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.service.PhotoScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final PlatformTransactionManager transactionManager;
    @Value("${photo.scan.base-path}")
    private String basePath;

    private final ScheduledExecutorService uploadProcessExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentLinkedQueue<Path> pendingUploadPaths = new ConcurrentLinkedQueue<>();
    private volatile ScheduledFuture<?> uploadProcessFuture;

    /**
     * 移动/重命名文件夹，同时更新数据库中的相册/照片路径
     */
    @Transactional
    public void moveFolder(String sourcePath, String targetPath) throws Exception {
        Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
        Path target = Paths.get(targetPath).toAbsolutePath().normalize();

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
        String newPrefix = target.toString().replace("\\", "/");

        // 更新相册路径（使用标准化路径匹配）
        List<Album> allAlbums = albumRepository.findAll();
        List<Album> albumsToUpdate = new ArrayList<>();
        for (Album a : allAlbums) {
            if (a.getPath() != null) {
                String normalizedPath = a.getPath().replace("\\", "/");
                if (normalizedPath.startsWith(oldPrefix)) {
                    a.setPath(replacePrefix(a.getPath(), oldPrefix, newPrefix));
                    albumsToUpdate.add(a);
                }
            }
        }
        albumRepository.saveAll(albumsToUpdate);

        // 更新照片路径
        List<Photo> allPhotos = photoRepository.findAll();
        List<Photo> photosToUpdate = new ArrayList<>();
        for (Photo p : allPhotos) {
            if (p.getOriginalPath() != null) {
                String normalizedPath = p.getOriginalPath().replace("\\", "/");
                if (normalizedPath.startsWith(oldPrefix)) {
                    p.setOriginalPath(replacePrefix(p.getOriginalPath(), oldPrefix, newPrefix));
                    p.setThumbnailPath(replacePrefix(p.getThumbnailPath(), oldPrefix, newPrefix));
                    p.setWebpPath(replacePrefix(p.getWebpPath(), oldPrefix, newPrefix));
                    p.setSmallThumbPath(replacePrefix(p.getSmallThumbPath(), oldPrefix, newPrefix));
                    p.setMediumThumbPath(replacePrefix(p.getMediumThumbPath(), oldPrefix, newPrefix));
                    p.setLargeThumbPath(replacePrefix(p.getLargeThumbPath(), oldPrefix, newPrefix));
                    p.setBackgroundRemovedPath(replacePrefix(p.getBackgroundRemovedPath(), oldPrefix, newPrefix));
                    photosToUpdate.add(p);
                }
            }
        }
        photoRepository.saveAll(photosToUpdate);
    }

    /**
     * 删除目录，并删除其下关联的相册/照片记录
     */
    @Transactional
    public void deleteFolder(String folderPath) throws Exception {
        Path dir = Paths.get(folderPath).toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            throw new IllegalArgumentException("目录不存在: " + dir);
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("路径不是目录: " + dir);
        }

        // 标准化路径前缀
        String prefix = dir.toString().replace("\\", "/");

        // 找到匹配的相册（使用标准化路径匹配）
        List<Album> allAlbums = albumRepository.findAll();
        List<Album> albumsToDelete = new ArrayList<>();
        for (Album a : allAlbums) {
            if (a.getPath() != null) {
                String normalizedPath = a.getPath().replace("\\", "/");
                if (normalizedPath.startsWith(prefix)) {
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
                .map(Path::toString)
                .collect(Collectors.toList());
        }
    }

    /**
     * 列出指定目录下的文件和文件夹（用于文件浏览器）
     */
    public Map<String, Object> listFilesAndDirectories(String folderPath) throws Exception {
        Path dir = resolvePath(folderPath);
        Map<String, Object> result = new HashMap<>();
        
        if (dir == null || !Files.exists(dir) || !Files.isDirectory(dir)) {
            result.put("path", folderPath);
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
                    item.put("path", path.toString());
                    item.put("isDirectory", isDir);
                    
                    if (isDir) {
                        // 查找对应的相册，获取封面图片信息
                        Optional<Album> albumOpt = albumRepository.findByPath(path.toString());
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
                            Optional<Photo> photoOpt = photoRepository.findByOriginalPath(path.toString());
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

        result.put("path", dir.toString());
        result.put("parent", dir.getParent() != null ? dir.getParent().toString() : null);
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

    /**
     * 重命名文件夹或文件
     */
    public void renameItem(String sourcePath, String newName) throws Exception {
        Path source = resolvePath(sourcePath);
        if (source == null || !Files.exists(source)) {
            throw new IllegalArgumentException("源路径不存在: " + sourcePath);
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
            String newPrefix = target.toString().replace("\\", "/");

            // 更新相册路径
            List<Album> allAlbums = albumRepository.findAll();
            List<Album> albumsToUpdate = new ArrayList<>();
            for (Album a : allAlbums) {
                if (a.getPath() != null) {
                    String normalizedPath = a.getPath().replace("\\", "/");
                    if (normalizedPath.startsWith(oldPrefix)) {
                        a.setPath(replacePrefix(a.getPath(), oldPrefix, newPrefix));
                        albumsToUpdate.add(a);
                    }
                }
            }
            albumRepository.saveAll(albumsToUpdate);

            // 更新照片路径
            List<Photo> allPhotos = photoRepository.findAll();
            List<Photo> photosToUpdate = new ArrayList<>();
            for (Photo p : allPhotos) {
                if (p.getOriginalPath() != null) {
                    String normalizedPath = p.getOriginalPath().replace("\\", "/");
                    if (normalizedPath.startsWith(oldPrefix)) {
                        p.setOriginalPath(replacePrefix(p.getOriginalPath(), oldPrefix, newPrefix));
                        p.setThumbnailPath(replacePrefix(p.getThumbnailPath(), oldPrefix, newPrefix));
                        p.setWebpPath(replacePrefix(p.getWebpPath(), oldPrefix, newPrefix));
                        p.setSmallThumbPath(replacePrefix(p.getSmallThumbPath(), oldPrefix, newPrefix));
                        p.setMediumThumbPath(replacePrefix(p.getMediumThumbPath(), oldPrefix, newPrefix));
                        p.setLargeThumbPath(replacePrefix(p.getLargeThumbPath(), oldPrefix, newPrefix));
                        p.setBackgroundRemovedPath(replacePrefix(p.getBackgroundRemovedPath(), oldPrefix, newPrefix));
                        photosToUpdate.add(p);
                    }
                }
            }
            photoRepository.saveAll(photosToUpdate);
        }
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

    /**
     * 将相对路径转换为项目根目录下的绝对路径，若已是绝对路径则直接返回
     */
    private Path resolvePath(String pathStr) {
        if (pathStr == null || pathStr.isEmpty()) return null;
        Path path = Paths.get(pathStr);
        if (!path.isAbsolute()) {
            String projectRoot = System.getProperty("user.dir");
            if (projectRoot.endsWith("backend")) {
                projectRoot = Paths.get(projectRoot).getParent().toString();
            }
            String clean = pathStr.startsWith("./") ? pathStr.substring(2) : pathStr;
            path = Paths.get(projectRoot, clean).toAbsolutePath().normalize();
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
    public int uploadFiles(List<MultipartFile> files, String targetDir, List<String> relativePaths) throws Exception {
        if (files == null || files.isEmpty()) return 0;
        Path baseTarget = resolvePath(targetDir);
        Files.createDirectories(baseTarget);
        int saved = 0;
        for (int i = 0; i < files.size(); i++) {
            MultipartFile mf = files.get(i);
            String rel = (relativePaths != null && relativePaths.size() > i) ? relativePaths.get(i) : mf.getOriginalFilename();
            if (rel == null || rel.isEmpty()) continue;
            Path dest = baseTarget.resolve(rel).normalize();
            Files.createDirectories(dest.getParent());
            mf.transferTo(dest.toFile());
            pendingUploadPaths.add(dest);
            saved++;
        }
        scheduleDeferredProcessing();
        return saved;
    }

    /**
     * 防抖调度：上一批上传后 3 秒内若无新批次，则开始后台处理。
     */
    private void scheduleDeferredProcessing() {
        if (uploadProcessFuture != null && !uploadProcessFuture.isDone()) {
            uploadProcessFuture.cancel(false);
        }
        uploadProcessFuture = uploadProcessExecutor.schedule(this::processQueuedUploads, 3, TimeUnit.SECONDS);
    }

    /**
     * 后台线程：逐个处理待处理的上传文件，每个文件单独事务。
     */
    private void processQueuedUploads() {
        List<Path> toProcess = new ArrayList<>();
        Path p;
        while ((p = pendingUploadPaths.poll()) != null) {
            toProcess.add(p);
        }
        if (toProcess.isEmpty()) return;

        log.info("开始后台处理 {} 个上传文件...", toProcess.size());
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        int processed = 0;
        for (Path path : toProcess) {
            try {
                txTemplate.executeWithoutResult(status -> syncFileCreate(path));
                processed++;
            } catch (Exception e) {
                log.warn("处理上传文件失败: {}", path, e);
            }
        }
        log.info("后台处理完成: {} / {} 个文件", processed, toProcess.size());
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
            photoRepository.findByOriginalPath(abs).ifPresent(photo -> {
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
        photoRepository.findByOriginalPath(oldAbs).ifPresent(photo -> {
            Long oldAlbumId = photo.getAlbumId();
            Path newAlbumPath = target.getParent();
            Album newAlbum = findOrCreateAlbum(newAlbumPath);
            photo.setAlbumId(newAlbum.getId());
            photo.setOriginalPath(newAbs);
            if (photo.getThumbnailPath() != null) {
                photo.setThumbnailPath(replacePrefix(photo.getThumbnailPath(), oldAbs, newAbs));
            }
            if (photo.getWebpPath() != null) {
                photo.setWebpPath(replacePrefix(photo.getWebpPath(), oldAbs, newAbs));
            }
            photoRepository.save(photo);
            updateAlbumCount(oldAlbumId);
            updateAlbumCount(newAlbum.getId());
        });
    }

    private Album findOrCreateAlbum(Path albumPath) {
        String pathStr = albumPath.toAbsolutePath().normalize().toString();
        return albumRepository.findByPath(pathStr).orElseGet(() -> {
            Album a = new Album();
            a.setName(albumPath.getFileName().toString());
            a.setPath(pathStr);
            a.setPhotoCount(0);
            return albumRepository.save(a);
        });
    }

    private boolean isUnderBase(String absPath) {
        try {
            Path base = resolveBasePath();
            return Paths.get(absPath).normalize().startsWith(base);
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

    /**
     * 将绝对路径转换为以资源映射可访问的相对路径
     */
    private String toRelativePath(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return null;
        }
        try {
            Path base = resolveBasePath();
            Path target = Paths.get(absolutePath).normalize();
            if (target.startsWith(base)) {
                Path relative = base.relativize(target);
                String rel = relative.toString().replace("\\", "/");
                if (!rel.startsWith("/")) {
                    rel = "/" + rel;
                }
                return rel;
            }
        } catch (Exception e) {
            log.warn("无法转换为相对路径: {}", absolutePath, e);
        }
        return absolutePath;
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
        } else {
            base = base.toAbsolutePath().normalize();
        }
        return base;
    }
}

