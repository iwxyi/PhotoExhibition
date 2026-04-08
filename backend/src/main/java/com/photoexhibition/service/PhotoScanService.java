package com.photoexhibition.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.ProcessingStatus;
import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.entity.StorageType;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.StorageProviderRepository;
import com.photoexhibition.repository.TagRepository;
import com.photoexhibition.service.FilterOptionService;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.UnsatisfiedLinkError;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.function.Consumer;

@Slf4j
@Service
public class PhotoScanService {

    public enum ScanControlAction {
        CONTINUE,
        PAUSE,
        CANCEL
    }

    public static class ScanInterruptedException extends RuntimeException {
        private final ScanControlAction action;
        private final String path;

        public ScanInterruptedException(ScanControlAction action, String path) {
            super(action == ScanControlAction.PAUSE ? "扫描任务已暂停" : "扫描任务已取消");
            this.action = action;
            this.path = path;
        }

        public ScanControlAction getAction() {
            return action;
        }

        public String getPath() {
            return path;
        }
    }

    public interface ScanProgressListener {
        default void onScanPrepared(String rootPath, boolean force, int totalItems) {}

        default void onFileProcessed(String absolutePath, int current, int total) {}

        default void onPathProcessed(String absolutePath, String pathType, int current, int total) {
            onFileProcessed(absolutePath, current, total);
        }

        default void onFileSkipped(String absolutePath, String reason, String detail, int current, int total) {}

        default void onPathSkipped(String absolutePath, String pathType, String reason, String detail, int current, int total) {
            onFileSkipped(absolutePath, reason, detail, current, total);
        }

        default void onFileFailed(String absolutePath, String errorMessage, int current, int total) {}

        default void onPathFailed(String absolutePath, String pathType, String errorMessage, int current, int total) {
            onFileFailed(absolutePath, errorMessage, current, total);
        }

        default void onScanCompleted(int processed, int total, int skipped, int failed) {}

        default void onScanFailed(Exception exception, int processed, int total) {}

        default ScanControlAction getControlAction() {
            return ScanControlAction.CONTINUE;
        }

        default String getResumeFromPath() {
            return null;
        }

        default String getResumeFromType() {
            return null;
        }

        default int getInitialProcessedItems() {
            return 0;
        }
    }

    // 简单的任务状态记录结构（用于后台异步任务的进度与日志查询）
    private static class TaskStatus {
        public String taskId;
        public String taskName;
        public String status;
        public final List<String> logs = Collections.synchronizedList(new ArrayList<>());
        public int current = 0;
        public int total = 0;
        public boolean complete = false;
        public boolean stopped = false;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
    }

    // 跳过文件记录（扫描进度差异详情）
    public static class SkippedFileRecord {
        public int index;
        public Long userId;
        public String relativePath;
        public String reason;
        public String detail;
        public long fileSizeBytes;
        public LocalDateTime recordedAt;

        public SkippedFileRecord(int index, Long userId, String relativePath, String reason, String detail, long fileSizeBytes, LocalDateTime recordedAt) {
            this.index = index;
            this.userId = userId;
            this.relativePath = relativePath;
            this.reason = reason;
            this.detail = detail;
            this.fileSizeBytes = fileSizeBytes;
            this.recordedAt = recordedAt;
        }
    }

    private final List<SkippedFileRecord> skippedFileRecords = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger skippedFileIndex = new AtomicInteger(0);

    @Value("${photo.scan.base-path}")
    private String basePath;

    @Value("${photo.scan.supported-formats}")
    private String supportedFormats;

    @Value("${photo.scan.thumbnail-width}")
    private int thumbnailWidth;

    @Value("${photo.scan.thumbnail-height}")
    private int thumbnailHeight;

    @Value("${photo.scan.webp-quality}")
    private float webpQuality;

    // 三级缩略图配置
    @Value("${photo.scan.thumbnail.small.width}")
    private int smallThumbnailWidth;

    @Value("${photo.scan.thumbnail.small.height}")
    private int smallThumbnailHeight;

    @Value("${photo.scan.thumbnail.small.quality}")
    private float smallThumbnailQuality;

    @Value("${photo.scan.thumbnail.medium.width}")
    private int mediumThumbnailWidth;

    @Value("${photo.scan.thumbnail.medium.height}")
    private int mediumThumbnailHeight;

    @Value("${photo.scan.thumbnail.medium.quality}")
    private float mediumThumbnailQuality;

    @Value("${photo.scan.thumbnail.large.width}")
    private int largeThumbnailWidth;

    @Value("${photo.scan.thumbnail.large.height}")
    private int largeThumbnailHeight;

    @Value("${photo.scan.thumbnail.large.quality}")
    private float largeThumbnailQuality;

    @Value("${photo.scan.thumbnail.large.skip-if-no-benefit}")
    private boolean largeThumbnailSkipIfNoBenefit;

    // 扫描时是否自动进行背景移除
    @Value("${photo.scan.auto-background-removal:false}")
    private boolean autoBackgroundRemoval;

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final TagRepository tagRepository;
    private final TagService tagService;
    private final ColorAnalysisService colorAnalysisService;
    private final SubjectDetectionService subjectDetectionService;
    private final FaceService faceService;
    private final SmartTagService smartTagService;
    private final AlbumAtmosphereAnalysisService atmosphereAnalysisService;
    private final AtmosphereEffectsService atmosphereEffectsService;
    private final SystemConfigService systemConfigService;
    private final FilterOptionService filterOptionService;
    private final PhotoAIScoringService aiScoringService;
    private final BackgroundRemovalService backgroundRemovalService;
    private final UserPathService userPathService;
    private final StorageProviderRepository storageProviderRepository;
    private final StorageUploadService storageUploadService;
    private final AtomicInteger activeScanCount = new AtomicInteger(0);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    // task tracking for async admin tasks
    private final ConcurrentHashMap<String, TaskStatus> tasks = new ConcurrentHashMap<>();
    private final ThreadLocal<String> currentTaskId = new ThreadLocal<>();
    private final AtomicInteger scanCurrent = new AtomicInteger(0);
    private final AtomicInteger scanTotal = new AtomicInteger(0);
    private final Set<String> processedFiles = ConcurrentHashMap.newKeySet(); // 跟踪已处理的文件的路径
    private volatile LocalDateTime lastScanStart = null;
    private volatile LocalDateTime lastScanEnd = null;
    private final ThreadLocal<ScanProgressListener> currentScanProgressListener = new ThreadLocal<>();
    private final ThreadLocal<String> currentResumeAnchor = new ThreadLocal<>();
    private final ThreadLocal<String> currentResumeAnchorType = new ThreadLocal<>();
    private final ThreadLocal<Boolean> currentResumeAnchorReached = new ThreadLocal<>();
    private final ThreadLocal<String> currentResumeSkipSubtree = new ThreadLocal<>();
    private final ThreadLocal<Long> currentStorageProviderId = new ThreadLocal<>();
    private final ThreadLocal<Path> currentStorageProviderBasePath = new ThreadLocal<>();
    private final ThreadLocal<Long> currentStorageUserId = new ThreadLocal<>();

    private final ObjectMapper objectMapper;

    public PhotoScanService(AlbumRepository albumRepository,
                           PhotoRepository photoRepository,
                           TagRepository tagRepository,
                           TagService tagService,
                           ColorAnalysisService colorAnalysisService,
                           SubjectDetectionService subjectDetectionService,
                           FaceService faceService,
                           SmartTagService smartTagService,
                           AlbumAtmosphereAnalysisService atmosphereAnalysisService,
                           AtmosphereEffectsService atmosphereEffectsService,
                           SystemConfigService systemConfigService,
                           FilterOptionService filterOptionService,
                           PhotoAIScoringService aiScoringService,
                           BackgroundRemovalService backgroundRemovalService,
                           UserPathService userPathService,
                           StorageProviderRepository storageProviderRepository,
                           StorageUploadService storageUploadService,
                           ObjectMapper objectMapper) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.tagRepository = tagRepository;
        this.tagService = tagService;
        this.colorAnalysisService = colorAnalysisService;
        this.subjectDetectionService = subjectDetectionService;
        this.faceService = faceService;
        this.smartTagService = smartTagService;
        this.atmosphereAnalysisService = atmosphereAnalysisService;
        this.atmosphereEffectsService = atmosphereEffectsService;
        this.systemConfigService = systemConfigService;
        this.filterOptionService = filterOptionService;
        this.aiScoringService = aiScoringService;
        this.backgroundRemovalService = backgroundRemovalService;
        this.userPathService = userPathService;
        this.storageProviderRepository = storageProviderRepository;
        this.storageUploadService = storageUploadService;
        this.objectMapper = objectMapper;
    }
    
    private void createTask(String taskId, String initialMessage) {
        createTask(taskId, initialMessage, resolveTaskName(taskId));
    }

    private void createTask(String taskId, String initialMessage, String taskName) {
        TaskStatus ts = new TaskStatus();
        ts.taskId = taskId;
        ts.taskName = taskName;
        ts.status = "running";
        ts.startTime = LocalDateTime.now();
        if (initialMessage != null) ts.logs.add(LocalDateTime.now().toString() + " " + initialMessage);
        tasks.put(taskId, ts);
    }

    private String resolveTaskName(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return "后台异步任务";
        }
        String normalized = taskId.toLowerCase(Locale.ROOT);
        if (normalized.contains("face")) {
            return "人脸重建任务";
        }
        if (normalized.contains("exif")) {
            return "EXIF 修复任务";
        }
        if (normalized.contains("color")) {
            return "颜色分析任务";
        }
        return "后台异步任务";
    }

    public void runWithStorageContext(Long storageProviderId, Runnable action) {
        runWithStorageContext(storageProviderId, null, action);
    }

    public void runWithStorageContext(Long storageProviderId, Long storageUserId, Runnable action) {
        Long previousProviderId = currentStorageProviderId.get();
        Path previousBasePath = currentStorageProviderBasePath.get();
        Long previousStorageUserId = currentStorageUserId.get();
        try {
            currentStorageProviderId.set(storageProviderId);
            currentStorageProviderBasePath.set(resolveStorageProviderBasePath(storageProviderId));
            currentStorageUserId.set(storageUserId);
            action.run();
        } finally {
            if (previousProviderId == null) {
                currentStorageProviderId.remove();
            } else {
                currentStorageProviderId.set(previousProviderId);
            }
            if (previousBasePath == null) {
                currentStorageProviderBasePath.remove();
            } else {
                currentStorageProviderBasePath.set(previousBasePath);
            }
            if (previousStorageUserId == null) {
                currentStorageUserId.remove();
            } else {
                currentStorageUserId.set(previousStorageUserId);
            }
        }
    }

    private File resolveOriginalFile(Photo photo) throws IOException {
        if (photo == null || photo.getOriginalPath() == null || photo.getOriginalPath().isBlank()) {
            throw new IOException("照片原始路径为空");
        }
        var resolved = userPathService.tryResolveLocalStoredPhotoPath(photo.getOriginalPath());
        if (resolved.isEmpty()) {
            throw new IOException("照片路径不是可映射到本地磁盘的存储路径: " + toRelativePath(photo.getOriginalPath()));
        }
        return resolved.get().toFile();
    }

    private File resolveStoredPathSafely(String storedPath) throws IOException {
        if (storedPath == null || storedPath.isBlank()) {
            throw new IOException("路径为空");
        }
        var resolved = userPathService.tryResolveLocalStoredPhotoPath(storedPath);
        if (resolved.isEmpty()) {
            throw new IOException("路径不是可映射到本地磁盘的存储路径: " + toRelativePath(storedPath));
        }
        return resolved.get().toFile();
    }

    private Path resolveStorageProviderBasePath(Long storageProviderId) {
        if (storageProviderId == null) {
            return null;
        }
        StorageProvider provider = storageProviderRepository.findById(storageProviderId).orElse(null);
        if (provider == null || provider.getType() != StorageType.LOCAL) {
            return null;
        }
        return userPathService.resolveStorageProviderBaseDirectory(provider);
    }

    private String toStoredManagedPath(String absolutePath, Long userId) {
        if (absolutePath == null || absolutePath.isBlank()) {
            return absolutePath;
        }
        Long storageProviderId = currentStorageProviderId.get();
        Path providerBasePath = currentStorageProviderBasePath.get();
        if (storageProviderId == null || providerBasePath == null) {
            return absolutePath;
        }
        try {
            Path fullPath = Paths.get(absolutePath).toAbsolutePath().normalize();
            Path scopedRoot = userId != null ? providerBasePath.resolve(String.valueOf(userId)).normalize() : providerBasePath;
            if (!fullPath.startsWith(scopedRoot)) {
                return absolutePath;
            }
            Path relativePath = scopedRoot.relativize(fullPath);
            return userPathService.buildStoragePathReference(storageProviderId, userId, relativePath.toString());
        } catch (Exception e) {
            log.debug("转换存储路径引用失败，回退绝对路径: {}", absolutePath, e);
            return absolutePath;
        }
    }

    private void appendTaskLog(String taskId, String msg) {
        if (taskId == null) return;
        TaskStatus ts = tasks.get(taskId);
        if (ts != null) {
            ts.logs.add(LocalDateTime.now().toString() + " " + sanitizeVisibleMessage(msg));
        }
    }

    private void setTaskProgress(String taskId, int current, int total) {
        if (taskId == null) return;
        TaskStatus ts = tasks.get(taskId);
        if (ts != null) {
            ts.current = current;
            ts.total = total;
        }
    }

    private void completeTask(String taskId, String finalMessage) {
        if (taskId == null) return;
        TaskStatus ts = tasks.get(taskId);
        if (ts != null) {
            ts.complete = true;
            ts.status = "completed";
            ts.endTime = LocalDateTime.now();
            if (finalMessage != null) ts.logs.add(LocalDateTime.now().toString() + " " + sanitizeVisibleMessage(finalMessage));
        }
    }
    
    @PreDestroy
    public void onShutdown() {
        isShuttingDown.set(true);
        log.info("扫描服务正在关闭，停止所有异步任务");
    }

    /**
     * 初始化现有照片的处理状态
     */
    @Async
    public void initializeProcessingStatusAsync() {
        try {
            int total = countPhotosForCurrentScope();
            AtomicInteger updatedCount = new AtomicInteger();
            log.info("开始初始化照片处理状态，总计 {} 张", total);

            forEachPhotoInCurrentScope(photo -> {
                if (photo.getProcessingStatus() == ProcessingStatus.PENDING) {
                    photo.setProcessingStatus(ProcessingStatus.COMPLETED);
                    photoRepository.save(photo);
                    updatedCount.incrementAndGet();
                }
            });

            log.info("初始化照片处理状态完成，更新了 {} 张照片", updatedCount.get());
        } catch (Exception e) {
            log.error("初始化照片处理状态失败", e);
        }
    }

    /**
     * 异步回填所有照片的哈希值
     */
    @Async
    public void backfillHashesAsync() {
        try {
            int total = countPhotosForCurrentScope();
            log.info("开始回填哈希，总计 {} 张", total);
            forEachPhotoInCurrentScope(photo -> {
                try {
                    if (photo.getCanonicalPhotoId() != null) {
                        return;
                    }
                    if (photo.getContentHash() != null && !photo.getContentHash().isEmpty()) {
                        return;
                    }
                    File file = resolveOriginalFile(photo);
                    if (!file.exists()) {
                        log.warn("文件不存在，跳过哈希回填: {}", toRelativePath(photo.getOriginalPath()));
                        return;
                    }
                    String hash = calculateSha256(file);
                    photo.setContentHash(hash);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    log.warn("回填哈希失败: {}", toRelativePath(photo.getOriginalPath()), e);
                }
            });
            log.info("哈希回填完成");
        } catch (Exception e) {
            log.error("回填哈希任务失败", e);
        }
    }

    /**
     * 异步清空所有抠图缓存（文件 + 数据库记录）
     * @param taskId 任务ID（由调用方生成）
     */
    @Async
    public void clearBackgroundCacheAsync(String taskId) {
        TaskStatus ts = new TaskStatus();
        ts.taskId = taskId;
        ts.status = "running";
        ts.startTime = LocalDateTime.now();
        tasks.put(taskId, ts);
        
        try {
            log.info("开始清空抠图缓存...");
            ts.logs.add("开始清空抠图缓存...");

            ts.total = countPhotosWithBackgroundForCurrentScope();
            ts.logs.add("共找到 " + ts.total + " 个抠图文件");

            AtomicInteger totalCleared = new AtomicInteger();
            AtomicInteger totalErrors = new AtomicInteger();
            AtomicInteger processed = new AtomicInteger();

            forEachPhotoWithBackgroundInCurrentScope(photo -> {
                int current = processed.incrementAndGet();
                ts.current = current;
                String bgRemovedPath = photo.getBackgroundRemovedPath();
                try {
                    File bgFile = resolveStoredPathSafely(bgRemovedPath);
                    if (bgFile.exists()) {
                        if (bgFile.delete()) {
                            totalCleared.incrementAndGet();
                        } else {
                            totalErrors.incrementAndGet();
                            log.warn("无法删除文件: {}", bgRemovedPath);
                        }
                    }
                } catch (Exception e) {
                    totalErrors.incrementAndGet();
                    log.warn("删除文件失败: {} - {}", bgRemovedPath, e.getMessage());
                }

                if (current % 10 == 0) {
                    ts.logs.add("已处理 " + current + " / " + ts.total);
                }
            });
            
            int updatedCount = photoRepository.clearAllBackgroundRemovedPath();
            log.info("已清除 {} 条背景移除路径记录", updatedCount);
            ts.logs.add("已清除数据库记录: " + updatedCount + " 条");
            
            ts.logs.add("清空完成: 清除 " + totalCleared.get() + " 个文件, 错误 " + totalErrors.get() + " 个");
            log.info("清空抠图缓存完成: 清除 {} 个文件, 错误 {} 个", totalCleared.get(), totalErrors.get());
            
            ts.complete = true;
            ts.status = "completed";
        } catch (Exception e) {
            log.error("清空抠图缓存失败", e);
            ts.status = "failed";
            ts.logs.add("失败: " + sanitizeVisibleMessage(e.getMessage()));
        } finally {
            ts.endTime = LocalDateTime.now();
        }
    }

    @Transactional
    public Map<String, Object> clearBackgroundCache() {
        Map<String, Object> result = new HashMap<>();
        try {
            AtomicInteger totalCleared = new AtomicInteger();
            AtomicInteger totalErrors = new AtomicInteger();
            forEachPhotoWithBackgroundInCurrentScope(photo -> {
                String bgRemovedPath = photo.getBackgroundRemovedPath();
                try {
                    File bgFile = resolveStoredPathSafely(bgRemovedPath);
                    if (bgFile.exists() && !bgFile.delete()) {
                        totalErrors.incrementAndGet();
                        return;
                    }
                    totalCleared.incrementAndGet();
                } catch (Exception e) {
                    totalErrors.incrementAndGet();
                }
            });
            int updatedCount = photoRepository.clearAllBackgroundRemovedPath();
            result.put("success", true);
            result.put("deletedFiles", totalCleared.get());
            result.put("updatedRows", updatedCount);
            result.put("errors", totalErrors.get());
            result.put("message", "抠图缓存已清空");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "清空抠图缓存失败: " + sanitizeVisibleMessage(e.getMessage()));
        }
        return result;
    }

    /**
     * 异步批量移除背景（抠图处理）
     * @param albumId 相册ID（可选，为空则处理所有图片）
     * @param batchSize 每批处理数量
     * @param saveToPhoto 是否保存路径到照片记录
     * @param force 是否强制重新处理
     */
    @Async
    public void batchBackgroundRemovalAsync(String taskId, Long albumId, int batchSize, boolean saveToPhoto, boolean force) {
        batchBackgroundRemovalAsync(taskId, albumId, batchSize, saveToPhoto, force, null);
    }

    @Async
    public void batchBackgroundRemovalAsync(String taskId, Long albumId, int batchSize, boolean saveToPhoto, boolean force, Long scopedUserId) {
        Long previousUserId = currentStorageUserId.get();
        TaskStatus ts = tasks.get(taskId);
        if (ts == null) {
            ts = new TaskStatus();
            ts.taskId = taskId;
            ts.status = "running";
            ts.startTime = LocalDateTime.now();
            tasks.put(taskId, ts);
        }
        
        try {
            if (scopedUserId != null) {
                currentStorageUserId.set(scopedUserId);
            }
            if (!backgroundRemovalService.isModelAvailable()) {
                ts.status = "failed";
                ts.logs.add("背景移除功能未启用或模型未加载");
                return;
            }
            
            log.info("开始批量背景移除任务: albumId={}, batchSize={}, force={}", albumId, batchSize, force);
            ts.logs.add("开始批量背景移除...");
            
            // 先获取总数
            long totalPhotos;
            if (albumId != null) {
                totalPhotos = photoRepository.countByAlbumId(albumId);
                ts.logs.add("处理相册 " + albumId + " 的照片，共 " + totalPhotos + " 张");
            } else {
                Long userId = resolveCurrentScanUserId();
                totalPhotos = userId == null ? photoRepository.count() : photoRepository.countByUserId(userId);
                ts.logs.add("处理所有照片，共 " + totalPhotos + " 张");
            }
            ts.total = (int) totalPhotos;
            
            if (totalPhotos == 0) {
                ts.logs.add("没有找到需要处理的照片");
                ts.complete = true;
                ts.status = "completed";
                return;
            }
            
            int successCount = 0;
            int skipCount = 0;
            int failCount = 0;
            int processedCount = 0;
            int pageNum = 0;
            
            // 循环处理所有页面
            while (processedCount < totalPhotos) {
                // 检查任务是否被停止
                if (ts.stopped) {
                    ts.logs.add("任务已停止");
                    ts.status = "stopped";
                    break;
                }
                
                Page<Photo> photoPage;
                if (albumId != null) {
                    photoPage = photoRepository.findByAlbumId(albumId, PageRequest.of(pageNum, batchSize));
                } else if (resolveCurrentScanUserId() != null) {
                    photoPage = photoRepository.findByUserId(resolveCurrentScanUserId(), PageRequest.of(pageNum, batchSize));
                } else {
                    photoPage = photoRepository.findAll(PageRequest.of(pageNum, batchSize));
                }
                
                List<Photo> photos = photoPage.getContent();
                if (photos.isEmpty()) {
                    break;
                }
                
                for (Photo photo : photos) {
                    ts.current = processedCount + 1;
                    
                    try {
                        // 检查是否已有缓存（除非force=true）
                        if (!force && photo.getBackgroundRemovedPath() != null && !photo.getBackgroundRemovedPath().isEmpty()) {
                            File existingFile = resolveStoredPathSafely(photo.getBackgroundRemovedPath());
                            if (existingFile.exists()) {
                                skipCount++;
                                processedCount++;
                                continue;
                            }
                        }
                        
                        File sourceFile = resolveOriginalFile(photo);
                        if (!sourceFile.exists()) {
                            failCount++;
                            processedCount++;
                            continue;
                        }
                        
                        // 输出到 .thumbnails 文件夹
                        File parentDir = sourceFile.getParentFile();
                        File cacheDir = new File(parentDir, ".thumbnails");
                        if (!cacheDir.exists()) {
                            cacheDir.mkdirs();
                        }
                        File outputFile = new File(cacheDir, "bg_removed_" + photo.getId() + ".png");
                        
                        if (backgroundRemovalService.removeBackground(sourceFile, outputFile)) {
                            if (saveToPhoto) {
                                photo.setBackgroundRemovedPath(toStoredManagedPath(outputFile.getAbsolutePath(), photo.getUserId()));
                                photoRepository.save(photo);
                            }
                            successCount++;
                        } else {
                            failCount++;
                        }
                        
                    } catch (Exception e) {
                        log.error("处理照片失败: {}", photo.getId(), e);
                        failCount++;
                    }
                    
                    processedCount++;
                    
                    // 每处理10张输出一次日志
                    if (processedCount % 10 == 0) {
                        ts.logs.add("已处理 " + processedCount + " / " + totalPhotos + " (成功:" + successCount + " 跳过:" + skipCount + " 失败:" + failCount + ")");
                    }
                }
                
                pageNum++;
            }
            
            ts.logs.add("处理完成: 成功 " + successCount + ", 跳过 " + skipCount + ", 失败 " + failCount);
            log.info("批量背景移除完成: 成功 {} 跳过 {} 失败 {}", successCount, skipCount, failCount);
            
            ts.complete = true;
            ts.status = "completed";
        } catch (Exception e) {
            log.error("批量背景移除失败", e);
            ts.status = "failed";
            ts.logs.add("失败: " + sanitizeVisibleMessage(e.getMessage()));
        } finally {
            if (previousUserId == null) {
                currentStorageUserId.remove();
            } else {
                currentStorageUserId.set(previousUserId);
            }
            ts.endTime = LocalDateTime.now();
        }
    }

    /**
     * 定时扫描文件夹
     * initialDelay设置为扫描间隔时间，确保应用启动后不会立即执行第一次扫描
     */
    public void scheduledScan() {
        log.debug("定时扫描已迁移到 ScanTaskService.enqueueScheduledScan");
    }
    
    /**
     * 处理所有未完成的照片（优先处理）
     * @return 处理的照片数量
     */
    private int processIncompletePhotosFirst() {
        try {
            List<Photo> incompletePhotos = photoRepository.findPhotosNeedingReprocessing();
            if (incompletePhotos.isEmpty()) {
                return 0;
            }

            log.info("优先处理 {} 张未完成的照片", incompletePhotos.size());

            int processedCount = 0;
            int successCount = 0;
            int failCount = 0;

            for (Photo photo : incompletePhotos) {
                try {
                    // 检查文件是否存在
                    if (photo.getOriginalPath() == null || photo.getOriginalPath().isEmpty()) {
                        log.warn("照片 {} 没有原始路径，跳过", photo.getId());
                        continue;
                    }

                    File imageFile = resolveOriginalFile(photo);
                    if (!imageFile.exists()) {
                        log.warn("照片文件不存在: {}", toRelativePath(photo.getOriginalPath()));
                        // 标记为失败，但不删除记录（文件可能被移动）
                        photo.setProcessingStatus(ProcessingStatus.FAILED);
                        photo.addProcessingError("文件不存在");
                        photoRepository.save(photo);
                        failCount++;
                        continue;
                    }

                    // 获取相册信息
                    Album album = albumRepository.findById(photo.getAlbumId()).orElse(null);
                    if (album == null) {
                        log.warn("照片 {} 的相册不存在，跳过", photo.getId());
                        continue;
                    }

                    // 重新处理照片
                    boolean foundByContentHash = photo.getContentHash() != null &&
                                               photoRepository.findByContentHash(photo.getContentHash())
                                                   .filter(p -> !p.getId().equals(photo.getId()))
                                                   .isPresent();

                    processPhotoStepByStep(imageFile, photo, album, photo.getContentHash(),
                                         photo.getPathHash(), false, true, foundByContentHash);

                    if (photo.getProcessingStatus() == ProcessingStatus.COMPLETED) {
                        successCount++;
                    } else {
                        failCount++;
                    }

                    processedCount++;

                    // 每处理50张照片记录一次日志
                    if (processedCount % 50 == 0) {
                        log.info("已优先处理未完成照片: {}/{}", processedCount, incompletePhotos.size());
                    }

                } catch (Exception e) {
                    failCount++;
                    log.error("优先处理未完成照片失败: photoId={}, error={}", photo.getId(), e.getMessage());
                }
            }

            log.info("优先处理未完成照片完成 - 总计: {} 张，成功: {} 张，失败: {} 张",
                    processedCount, successCount, failCount);

            return processedCount;
        } catch (Exception e) {
            log.error("优先处理未完成照片时发生错误", e);
            return 0;
        }
    }

    /**
     * 统计文件系统中实际存在的照片数量
     */
    private long countPhotosInFilesystem() {
        try {
            Path basePath = resolveCurrentFilesystemScopeRoot();
            if (basePath == null) {
                return 0;
            }
            if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
                return 0;
            }

            Set<String> supportedSet = Arrays.stream(supportedFormats.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

            try (Stream<Path> paths = Files.walk(basePath)) {
                return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        if (name.contains("_thumb")) return false;
                        Path parent = p.getParent();
                        if (parent != null && parent.getFileName().toString().equals(".thumbnails")) return false;
                        String ext = FilenameUtils.getExtension(name).toLowerCase();
                        return supportedSet.contains(ext);
                    })
                    .count();
            }
        } catch (Exception e) {
            log.warn("统计文件系统照片数量失败", e);
            return 0;
        }
    }

    /**
     * 分析未扫描的文件
     */
    public Map<String, Object> analyzeUnscannedFiles() {
        Map<String, Object> result = new HashMap<>();

        try {
            long filesystemTotal = countPhotosInFilesystem();
            Long userId = resolveCurrentScanUserId();
            long databaseTotal = userId == null ? photoRepository.count() : photoRepository.countByUserId(userId);

            result.put("filesystemTotal", filesystemTotal);
            result.put("databaseTotal", databaseTotal);
            result.put("unscanned", filesystemTotal - databaseTotal);

            // 如果有未扫描的文件，尝试找出原因
            if (filesystemTotal > databaseTotal) {
                result.put("analysis", analyzeScanGaps());
            }

            result.put("success", true);
        } catch (Exception e) {
            log.error("分析未扫描文件失败", e);
            result.put("success", false);
            result.put("error", sanitizeVisibleMessage(e.getMessage()));
        }

        return result;
    }

    /**
     * 分析扫描差距的原因
     */
    private Map<String, Object> analyzeScanGaps() {
        Map<String, Object> analysis = new HashMap<>();

        try {
            // 获取文件系统中的所有照片路径
            Set<String> filesystemPaths = getAllFilesystemPhotoPaths();

            Set<String> databasePaths = getAllDatabasePhotoPathsForCurrentScope();

            // 找出未扫描的文件
            Set<String> unscannedPaths = new HashSet<>(filesystemPaths);
            unscannedPaths.removeAll(databasePaths);

            analysis.put("unscannedPaths", unscannedPaths);
            analysis.put("unscannedCount", unscannedPaths.size());

            // 分析未扫描文件的原因
            Map<String, Integer> reasons = new HashMap<>();
            for (String path : unscannedPaths) {
                File file = new File(path);
                if (!file.exists()) {
                    reasons.put("文件不存在", reasons.getOrDefault("文件不存在", 0) + 1);
                } else if (!file.canRead()) {
                    reasons.put("无读取权限", reasons.getOrDefault("无读取权限", 0) + 1);
                } else {
                    reasons.put("其他原因", reasons.getOrDefault("其他原因", 0) + 1);
                }
            }

            analysis.put("reasons", reasons);

        } catch (Exception e) {
            log.error("分析扫描差距失败", e);
            analysis.put("error", sanitizeVisibleMessage(e.getMessage()));
        }

        return analysis;
    }

    /**
     * 获取文件系统中所有照片的路径
     */
    private Set<String> getAllFilesystemPhotoPaths() {
        Set<String> paths = new HashSet<>();

        try {
            Path basePath = resolveCurrentFilesystemScopeRoot();
            if (basePath == null) {
                return paths;
            }
            if (!Files.exists(basePath) || !Files.isDirectory(basePath)) {
                return paths;
            }

            Set<String> supportedSet = Arrays.stream(supportedFormats.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

            try (Stream<Path> stream = Files.walk(basePath)) {
                stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        if (name.contains("_thumb")) return false;
                        Path parent = p.getParent();
                        if (parent != null && parent.getFileName().toString().equals(".thumbnails")) return false;
                        String ext = FilenameUtils.getExtension(name).toLowerCase();
                        return supportedSet.contains(ext);
                    })
                    .map(Path::toString)
                    .forEach(paths::add);
            }
        } catch (Exception e) {
            log.error("获取文件系统照片路径失败", e);
        }

        return paths;
    }

    /**
     * 异步触发扫描，避免阻塞接口
     */
    @Async
    public void scanDirectoryAsync(String directoryPath) {
        scanDirectory(directoryPath);
    }
    
    /**
     * 异步触发强制扫描（重新处理所有图片）
     */
    @Async
    public void rescanDirectoryAsync(String directoryPath) {
        rescanDirectory(directoryPath);
    }

    /**
     * 异步重建所有照片的人脸数据，默认尽量保留人物绑定与确认状态。
     */
    @Async
    public void rebuildAllFacesAsync(String taskId) {
        log.info("异步任务 {}: 开始重建所有照片的人脸数据", taskId);
        createTask(taskId, "任务已启动");
        currentTaskId.set(taskId);
        try {
            int total = Math.toIntExact(photoRepository.count());
            setTaskProgress(taskId, 0, total);
            appendTaskLog(taskId, "开始逐张重建人脸，默认保留已有人物绑定");

            int processed = 0;
            int success = 0;
            int skipped = 0;
            int failed = 0;
            int page = 0;
            final int pageSize = 100;

            while (true) {
                Page<Photo> photoPage = photoRepository.findAll(PageRequest.of(page, pageSize));
                if (!photoPage.hasContent()) {
                    break;
                }

                for (Photo photo : photoPage.getContent()) {
                    TaskStatus ts = tasks.get(taskId);
                    if (ts != null && ts.stopped) {
                        appendTaskLog(taskId, "任务已停止");
                        completeTask(taskId, "已停止");
                        return;
                    }

                    processed++;
                    setTaskProgress(taskId, processed, total);

                    if (photo.getOriginalPath() == null || photo.getOriginalPath().isEmpty()) {
                        skipped++;
                        appendTaskLog(taskId, "跳过照片 " + photo.getId() + "：原图路径为空");
                        continue;
                    }

                    File imageFile = new File(photo.getOriginalPath());
                    if (!imageFile.exists()) {
                        skipped++;
                        appendTaskLog(taskId, "跳过照片 " + photo.getId() + "：原图不存在");
                        continue;
                    }

                    try {
                        List<Face> faces = faceService.detectAndSaveFaces(imageFile, photo, false, true, true);
                        success++;
                        appendTaskLog(taskId, "照片 " + photo.getId() + " 重建完成，人脸数=" + (faces == null ? 0 : faces.size()));
                    } catch (Exception e) {
                        failed++;
                        appendTaskLog(taskId, "照片 " + photo.getId() + " 重建失败: " + e.getMessage());
                        log.warn("重建照片 {} 的人脸失败", photo.getId(), e);
                    }
                }

                if (!photoPage.hasNext()) {
                    break;
                }
                page++;
            }

            appendTaskLog(taskId, "重建结束：成功 " + success + "，跳过 " + skipped + "，失败 " + failed);
            completeTask(taskId, "已完成");
            log.info("异步任务 {}: 重建所有人脸完成", taskId);
        } catch (Exception e) {
            appendTaskLog(taskId, "发生异常: " + e.getMessage());
            completeTask(taskId, "已失败");
            log.error("异步任务 {}: 重建所有人脸失败", taskId, e);
        } finally {
            currentTaskId.remove();
        }
    }

    /**
     * 异步重新计算所有照片的颜色相关属性（后台任务）
     */
    @Async
    public void recalculateAllPhotoColorsAsync(String taskId) {
        log.info("异步任务 {}: 开始重新计算所有照片的颜色相关属性", taskId);
        createTask(taskId, "任务已启动");
        currentTaskId.set(taskId);
        try {
            recalculateAllPhotoColors();
            appendTaskLog(taskId, "颜色重新计算完成，开始更新筛选选项");
            // 更新筛选选项
            try {
                filterOptionService.updateAllFilterOptions();
                appendTaskLog(taskId, "筛选选项更新完成");
            } catch (Exception e) {
                log.error("更新筛选选项失败", e);
                appendTaskLog(taskId, "筛选选项更新失败: " + sanitizeVisibleMessage(e.getMessage()));
                // 不抛出异常，避免影响整体任务
            }
            completeTask(taskId, "已完成");
            log.info("异步任务 {}: 颜色重新计算完成", taskId);
        } catch (Exception e) {
            completeTask(taskId, "已失败");
            log.error("异步任务 {}: 重新计算颜色相关属性失败", taskId, e);
        } finally {
            currentTaskId.remove();
        }
    }

    /**
     * 异步更新所有照片的颜色分类（后台任务）
     */
    @Async
    public void updateAllColorCategoriesAsync(String taskId) {
        log.info("异步任务 {}: 开始更新所有照片的颜色分类", taskId);
        createTask(taskId, "任务已启动");
        currentTaskId.set(taskId);
        try {
            updateAllColorCategories();
            completeTask(taskId, "已完成");
            log.info("异步任务 {}: 颜色分类更新完成", taskId);
        } catch (Exception e) {
            completeTask(taskId, "已失败");
            log.error("异步任务 {}: 更新颜色分类失败", taskId, e);
        } finally {
            currentTaskId.remove();
        }
    }

    /**
     * 异步更新所有照片的 EXIF 数值字段（后台任务）
     */
    @Async
    public void updateAllExifNumericFieldsAsync(String taskId) {
        log.info("异步任务 {}: 开始更新所有照片的 EXIF 字段", taskId);
        createTask(taskId, "任务已启动");
        currentTaskId.set(taskId);
        try {
            updateAllExifNumericFields();
            appendTaskLog(taskId, "EXIF更新完成，开始更新筛选选项");
            // 更新筛选选项
            try {
                filterOptionService.updateAllFilterOptions();
                appendTaskLog(taskId, "筛选选项更新完成");
            } catch (Exception e) {
                log.error("更新筛选选项失败", e);
                appendTaskLog(taskId, "筛选选项更新失败: " + sanitizeVisibleMessage(e.getMessage()));
                // 不抛出异常，避免影响整体任务
            }
            completeTask(taskId, "已完成");
            log.info("异步任务 {}: 更新完成", taskId);
        } catch (Exception e) {
            appendTaskLog(taskId, "发生异常: " + sanitizeVisibleMessage(e.getMessage()));
            completeTask(taskId, "已失败");
            log.error("异步任务 {}: 更新 EXIF 数值字段失败", taskId, e);
        } finally {
            currentTaskId.remove();
        }
    }

    
    /**
     * 应用启动后执行一次扫描
     */
    @PostConstruct
    public void init() {
        log.info("扫描服务初始化，默认扫描根目录: {}", resolveBasePath());

        // 初始化现有照片的处理状态
        initializeProcessingStatusAsync();

        // 检查是否有需要重新处理的照片
        checkAndRetryIncompletePhotos();

        // 检查是否需要初始化扫描：如果数据库中没有任何相册，则执行一次扫描
        try {
            long albumCount = albumRepository.count();
            if (albumCount == 0) {
                log.info("数据库中没有任何相册，执行初始化扫描");
                scanDirectoryAsync(null);
            } else {
                log.info("数据库中已有 {} 个相册，跳过初始化扫描", albumCount);
            }
        } catch (Exception e) {
            log.warn("检查相册数量失败，跳过初始化扫描", e);
        }
    }

    /**
     * 检查并重试未完成的照片处理
     */
    private void checkAndRetryIncompletePhotos() {
        try {
            Long userId = resolveCurrentScanUserId();
            long failedCount = userId == null ? photoRepository.countFailedPhotos() : photoRepository.countFailedPhotosByUserId(userId);
            long incompleteCount = userId == null ? photoRepository.countIncompletePhotos() : photoRepository.countIncompletePhotosByUserId(userId);

            if (failedCount > 0 || incompleteCount > 0) {
                log.info("发现需要重新处理的照片 - 失败: {} 张，未完成: {} 张", failedCount, incompleteCount);

                // 如果有失败的照片，启动重试任务
                if (failedCount > 0) {
                    log.info("启动失败照片重试任务");
                    retryFailedPhotosAsync();
                }

                // 如果有未完成的照片，在下次扫描时会自动处理
                if (incompleteCount > 0) {
                    log.info("发现 {} 张未完成的照片，将在下次扫描时继续处理", incompleteCount);
                }
            } else {
                log.info("所有照片处理状态正常");
            }
        } catch (Exception e) {
            log.warn("检查未完成照片失败", e);
        }
    }

    /**
     * 手动触发扫描
     */
    @Transactional
    public void scanDirectory(String directoryPath) {
        scanDirectory(directoryPath, null);
    }

    @Transactional
    public void scanDirectory(String directoryPath, ScanProgressListener listener) {
        executeWithScanProgressListener(listener, () -> scanDirectoryInternal(directoryPath, false));
    }

    /**
     * 强制重新扫描（重新处理所有图片，重建缩略图、人脸、标签）
     */
    @Transactional
    public void rescanDirectory(String directoryPath) {
        rescanDirectory(directoryPath, null);
    }

    @Transactional
    public void rescanDirectory(String directoryPath, ScanProgressListener listener) {
        executeWithScanProgressListener(listener, () -> scanDirectoryInternal(directoryPath, true));
    }

    /**
     * 仅针对单张图片重建人脸与向量
     * @return 返回结果描述
     */
    @Transactional
    public Map<String, Object> rescanFacesForPhoto(Long photoId) {
        Map<String, Object> result = new HashMap<>();
        Optional<Photo> opt = photoRepository.findById(photoId);
        if (opt.isEmpty()) {
            result.put("error", "照片不存在");
            return result;
        }
        Photo photo = opt.get();
        if (photo.getOriginalPath() == null || photo.getOriginalPath().isEmpty()) {
            result.put("error", "原始路径为空，无法定位文件");
            return result;
        }
        File imageFile;
        try {
            imageFile = resolveOriginalFile(photo);
        } catch (IOException e) {
            result.put("error", "解析原始文件路径失败: " + toRelativePath(e.getMessage()));
            return result;
        }
        if (!imageFile.exists()) {
            result.put("error", "文件不存在: " + userPathService.toDisplayPath(photo.getOriginalPath(), true));
            return result;
        }
        // 调用现有人脸检测流程（单张重建时开启详细日志）
        List<Face> faces = faceService.detectAndSaveFaces(imageFile, photo, true, true, true);
        int count = faces == null ? 0 : faces.size();

        // 安全更新关联集合，避免 orphan 触发
        try {
            // 不再直接修改photo的faces属性，而是通过faceRepository来管理关联
            // faces已经在faceService.detectAndSaveFaces中保存到数据库了
            // 这里只需要确保photo实体的faces集合与数据库同步即可
            // 但为了避免Hibernate orphanRemoval问题，我们不直接操作集合

            // 如果需要更新photo的处理状态，可以在这里进行
            // photo.setProcessingStatus(ProcessingStatus.FACES_DONE);
            // photoRepository.save(photo);
        } catch (Exception e) {
            log.warn("人脸重建完成但状态更新失败: photoId={}, err={}", photoId, e.getMessage());
        }

        result.put("count", count);
        result.put("photoId", photoId);
        if (count == 0) {
            result.put("message", "未检测到人脸或全部被过滤，请检查阈值/尺寸/比例设置");
        } else {
            result.put("message", "重建完成");
        }
        return result;
    }

    /**
     * 仅重算单张图片已有的人脸 embedding，保留原有人物绑定。
     */
    @Transactional
    public Map<String, Object> rebuildFaceEmbeddingsForPhoto(Long photoId) {
        Map<String, Object> result = new HashMap<>();
        Optional<Photo> opt = photoRepository.findById(photoId);
        if (opt.isEmpty()) {
            result.put("error", "照片不存在");
            return result;
        }

        Photo photo = opt.get();
        if (photo.getOriginalPath() == null || photo.getOriginalPath().isEmpty()) {
            result.put("error", "原始路径为空，无法定位文件");
            return result;
        }

        File imageFile;
        try {
            imageFile = resolveOriginalFile(photo);
        } catch (IOException e) {
            result.put("error", "解析原始文件路径失败: " + toRelativePath(e.getMessage()));
            return result;
        }
        if (!imageFile.exists()) {
            result.put("error", "文件不存在: " + userPathService.toDisplayPath(photo.getOriginalPath(), true));
            return result;
        }

        List<Face> faces = faceService.rebuildEmbeddingsForPhoto(imageFile, photo);
        result.put("photoId", photoId);
        result.put("count", faces.size());
        result.put("message", faces.isEmpty() ? "该照片暂无可重算的人脸" : "embedding重算完成");
        return result;
    }

    @Transactional
    public Map<String, Object> removeBackgroundForPhoto(Long photoId, boolean forceRebuild) {
        Map<String, Object> result = new HashMap<>();
        Optional<Photo> opt = photoRepository.findById(photoId);
        if (opt.isEmpty()) {
            result.put("error", "照片不存在");
            return result;
        }
        if (!backgroundRemovalService.isModelAvailable()) {
            result.put("error", "背景移除功能未启用或模型未加载");
            return result;
        }
        Photo photo = opt.get();
        try {
            if (!forceRebuild && photo.getBackgroundRemovedPath() != null && !photo.getBackgroundRemovedPath().isBlank()) {
                File existingFile = resolveStoredPathSafely(photo.getBackgroundRemovedPath());
                if (existingFile.exists()) {
                    result.put("message", "已有抠图缓存，跳过");
                    result.put("skipped", true);
                    return result;
                }
            }
            File sourceFile = resolveOriginalFile(photo);
            if (!sourceFile.exists()) {
                result.put("error", "源文件不存在");
                return result;
            }
            File parentDir = sourceFile.getParentFile();
            File cacheDir = new File(parentDir, ".thumbnails");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File outputFile = new File(cacheDir, "bg_removed_" + photo.getId() + ".png");
            if (backgroundRemovalService.removeBackground(sourceFile, outputFile)) {
                photo.setBackgroundRemovedPath(toStoredManagedPath(outputFile.getAbsolutePath(), photo.getUserId()));
                photoRepository.save(photo);
                result.put("success", true);
                result.put("message", "背景移除完成");
                result.put("photoId", photoId);
                return result;
            }
            result.put("error", "背景移除失败");
            return result;
        } catch (Exception e) {
            result.put("error", "背景移除失败: " + sanitizeVisibleMessage(e.getMessage()));
            return result;
        }
    }

    /**
     * 获取本次扫描跳过的文件列表（进度差异详情）
     */
    public List<SkippedFileRecord> getSkippedFileRecords(UserAccount currentUser) {
        if (currentUser == null) {
            return List.of();
        }
        if (currentUser.getRole() == UserRole.SUPER_ADMIN) {
            return new ArrayList<>(skippedFileRecords);
        }
        return skippedFileRecords.stream()
            .filter(record -> Objects.equals(record.userId, currentUser.getId()))
            .collect(Collectors.toList());
    }

    public Map<String, Object> clearSkippedFileRecords(UserAccount currentUser) {
        Map<String, Object> result = new HashMap<>();
        if (currentUser == null) {
            result.put("success", false);
            result.put("message", "未找到当前用户");
            return result;
        }

        int removed;
        synchronized (skippedFileRecords) {
            if (currentUser.getRole() == UserRole.SUPER_ADMIN) {
                removed = skippedFileRecords.size();
                skippedFileRecords.clear();
            } else {
                int before = skippedFileRecords.size();
                skippedFileRecords.removeIf(record -> Objects.equals(record.userId, currentUser.getId()));
                removed = before - skippedFileRecords.size();
            }
        }
        result.put("success", true);
        result.put("removed", removed);
        result.put("message", removed > 0 ? "失败文件记录已清理" : "当前没有可清理的失败文件记录");
        return result;
    }

    /**
     * 获取扫描进度/状态
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getScanStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("scanning", isScanning.get() || activeScanCount.get() > 0);

        // 处理并发扫描情况：如果有多个扫描在进行，显示并发状态，不显示进度
        if (activeScanCount.get() > 1) {
            status.put("concurrent", true);
            status.put("activeScanCount", activeScanCount.get());
            status.put("current", 0);
            status.put("total", 0);
            status.put("scanMode", "concurrent");
            return status; // 并发扫描时不进行后续统计
        } else {
            status.put("concurrent", false);
        }

        status.put("lastScanStart", lastScanStart);
        status.put("lastScanEnd", lastScanEnd);

        // 添加处理状态统计和文件系统统计
        try {
            Long userId = resolveCurrentScanUserId();
            long totalPhotos = userId == null ? photoRepository.count() : photoRepository.countByUserId(userId);
            long failedCount = userId == null ? photoRepository.countFailedPhotos() : photoRepository.countFailedPhotosByUserId(userId);
            long completedCount = userId == null
                ? photoRepository.countPhotosByProcessingStatus(ProcessingStatus.COMPLETED)
                : photoRepository.countPhotosByProcessingStatusAndUserId(ProcessingStatus.COMPLETED, userId);
            long incompleteCount = totalPhotos - failedCount - completedCount;

            // 确保计数不为负数（处理可能的计算误差）
            incompleteCount = Math.max(0, incompleteCount);

            status.put("processingStats", Map.of(
                "total", totalPhotos,
                "failed", failedCount,
                "incomplete", incompleteCount,
                "completed", completedCount
            ));

            // 添加文件系统中的实际照片数量统计，并设置扫描进度
            try {
                long filesystemTotal = countPhotosInFilesystem();
                int displayCurrent;

                // 根据扫描状态设置进度显示
                boolean isCurrentlyScanning = isScanning.get() || activeScanCount.get() > 0;
                if (isCurrentlyScanning) {
                    // 扫描进行中：显示本次遍历的照片数量 / 文件系统总照片数量
                    int traversedThisScan = scanCurrent.get();  // 本次扫描已遍历的照片数量
                    displayCurrent = Math.min(traversedThisScan, (int) filesystemTotal);
                    status.put("current", displayCurrent);
                    status.put("total", filesystemTotal);
                    status.put("scanMode", "scanning"); // 表示正在扫描文件
                } else {
                    // 扫描未进行：如果本次会话有过扫描，使用扫描计数器（包含空文件/去重文件）
                    int lastScanCurrent = scanCurrent.get();
                    if (lastScanCurrent > 0 && lastScanCurrent >= totalPhotos) {
                        // 扫描已完成，显示实际遍历数/总数（可能 >= total，因为包含空文件和去重文件的计数）
                        displayCurrent = Math.min(lastScanCurrent, (int) filesystemTotal);
                    } else {
                        // 未扫描过或计数异常，显示数据库记录数
                        displayCurrent = (int) Math.min(totalPhotos, filesystemTotal);
                    }
                    status.put("current", displayCurrent);
                    status.put("total", filesystemTotal);
                    status.put("scanMode", "completed"); // 表示扫描已完成
                }

                long waitingCount = Math.max(filesystemTotal - displayCurrent, 0);

                status.put("filesystemStats", Map.of(
                    "total", filesystemTotal,
                    "scanned", totalPhotos,
                    "unscanned", Math.max(0, filesystemTotal - totalPhotos)
                ));
                status.put("scanSummary", Map.of(
                    "total", filesystemTotal,
                    "scanned", displayCurrent,
                    "failed", failedCount,
                    "waiting", waitingCount
                ));
            } catch (Exception e) {
                log.debug("统计文件系统照片数量失败", e);
                // 如果无法获取文件系统统计，则使用数据库统计作为fallback
                status.put("current", totalPhotos);
                status.put("total", totalPhotos); // fallback：已扫描数量作为总数
                status.put("scanMode", "fallback");
                status.put("filesystemStats", Map.of("error", "无法获取文件系统统计"));
                status.put("scanSummary", Map.of(
                    "total", totalPhotos,
                    "scanned", totalPhotos,
                    "failed", failedCount,
                    "waiting", 0
                ));
            }
        } catch (Exception e) {
            log.warn("获取处理状态统计失败", e);
            status.put("processingStats", Map.of("error", "无法获取统计信息"));
        }

        return status;
    }

    /**
     * 重试所有失败的照片处理
     */
    @Async
    @Transactional
    public void retryFailedPhotosAsync() {
        log.info("开始重试失败的照片处理");
        try {
            Long userId = resolveCurrentScanUserId();
            List<Photo> failedPhotos = userId == null ? photoRepository.findFailedPhotos() : photoRepository.findFailedPhotosByUserId(userId);
            log.info("发现 {} 张处理失败的照片", failedPhotos.size());

            int retrySuccessCount = 0;
            int retryFailCount = 0;

            for (Photo photo : failedPhotos) {
                try {
                    // 重置处理状态为待处理
                    photo.setProcessingStatus(ProcessingStatus.PENDING);
                    photo.setProcessingErrors(null); // 清除之前的错误信息

                    // 获取文件路径
                    if (photo.getOriginalPath() == null || photo.getOriginalPath().isEmpty()) {
                        log.warn("照片 {} 没有原始路径，跳过重试", photo.getId());
                        continue;
                    }

                    File imageFile = resolveOriginalFile(photo);
                    if (!imageFile.exists()) {
                        log.warn("照片文件不存在，标记为失败: {}", toRelativePath(photo.getOriginalPath()));
                        photo.setProcessingStatus(ProcessingStatus.FAILED);
                        photo.addProcessingError("文件不存在");
                        photoRepository.save(photo);
                        retryFailCount++;
                        continue;
                    }

                    // 获取相册信息
                    Album album = albumRepository.findById(photo.getAlbumId()).orElse(null);
                    if (album == null) {
                        log.warn("照片 {} 的相册不存在，跳过重试", photo.getId());
                        continue;
                    }

                    // 重新处理照片（强制处理）
                    processPhotoStepByStep(imageFile, photo, album, photo.getContentHash(),
                                         photo.getPathHash(), true, true, false);

                    if (photo.getProcessingStatus() == ProcessingStatus.COMPLETED) {
                        retrySuccessCount++;
                        log.info("重试成功: {}", toRelativePath(photo.getOriginalPath()));
                    } else {
                        retryFailCount++;
                        log.warn("重试仍然失败: {}", toRelativePath(photo.getOriginalPath()));
                    }

                } catch (Exception e) {
                    retryFailCount++;
                    log.error("重试照片处理失败: photoId={}, error={}", photo.getId(), e.getMessage());
                }
            }

            log.info("重试完成，成功: {} 张，失败: {} 张", retrySuccessCount, retryFailCount);

        } catch (Exception e) {
            log.error("重试失败的照片处理任务失败", e);
        }
    }

    /**
     * 重新分析所有相册的氛围信息
     */
    @Async
    @Transactional
    public void reanalyzeAllAtmosphere() {
        log.info("开始重新分析所有相册的氛围信息");
        try {
            Long userId = resolveCurrentScanUserId();
            atmosphereAnalysisService.analyzeAllAlbumsAtmosphere(userId);
            atmosphereEffectsService.analyzeAllAlbumsEffects(userId);
            log.info("所有相册氛围信息重新分析完成");
        } catch (Exception e) {
            log.error("重新分析氛围信息失败", e);
        }
    }

    /**
     * 重新分析指定相册的氛围信息
     */
    @Transactional
    public void reanalyzeAlbumAtmosphere(Long albumId) {
        log.info("重新分析相册 {} 的氛围信息", albumId);
        try {
            atmosphereAnalysisService.analyzeAlbumAtmosphere(albumId);
            atmosphereEffectsService.analyzeAlbumEffects(albumId);
            log.info("相册 {} 氛围信息重新分析完成", albumId);
        } catch (Exception e) {
            log.error("重新分析相册 {} 氛围信息失败", albumId, e);
        }
    }

    /**
     * 设置相册氛围特效
     */
    @Transactional
    public Map<String, Object> setAlbumAtmosphereEffects(Long albumId, List<Map<String, Object>> effects) {
        Map<String, Object> result = new HashMap<>();

        try {
            Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));

            // 将特效配置转换为 AtmosphereEffectDTO 列表
            List<com.photoexhibition.dto.AtmosphereEffectDTO> effectDTOs = new ArrayList<>();
            for (Map<String, Object> effect : effects) {
                String type = (String) effect.get("type");
                String intensity = (String) effect.get("intensity");
                String layer = (String) effect.get("layer");

                // 使用前端发送的config，如果没有则生成默认配置
                @SuppressWarnings("unchecked")
                Map<String, Object> config = (Map<String, Object>) effect.get("config");
                if (config == null) {
                    // 如果前端没有发送config，使用AtmosphereEffectsService生成默认配置
                    config = (Map<String, Object>) atmosphereEffectsService.generateEffectConfig(type, intensity, layer);
                }
                // 手动设置的特效移除自动生成标记，防止被自动分析覆盖
                if (config != null) {
                    config.remove("source");
                }

                com.photoexhibition.dto.AtmosphereEffectDTO dto = new com.photoexhibition.dto.AtmosphereEffectDTO(
                    type, intensity, layer, config);
                effectDTOs.add(dto);
            }

            // 序列化特效配置
            String effectsJson = effectDTOs.isEmpty() ? null :
                objectMapper.writeValueAsString(effectDTOs);

            // 更新相册特效
            album.setAtmosphereEffects(effectsJson);
            album.setAtmosphereLastUpdated(LocalDateTime.now());
            albumRepository.save(album);

            result.put("message", "相册特效设置成功");
            result.put("effectsCount", effectDTOs.size());

        } catch (Exception e) {
            log.error("设置相册 {} 特效失败", albumId, e);
            throw new RuntimeException("设置特效失败: " + sanitizeVisibleMessage(e.getMessage()));
        }

        return result;
    }

    /**
     * 获取相册当前氛围特效配置
     */
    public Map<String, Object> getAlbumAtmosphereEffects(Long albumId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new RuntimeException("相册不存在"));

            List<com.photoexhibition.dto.AtmosphereEffectDTO> effects = new ArrayList<>();
            if (album.getAtmosphereEffects() != null && !album.getAtmosphereEffects().isEmpty()) {
                try {
                    effects = objectMapper.readValue(album.getAtmosphereEffects(),
                        objectMapper.getTypeFactory().constructCollectionType(
                            List.class, com.photoexhibition.dto.AtmosphereEffectDTO.class));
                } catch (Exception e) {
                    log.warn("解析相册 {} 特效配置失败: {}", albumId, e.getMessage());
                }
            }

            result.put("effects", effects);
            result.put("lastUpdated", album.getAtmosphereLastUpdated());

        } catch (Exception e) {
            log.error("获取相册 {} 特效配置失败", albumId, e);
            throw new RuntimeException("获取特效配置失败: " + sanitizeVisibleMessage(e.getMessage()));
        }

        return result;
    }

    private void scanDirectoryInternal(String directoryPath, boolean force) {
        StorageProvider currentProvider = resolveCurrentStorageProvider();
        if (currentProvider != null && currentProvider.getType() != StorageType.LOCAL) {
            scanRemoteDirectoryInternal(directoryPath, force, currentProvider);
            return;
        }
        Path path = resolveRequestedFilesystemPath(directoryPath);
        String scanRootLabel = path.toString();
        ensureScanCanContinue(scanRootLabel);
        activeScanCount.incrementAndGet();
        final Set<String> allExpectedPaths = new java.util.LinkedHashSet<>();
        Exception scanFailure = null;
        try {
            // 只有在没有其他扫描进行时才重置计数器和设置扫描状态
            if (activeScanCount.get() == 1) {
                isScanning.set(true);
                ScanProgressListener listener = currentScanProgressListener.get();
                scanCurrent.set(listener != null ? Math.max(0, listener.getInitialProcessedItems()) : 0);
                scanTotal.set(0);
                lastScanStart = LocalDateTime.now();
            } else {
                // 如果有并发扫描，为了避免计数混乱，我们不显示进度
                // 或者可以为每个扫描任务分配独立的计数器
                log.warn("检测到并发扫描 (activeScanCount={}), 进度显示可能不准确", activeScanCount.get());
            }

            if (!Files.exists(path)) {
                throw new IllegalArgumentException("目录不存在: " + path);
            }
            
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException("路径不是文件夹: " + path);
            }

            // 取消优先处理逻辑，所有照片都在正常的目录遍历中处理

            // 重置扫描计数器
            if (activeScanCount.get() == 1) {
                ScanProgressListener listener = currentScanProgressListener.get();
                scanCurrent.set(listener != null ? Math.max(0, listener.getInitialProcessedItems()) : 0);
                scanTotal.set(0);
                processedFiles.clear();
                skippedFileRecords.clear();
                skippedFileIndex.set(0);
            }

            // 预统计总数，同时收集所有文件路径（供扫描结束后做差集，避免二次遍历）
            try (Stream<Path> paths = Files.walk(path)) {
                Set<String> supportedSet = Arrays.stream(supportedFormats.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
                paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        if (name.contains("_thumb")) return false;
                        Path parent = p.getParent();
                        if (parent != null && parent.getFileName().toString().equals(".thumbnails")) return false;
                        String ext = FilenameUtils.getExtension(name).toLowerCase();
                        return supportedSet.contains(ext);
                    })
                    .map(Path::toString)
                    .forEach(allExpectedPaths::add);

                int total = allExpectedPaths.size();
                ScanProgressListener listener = currentScanProgressListener.get();
                int initialProcessed = listener != null ? Math.max(0, listener.getInitialProcessedItems()) : 0;
                // 设置扫描总数（只有第一个扫描任务才设置）
                if (activeScanCount.get() == 1) {
                    scanTotal.set(total);
                    scanCurrent.set(initialProcessed);
                } else {
                    scanTotal.addAndGet(total);
                }

                log.info("预统计待扫描图片数量: {}", total);
                notifyScanPrepared(path.toString(), force, total);
            } catch (Exception e) {
                log.warn("统计待扫描图片数量失败: {}", e.getMessage());
                ScanProgressListener listener = currentScanProgressListener.get();
                int initialProcessed = listener != null ? Math.max(0, listener.getInitialProcessedItems()) : 0;
                if (activeScanCount.get() == 1) {
                    scanTotal.set(0);
                    scanCurrent.set(initialProcessed);
                }
                notifyScanPrepared(path.toString(), force, 0);
            }

            // 先处理根目录本身（如果它包含图片文件）
            ensureScanCanContinue(path.toString());
            processAlbumDirectory(path, force);

            // 扫描所有子文件夹，跳过.thumbnails目录
            try (Stream<Path> paths = Files.walk(path)) {
                final boolean finalForce = force; // 创建final变量用于lambda表达式
                final Path rootPath = path; // 创建final变量用于lambda表达式
                paths.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(".thumbnails"))  // 跳过.thumbnails目录
                    .filter(p -> !p.equals(rootPath))  // 避免重复处理根目录
                    .forEach(p -> {
                        ensureScanCanContinue(p.toString());
                        processAlbumDirectory(p, finalForce);
                    });
            }
        } catch (Exception e) {
            scanFailure = e;
            if (e instanceof ScanInterruptedException) {
                log.info("{}: {}", e.getMessage(), toRelativePath(((ScanInterruptedException) e).getPath()));
                throw (ScanInterruptedException) e;
            }
            log.error("扫描目录失败: {}", scanRootLabel, e);
            throw new RuntimeException("扫描目录失败: " + (e.getMessage() == null ? scanRootLabel : e.getMessage()), e);
        } finally {
            lastScanEnd = LocalDateTime.now();
            if (activeScanCount.decrementAndGet() <= 0) {
                isScanning.set(false);
                // 扫描完成后先更新 EXIF 数值字段，再更新筛选选项
                try {
                    log.info("扫描完成，开始更新 EXIF 数值字段...");
                    updateAllExifNumericFields();
                    log.info("EXIF 数值字段更新完成，开始更新筛选选项...");
                    filterOptionService.updateAllFilterOptions();
                    log.info("筛选选项更新完成");
                } catch (Exception e) {
                    log.error("更新筛选选项失败", e);
                    // 不抛出异常，避免影响扫描结果
                }

                // 补录未被遍历到的文件（用预统计时收集的路径集合做差集，无需二次 walk）
                try {
                    Set<String> alreadyRecorded = skippedFileRecords.stream()
                        .map(r -> r.relativePath)
                        .collect(java.util.stream.Collectors.toSet());
                    int missedCount = 0;
                    for (String absPath : allExpectedPaths) {
                        if (!processedFiles.contains(absPath)) {
                            String relPath = toRelativePath(absPath);
                            if (!alreadyRecorded.contains(relPath)) {
                                File f = new File(absPath);
                                String reason = !f.exists() ? "文件不存在"
                                    : !f.canRead() ? "无读取权限"
                                    : f.length() == 0 ? "文件为空"
                                    : "未被扫描到";
                                String detail = "该文件在扫描遍历中未被处理，可能因上级目录处理时发生异常";
                                log.debug("补录未遍历文件: {} (原因: {}, absPath: {})", relPath, reason, absPath);
                                skippedFileRecords.add(buildSkippedFileRecord(absPath, reason, detail, f.length()));
                                scanCurrent.incrementAndGet();
                                notifyScanSkip(absPath, "FILE", reason, detail, scanCurrent.get(), scanTotal.get());
                                missedCount++;
                            }
                        }
                    }
                    if (missedCount > 0) {
                        log.info("补录完成: {} 个文件未在扫描中被处理, processedFiles 大小: {}, allExpectedPaths 大小: {}",
                            missedCount, processedFiles.size(), allExpectedPaths.size());
                    }
                } catch (Exception e) {
                    log.warn("补录未遍历文件失败", e);
                }

                if (scanFailure != null) {
                    notifyScanFailed(scanFailure);
                } else {
                    notifyScanCompleted();
                }
            }
        }
    }

    private void scanRemoteDirectoryInternal(String directoryPath, boolean force, StorageProvider provider) {
        ensureScanCanContinue(directoryPath);
        activeScanCount.incrementAndGet();
        Exception scanFailure = null;
        try {
            if (activeScanCount.get() == 1) {
                isScanning.set(true);
                ScanProgressListener listener = currentScanProgressListener.get();
                scanCurrent.set(listener != null ? Math.max(0, listener.getInitialProcessedItems()) : 0);
                scanTotal.set(0);
                lastScanStart = LocalDateTime.now();
                processedFiles.clear();
                skippedFileRecords.clear();
                skippedFileIndex.set(0);
            }

            Path remoteRoot = resolveRemoteScanRoot(directoryPath, provider);
            RemoteScanStats stats = collectRemoteScanStats(provider, remoteRoot);
            ScanProgressListener listener = currentScanProgressListener.get();
            int initialProcessed = listener != null ? Math.max(0, listener.getInitialProcessedItems()) : 0;
            if (activeScanCount.get() == 1) {
                scanTotal.set(stats.totalItems);
                scanCurrent.set(initialProcessed);
            } else {
                scanTotal.addAndGet(stats.totalItems);
            }
            notifyScanPrepared(remoteRoot.toString(), force, stats.totalItems);
            processRemoteDirectory(provider, remoteRoot, force);
        } catch (Exception e) {
            scanFailure = e;
            if (e instanceof ScanInterruptedException) {
                log.info("{}: {}", e.getMessage(), toRelativePath(((ScanInterruptedException) e).getPath()));
                throw (ScanInterruptedException) e;
            }
            log.error("远端扫描目录失败: provider={}, path={}", provider.getName(), directoryPath, e);
            throw new RuntimeException("远端扫描目录失败: " + (e.getMessage() == null ? directoryPath : e.getMessage()), e);
        } finally {
            lastScanEnd = LocalDateTime.now();
            if (activeScanCount.decrementAndGet() <= 0) {
                isScanning.set(false);
                if (scanFailure != null) {
                    notifyScanFailed(scanFailure);
                } else {
                    notifyScanCompleted();
                }
            }
        }
    }

    private StorageProvider resolveCurrentStorageProvider() {
        Long storageProviderId = currentStorageProviderId.get();
        if (storageProviderId == null) {
            return null;
        }
        return storageProviderRepository.findById(storageProviderId).orElse(null);
    }

    private Path resolveRemoteScanRoot(String directoryPath, StorageProvider provider) {
        Path base = resolveRemoteScopedRoot(provider);
        if (directoryPath == null || directoryPath.isBlank()) {
            return base;
        }
        Path candidate = Paths.get(directoryPath.trim()).normalize();
        if (!candidate.isAbsolute()) {
            String clean = directoryPath.startsWith("./") ? directoryPath.substring(2) : directoryPath;
            Path relative = Paths.get(clean).normalize();
            Long storageUserId = currentStorageUserId.get();
            if (storageUserId != null) {
                relative = userPathService.stripLeadingUserSegment(relative, storageUserId);
            }
            candidate = base.resolve(relative).normalize();
        }
        if (!candidate.startsWith(base)) {
            throw new IllegalArgumentException("远端扫描路径超出当前用户存储范围");
        }
        return candidate;
    }

    private Path resolveRemoteScopedRoot(StorageProvider provider) {
        String baseDirectory = provider.getBaseDirectory();
        if (baseDirectory == null || baseDirectory.isBlank()) {
            baseDirectory = provider.getBucketName();
        }
        Path base = (baseDirectory == null || baseDirectory.isBlank())
            ? Paths.get("/")
            : Paths.get(baseDirectory.startsWith("/") ? baseDirectory : "/" + baseDirectory).normalize();
        Long storageUserId = currentStorageUserId.get();
        if (storageUserId != null && systemConfigService.isMultiUserEnabled()) {
            return base.resolve(String.valueOf(storageUserId)).normalize();
        }
        return base;
    }

    private RemoteScanStats collectRemoteScanStats(StorageProvider provider, Path remoteRoot) throws Exception {
        RemoteScanStats stats = new RemoteScanStats();
        collectRemoteScanStats(provider, remoteRoot, stats);
        return stats;
    }

    private void collectRemoteScanStats(StorageProvider provider, Path remotePath, RemoteScanStats stats) throws Exception {
        ensureScanCanContinue(remotePath.toString());
        stats.totalItems++;
        Map<String, Object> listing = storageUploadService.listDirectory(provider, buildStorageContextUser(), toProviderRelativeRemotePath(provider, remotePath));
        List<Map<String, Object>> directories = castDirectoryItems(listing.get("directories"));
        List<Map<String, Object>> files = castDirectoryItems(listing.get("files"));
        for (Map<String, Object> file : files) {
            String name = stringValue(file.get("name"));
            if (isSupportedRemoteImage(name)) {
                stats.totalItems++;
            }
        }
        for (Map<String, Object> directory : directories) {
            String name = stringValue(directory.get("name"));
            if (name == null || ".thumbnails".equals(name)) {
                continue;
            }
            collectRemoteScanStats(provider, remotePath.resolve(name).normalize(), stats);
        }
    }

    private void processRemoteDirectory(StorageProvider provider, Path remoteDirectory, boolean force) throws Exception {
        if (isShuttingDown.get()) {
            return;
        }
        ensureScanCanContinue(remoteDirectory.toString());
        if (shouldSkipForResume(remoteDirectory.toString(), true)) {
            return;
        }
        String directoryKey = remoteDirectory.normalize().toString() + "/";
        if (!processedFiles.add(directoryKey)) {
            return;
        }

        int directoryCurrent = scanCurrent.incrementAndGet();
        notifyScanProgress(remoteDirectory.toString(), "DIRECTORY", directoryCurrent, scanTotal.get());

        Album album = findOrCreateRemoteAlbum(provider, remoteDirectory);
        Map<String, Object> listing = storageUploadService.listDirectory(provider, buildStorageContextUser(), toProviderRelativeRemotePath(provider, remoteDirectory));
        List<Map<String, Object>> files = castDirectoryItems(listing.get("files"));
        int photoCount = 0;
        for (Map<String, Object> file : files) {
            String name = stringValue(file.get("name"));
            if (!isSupportedRemoteImage(name)) {
                continue;
            }
            photoCount++;
            processRemotePhoto(provider, remoteDirectory.resolve(name).normalize(), album, file, force);
        }
        album.setPhotoCount(photoCount);
        albumRepository.save(album);

        List<Map<String, Object>> directories = castDirectoryItems(listing.get("directories"));
        for (Map<String, Object> directory : directories) {
            String name = stringValue(directory.get("name"));
            if (name == null || ".thumbnails".equals(name)) {
                continue;
            }
            processRemoteDirectory(provider, remoteDirectory.resolve(name).normalize(), force);
        }
    }

    private Album findOrCreateRemoteAlbum(StorageProvider provider, Path remoteDirectory) {
        String albumPath = remoteDirectory.normalize().toString();
        Long userId = resolveCurrentScanUserId();
        String storedAlbumPath = userPathService.buildStoragePathReference(
            provider.getId(),
            userId,
            toProviderRelativeRemotePath(provider, remoteDirectory).toString()
        );
        String albumPathHash = calculateSha256(storedAlbumPath);
        return albumRepository.findByPathHash(albumPathHash)
            .orElseGet(() -> {
                Album album = new Album();
                album.setName(remoteDirectory.getFileName() != null ? remoteDirectory.getFileName().toString() : albumPath);
                album.setPath(storedAlbumPath);
                album.setPathHash(albumPathHash);
                album.setUserId(userId);
                LocalDateTime albumNameDate = parseDateFromAlbumPath(storedAlbumPath);
                if (albumNameDate == null && remoteDirectory.getFileName() != null) {
                    albumNameDate = parseDateFromFolderName(remoteDirectory.getFileName().toString());
                }
                album.setAlbumNameDate(albumNameDate);
                album.setPhotoCount(0);
                return albumRepository.save(album);
            });
    }

    private void processRemotePhoto(StorageProvider provider,
                                    Path remoteFilePath,
                                    Album album,
                                    Map<String, Object> remoteFile,
                                    boolean force) {
        ensureScanCanContinue(remoteFilePath.toString());
        if (shouldSkipForResume(remoteFilePath.toString(), false)) {
            return;
        }

        String traversalPath = remoteFilePath.normalize().toString();
        if (!processedFiles.add(traversalPath)) {
            return;
        }

        int currentCount = scanCurrent.incrementAndGet();
        notifyScanProgress(traversalPath, "FILE", currentCount, scanTotal.get());

        String storedPath = userPathService.buildStoragePathReference(
            provider.getId(),
            album.getUserId(),
            toProviderRelativeRemotePath(provider, remoteFilePath).toString()
        );
        String pathHash = calculateSha256(storedPath);
        Photo photo = photoRepository.findByPathHash(pathHash).orElseGet(Photo::new);
        if (!force && photo.getId() != null && !photo.needsContinuation(false)) {
            return;
        }
        try {
            File cachedFile = prepareRemoteLocalCacheFile(provider, remoteFilePath, remoteFile, album.getUserId());
            processRemotePhotoWithLocalCache(photo, album, remoteFile, cachedFile, storedPath, pathHash, force);
        } catch (Exception e) {
            String detail = sanitizeVisibleMessage(e.getMessage());
            photo.setAlbumId(album.getId());
            photo.setUserId(album.getUserId());
            photo.setFilename(stringValue(remoteFile.get("name")));
            photo.setOriginalPath(storedPath);
            photo.setPathHash(pathHash);
            photo.setFileSize(longValue(remoteFile.get("size")));
            photo.setFormat(FilenameUtils.getExtension(photo.getFilename()).toLowerCase(Locale.ROOT));
            photo.markProcessingFailed("远端缓存处理失败: " + detail);
            photoRepository.save(photo);
            notifyScanFailure(traversalPath, "FILE", detail, currentCount, scanTotal.get());
        }
    }

    private void processRemotePhotoWithLocalCache(Photo photo,
                                                  Album album,
                                                  Map<String, Object> remoteFile,
                                                  File cachedFile,
                                                  String storedPath,
                                                  String pathHash,
                                                  boolean force) throws IOException {
        String contentHash = calculateSha256(cachedFile);
        ProcessingStatus currentStatus = photo.getProcessingStatus();
        boolean needsReprocessing = photo.getId() == null || force || currentStatus == null
            || currentStatus == ProcessingStatus.PENDING
            || currentStatus == ProcessingStatus.FAILED
            || photo.getExifData() == null
            || photo.getExifData().isBlank()
            || photo.getThumbnailPath() == null
            || photo.getThumbnailPath().isBlank();

        photo.setAlbumId(album.getId());
        photo.setUserId(album.getUserId());
        photo.setFilename(stringValue(remoteFile.get("name")));
        photo.setOriginalPath(storedPath);
        photo.setPathHash(pathHash);
        photo.setContentHash(contentHash);
        photo.setFileSize(longValue(remoteFile.get("size")) != null ? longValue(remoteFile.get("size")) : cachedFile.length());
        photo.setFormat(FilenameUtils.getExtension(photo.getFilename()).toLowerCase(Locale.ROOT));

        if (!needsReprocessing) {
            photoRepository.save(photo);
            return;
        }

        if (currentStatus == null || currentStatus == ProcessingStatus.PENDING || currentStatus == ProcessingStatus.FAILED || force) {
            photo.setProcessingStatus(ProcessingStatus.BASIC_INFO_DONE);
        }
        photoRepository.save(photo);

        if (photo.getProcessingStatus() == ProcessingStatus.BASIC_INFO_DONE || force) {
            try {
                extractExifData(cachedFile, photo);
                photo.setProcessingStatus(ProcessingStatus.BASIC_INFO_DONE);
                photoRepository.save(photo);
            } catch (Exception e) {
                photo.markProcessingFailed("远端 EXIF 提取失败: " + sanitizeVisibleMessage(e.getMessage()));
                photoRepository.save(photo);
                throw e;
            }
        }

        if (photo.getProcessingStatus() == ProcessingStatus.BASIC_INFO_DONE
            || photo.getProcessingStatus() == ProcessingStatus.THUMBNAILS_DONE
            || force) {
            try {
                runWithinDerivativeStorageContext(album.getUserId(), () -> {
                    try {
                        generateThumbnailAndWebP(cachedFile, photo);
                        regenerateMissingThumbnails(cachedFile, photo);
                    } catch (IOException ioException) {
                        throw new RuntimeException(ioException);
                    }
                });
                photo.setProcessingStatus(ProcessingStatus.THUMBNAILS_DONE);
                photoRepository.save(photo);
            } catch (Exception e) {
                Throwable cause = e instanceof RuntimeException && e.getCause() != null ? e.getCause() : e;
                photo.markProcessingFailed("远端缩略图生成失败: " + sanitizeVisibleMessage(cause.getMessage()));
                photoRepository.save(photo);
                throw cause instanceof IOException ? (IOException) cause : new IOException(cause);
            }
        }

        if (photo.getProcessingStatus() == ProcessingStatus.THUMBNAILS_DONE
            || photo.getProcessingStatus() == ProcessingStatus.ANALYSIS_DONE
            || force) {
            try {
                colorAnalysisService.analyzeColor(cachedFile, photo);
                calculateQualityScore(photo);
                photo.setProcessingStatus(ProcessingStatus.ANALYSIS_DONE);
                photoRepository.save(photo);
            } catch (Exception e) {
                photo.markProcessingFailed("远端基础分析失败: " + sanitizeVisibleMessage(e.getMessage()));
                photoRepository.save(photo);
                throw new IOException(e);
            }
        }

        List<Face> faces = new ArrayList<>();
        if (photo.getProcessingStatus() == ProcessingStatus.ANALYSIS_DONE
            || photo.getProcessingStatus() == ProcessingStatus.FACES_DONE
            || force) {
            try {
                faces = processFaces(cachedFile, photo, force, false);
                photo.setProcessingStatus(ProcessingStatus.FACES_DONE);
                photoRepository.save(photo);
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                if (errorMsg.contains("ONNX") || errorMsg.contains("onnxruntime")
                    || errorMsg.contains("NoClassDefFound") || errorMsg.contains("UnsatisfiedLinkError")) {
                    log.warn("远端人脸检测因ONNX Runtime问题跳过，继续处理其他步骤: {}", errorMsg);
                    photo.setProcessingStatus(ProcessingStatus.FACES_DONE);
                    photoRepository.save(photo);
                } else {
                    photo.markProcessingFailed("远端人脸检测失败: " + sanitizeVisibleMessage(e.getMessage()));
                    photoRepository.save(photo);
                    throw new IOException(e);
                }
            }
        } else if (photo.getProcessingStatus().ordinal() >= ProcessingStatus.FACES_DONE.ordinal()) {
            faces = faceService.getFacesByPhoto(photo.getId());
        }

        if (photo.getProcessingStatus() == ProcessingStatus.FACES_DONE
            || photo.getProcessingStatus() == ProcessingStatus.SUBJECT_DONE
            || force) {
            try {
                subjectDetectionService.detectSubject(cachedFile, photo, faces);
                photo.setProcessingStatus(ProcessingStatus.SUBJECT_DONE);
                photoRepository.save(photo);
            } catch (Exception e) {
                photo.markProcessingFailed("远端主体检测失败: " + sanitizeVisibleMessage(e.getMessage()));
                photoRepository.save(photo);
                throw new IOException(e);
            }
        }

        if (photo.getProcessingStatus() == ProcessingStatus.SUBJECT_DONE
            || photo.getProcessingStatus() == ProcessingStatus.TAGS_DONE
            || force) {
            try {
                processTags(photo, album, cachedFile, faces.size(), force);
                photo.setProcessingStatus(ProcessingStatus.TAGS_DONE);
                photoRepository.save(photo);
            } catch (Exception e) {
                photo.markProcessingFailed("远端标签处理失败: " + sanitizeVisibleMessage(e.getMessage()));
                photoRepository.save(photo);
                throw new IOException(e);
            }
        }

        if (photo.getProcessingStatus() == ProcessingStatus.TAGS_DONE
            || photo.getProcessingStatus() == ProcessingStatus.AI_SCORING_DONE
            || force) {
            try {
                aiScoringService.scorePhoto(photo);
                photo.setProcessingStatus(ProcessingStatus.AI_SCORING_DONE);
                photoRepository.save(photo);
            } catch (Exception e) {
                log.warn("远端 AI 评分失败，但继续处理: {}", e.getMessage());
                photo.setProcessingStatus(ProcessingStatus.AI_SCORING_DONE);
                photoRepository.save(photo);
            }
        }

        if (autoBackgroundRemoval && backgroundRemovalService.isModelAvailable()
            && (photo.getBackgroundRemovedPath() == null || photo.getBackgroundRemovedPath().isEmpty())) {
            try {
                runWithinDerivativeStorageContext(album.getUserId(), () -> {
                    try {
                        String thumbnailDir = new File(cachedFile.getParent(), ".thumbnails").getAbsolutePath();
                        File outputFile = new File(thumbnailDir, "bg_removed_" + photo.getId() + ".png");
                        BufferedImage img = ImageIO.read(cachedFile);
                        if (img == null) {
                            return;
                        }
                        final int imgWidth = img.getWidth();
                        final int imgHeight = img.getHeight();
                        List<Rectangle> faceRegions = null;
                        try {
                            List<Face> existingFaces = faceService.getFacesByPhoto(photo.getId());
                            if (existingFaces != null && !existingFaces.isEmpty()) {
                                faceRegions = existingFaces.stream()
                                    .map(face -> new Rectangle(
                                        (int) (face.getX() * imgWidth),
                                        (int) (face.getY() * imgHeight),
                                        (int) (face.getWidth() * imgWidth),
                                        (int) (face.getHeight() * imgHeight)))
                                    .collect(Collectors.toList());
                            }
                        } catch (Exception e) {
                            log.debug("获取远端缓存人脸信息失败，跳过人脸优化: {}", e.getMessage());
                        }
                        if (backgroundRemovalService.removeBackground(cachedFile, outputFile, faceRegions)) {
                            photo.setBackgroundRemovedPath(toStoredManagedPath(outputFile.getAbsolutePath(), photo.getUserId()));
                            photoRepository.save(photo);
                        }
                    } catch (IOException ioException) {
                        throw new RuntimeException(ioException);
                    }
                });
            } catch (Exception e) {
                Throwable cause = e instanceof RuntimeException && e.getCause() != null ? e.getCause() : e;
                log.warn("远端自动背景移除失败: {} - {}", photo.getId(), sanitizeVisibleMessage(cause.getMessage()));
            }
        }

        if (photo.getProcessingStatus() == ProcessingStatus.AI_SCORING_DONE) {
            photo.setProcessingStatus(ProcessingStatus.COMPLETED);
            photoRepository.save(photo);
        }
    }

    private File prepareRemoteLocalCacheFile(StorageProvider provider,
                                             Path remoteFilePath,
                                             Map<String, Object> remoteFile,
                                             Long userId) throws Exception {
        Path cacheRoot = buildRemoteCacheRoot(provider, userId);
        Path relativeRemotePath = toProviderRelativeRemotePath(provider, remoteFilePath);
        Path localCachePath = cacheRoot.resolve(relativeRemotePath).normalize();
        Files.createDirectories(localCachePath.getParent());
        Long remoteSize = longValue(remoteFile.get("size"));
        Long remoteLastModified = longValue(remoteFile.get("lastModified"));
        boolean needsDownload = !Files.exists(localCachePath)
            || !Files.isRegularFile(localCachePath)
            || (remoteSize != null && Files.size(localCachePath) != remoteSize)
            || hasRemoteFileChanged(localCachePath, remoteLastModified);
        if (needsDownload) {
            StorageUploadService.DownloadedFile downloadedFile = storageUploadService.downloadFile(
                provider,
                buildStorageContextUser(),
                relativeRemotePath
            );
            Files.write(localCachePath, downloadedFile.getBytes());
            if (remoteLastModified != null && remoteLastModified > 0) {
                Files.setLastModifiedTime(localCachePath, java.nio.file.attribute.FileTime.fromMillis(remoteLastModified));
            }
        }
        return localCachePath.toFile();
    }

    private boolean hasRemoteFileChanged(Path localCachePath, Long remoteLastModified) throws IOException {
        if (remoteLastModified == null || remoteLastModified <= 0 || !Files.exists(localCachePath) || !Files.isRegularFile(localCachePath)) {
            return false;
        }
        long localLastModified = Files.getLastModifiedTime(localCachePath).toMillis();
        return Math.abs(localLastModified - remoteLastModified) > 1000L;
    }

    private Path buildRemoteCacheRoot(StorageProvider provider, Long userId) {
        Path scopedRoot = userId == null ? resolveBasePath() : userPathService.getOwnedPhotoRoot(userId);
        return scopedRoot.resolve(".remote-cache")
            .resolve(provider.getId() == null ? "unknown" : String.valueOf(provider.getId()))
            .normalize();
    }

    private void runWithinDerivativeStorageContext(Long userId, Runnable runnable) {
        StorageProvider localProvider = resolvePreferredLocalDerivativeProvider();
        if (localProvider == null || localProvider.getId() == null) {
            log.warn("未找到本地存储提供者，跳过远端文件派生资源落盘");
            runnable.run();
            return;
        }
        runWithStorageContext(localProvider.getId(), userId, runnable);
    }

    private StorageProvider resolvePreferredLocalDerivativeProvider() {
        return storageProviderRepository.findByTypeOrderByPriorityAscIdAsc(StorageType.LOCAL).stream()
            .findFirst()
            .orElse(null);
    }

    private Path toProviderRelativeRemotePath(StorageProvider provider, Path remotePath) {
        Path scopedRoot = resolveRemoteScopedRoot(provider);
        Path normalizedRemotePath = remotePath.normalize();
        if (!normalizedRemotePath.startsWith(scopedRoot)) {
            throw new IllegalArgumentException("远端路径超出当前存储根目录");
        }
        return scopedRoot.relativize(normalizedRemotePath);
    }

    private Long resolveManagedUserId(String path, Long fallbackUserId) {
        if (fallbackUserId != null) {
            return fallbackUserId;
        }
        Long currentUserId = resolveCurrentScanUserId();
        if (currentUserId != null) {
            return currentUserId;
        }
        return userPathService.extractUserIdFromPath(path);
    }

    private List<String> buildAlbumPathLookupCandidates(Path albumPath, Long userId) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (albumPath == null) {
            return new ArrayList<>();
        }
        String absolutePath = albumPath.toAbsolutePath().normalize().toString();
        candidates.add(absolutePath);
        String storedPath = toStoredManagedPath(absolutePath, userId);
        if (storedPath != null && !storedPath.isBlank()) {
            candidates.add(storedPath);
        }
        return new ArrayList<>(candidates);
    }

    private Optional<Album> findAlbumByManagedPath(Path albumPath, Long userId) {
        for (String candidatePath : buildAlbumPathLookupCandidates(albumPath, userId)) {
            String candidateHash = calculateSha256(candidatePath);
            if (candidateHash != null) {
                Optional<Album> byHash = albumRepository.findByPathHash(candidateHash);
                if (byHash.isPresent()) {
                    return byHash;
                }
            }
            Optional<Album> byPath = albumRepository.findByPath(candidatePath);
            if (byPath.isPresent()) {
                return byPath;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castDirectoryItems(Object value) {
        if (!(value instanceof List)) {
            return List.of();
        }
        return ((List<?>) value).stream()
            .filter(Map.class::isInstance)
            .map(item -> (Map<String, Object>) item)
            .collect(Collectors.toList());
    }

    private boolean isSupportedRemoteImage(String name) {
        if (name == null || name.isBlank() || name.contains("_thumb")) {
            return false;
        }
        String extension = FilenameUtils.getExtension(name).toLowerCase(Locale.ROOT);
        if (extension.isBlank()) {
            return false;
        }
        return Arrays.stream(supportedFormats.split(","))
            .map(String::trim)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(extension::equals);
    }

    public int estimateScannableFileCount(String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            return 0;
        }
        Path directory;
        try {
            directory = Paths.get(rootPath).normalize();
        } catch (Exception e) {
            return 0;
        }
        if (!Files.exists(directory)) {
            return 0;
        }
        Set<String> supportedSet = Arrays.stream(supportedFormats.split(","))
            .map(String::trim)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        try (Stream<Path> paths = Files.walk(directory)) {
            return (int) paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.getFileName() != null ? path.getFileName().toString() : "";
                    if (fileName.contains("_thumb")) {
                        return false;
                    }
                    String extension = FilenameUtils.getExtension(fileName).toLowerCase(Locale.ROOT);
                    return !extension.isBlank() && supportedSet.contains(extension);
                })
                .count();
        } catch (Exception e) {
            log.debug("估算扫描文件数量失败: {}", rootPath, e);
            return 0;
        }
    }

    private Long resolveCurrentScanUserId() {
        Long userId = currentStorageUserId.get();
        if (userId != null) {
            return userId;
        }
        return null;
    }

    private UserAccount buildStorageContextUser() {
        Long userId = resolveCurrentScanUserId();
        if (userId == null) {
            return null;
        }
        UserAccount user = new UserAccount();
        user.setId(userId);
        return user;
    }

    private int countPhotosForCurrentScope() {
        Long userId = resolveCurrentScanUserId();
        long total = userId == null ? photoRepository.count() : photoRepository.countByUserId(userId);
        return Math.toIntExact(total);
    }

    private int countAlbumsForCurrentScope() {
        Long userId = resolveCurrentScanUserId();
        long total = userId == null ? albumRepository.count() : albumRepository.countByUserId(userId);
        return Math.toIntExact(total);
    }

    private int countPhotosWithBackgroundForCurrentScope() {
        Long userId = resolveCurrentScanUserId();
        long total = userId == null
            ? photoRepository.countByBackgroundRemovedPathPresent()
            : photoRepository.countByUserIdAndBackgroundRemovedPathPresent(userId);
        return Math.toIntExact(total);
    }

    private void forEachPhotoInCurrentScope(Consumer<Photo> consumer) {
        forEachPhotoPageInCurrentScope(consumer, 200);
    }

    private void forEachPhotoPageInCurrentScope(Consumer<Photo> consumer, int pageSize) {
        Long userId = resolveCurrentScanUserId();
        int pageNumber = 0;
        Page<Photo> page;
        do {
            PageRequest request = PageRequest.of(pageNumber, pageSize);
            page = userId == null ? photoRepository.findAll(request) : photoRepository.findByUserId(userId, request);
            page.forEach(consumer);
            pageNumber++;
        } while (page.hasNext());
    }

    private void forEachPhotoWithBackgroundInCurrentScope(Consumer<Photo> consumer) {
        Long userId = resolveCurrentScanUserId();
        int pageNumber = 0;
        Page<Photo> page;
        do {
            PageRequest request = PageRequest.of(pageNumber, 200);
            page = userId == null
                ? photoRepository.findByBackgroundRemovedPathPresent(request)
                : photoRepository.findByUserIdAndBackgroundRemovedPathPresent(userId, request);
            page.forEach(consumer);
            pageNumber++;
        } while (page.hasNext());
    }

    private void forEachAlbumInCurrentScope(Consumer<Album> consumer) {
        Long userId = resolveCurrentScanUserId();
        int pageNumber = 0;
        Page<Album> page;
        do {
            PageRequest request = PageRequest.of(pageNumber, 200);
            page = userId == null ? albumRepository.findAll(request) : albumRepository.findByUserId(userId, request);
            page.forEach(consumer);
            pageNumber++;
        } while (page.hasNext());
    }

    private Set<String> getAllDatabasePhotoPathsForCurrentScope() {
        Set<String> paths = new HashSet<>();
        Long userId = resolveCurrentScanUserId();
        int pageNumber = 0;
        Page<String> page;
        do {
            PageRequest request = PageRequest.of(pageNumber, 500);
            page = userId == null ? photoRepository.findAllOriginalPaths(request) : photoRepository.findOriginalPathsByUserId(userId, request);
            page.forEach(paths::add);
            pageNumber++;
        } while (page.hasNext());
        return paths;
    }

    private Path resolveCurrentFilesystemScopeRoot() {
        Long userId = resolveCurrentScanUserId();
        Long storageProviderId = currentStorageProviderId.get();
        Path providerBasePath = currentStorageProviderBasePath.get();
        if (storageProviderId != null && providerBasePath == null) {
            return null;
        }
        Path base = providerBasePath != null ? providerBasePath : resolveBasePath();
        if (userId != null) {
            return base.resolve(String.valueOf(userId)).normalize();
        }
        return base;
    }

    private Path resolveRequestedFilesystemPath(String directoryPath) {
        Path scopedRoot = resolveCurrentFilesystemScopeRoot();
        if (scopedRoot == null) {
            throw new IllegalStateException("当前存储上下文不是本地文件系统");
        }
        if (isDefaultFilesystemScanRequest(directoryPath)) {
            return scopedRoot;
        }
        Path requested = Paths.get(directoryPath.trim());
        if (requested.isAbsolute()) {
            return requested.toAbsolutePath().normalize();
        }
        String clean = normalizeRelativeFilesystemRequest(directoryPath);
        return scopedRoot.resolve(clean).toAbsolutePath().normalize();
    }

    private boolean isDefaultFilesystemScanRequest(String directoryPath) {
        if (directoryPath == null || directoryPath.isBlank()) {
            return true;
        }
        return matchesFilesystemPath(directoryPath, resolveBasePath())
            || matchesFilesystemPath(directoryPath, resolveCurrentFilesystemScopeRoot())
            || (basePath != null && directoryPath.trim().equals(basePath.trim()));
    }

    private String normalizeRelativeFilesystemRequest(String directoryPath) {
        String clean = directoryPath.startsWith("./") ? directoryPath.substring(2) : directoryPath;
        Path relative = Paths.get(clean.trim()).normalize();
        Long userId = resolveCurrentScanUserId();
        if (userId != null) {
            relative = userPathService.stripLeadingUserSegment(relative, userId);
        }
        return relative.toString();
    }

    private boolean matchesFilesystemPath(String rawPath, Path expectedPath) {
        if (expectedPath == null || rawPath == null || rawPath.isBlank()) {
            return false;
        }
        try {
            return Paths.get(rawPath.trim()).toAbsolutePath().normalize().equals(expectedPath.toAbsolutePath().normalize());
        } catch (Exception e) {
            return false;
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class RemoteScanStats {
        private int totalItems;
    }

    /**
     * 处理相册目录
     */
    private void processAlbumDirectory(Path albumPath, boolean force) {
        processAlbumDirectory(albumPath, force, null);
    }

    /**
     * 处理相册目录（带层级控制）
     * @param albumPath 相册路径
     * @param force 是否强制扫描
     * @param parentAlbum 如果当前目录超过最大层级，则使用父相册
     */
    private void processAlbumDirectory(Path albumPath, boolean force, Album parentAlbum) {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            log.debug("应用正在关闭，跳过处理: {}", toRelativePath(albumPath.toString()));
            return;
        }
        ensureScanCanContinue(albumPath.toString());
        if (shouldSkipForResume(albumPath.toString(), true)) {
            return;
        }
        
        try {
            // 跳过.thumbnails目录
            if (albumPath.getFileName().toString().equals(".thumbnails")) {
                return;
            }

            // 计算当前目录相对于base-path的层级深度
            int depth = calculateAlbumDepth(albumPath);
            int maxDepth = systemConfigService.getMaxAlbumDepth();

            // 如果目录深度超过最大层级，不创建相册，将所有图片归属到父相册
            if (depth > maxDepth) {
                // 如果 parentAlbum 为 null（从 Files.walk 调用），向上查找最近的父目录相册
                Album effectiveParent = parentAlbum;
                if (effectiveParent == null) {
                    Path ancestor = albumPath.getParent();
                    Path basePth = resolveCurrentFilesystemScopeRoot();
                    while (ancestor != null && ancestor.startsWith(basePth)) {
                        Long ancestorUserId = resolveManagedUserId(ancestor.toString(), null);
                        Optional<Album> ancestorAlbum = findAlbumByManagedPath(ancestor, ancestorUserId);
                        if (ancestorAlbum.isPresent()) {
                            effectiveParent = ancestorAlbum.get();
                            log.info("目录 {} 超过最大层级，找到祖先相册: {}", toRelativePath(albumPath.toString()), effectiveParent.getName());
                            break;
                        }
                        ancestor = ancestor.getParent();
                    }
                    if (effectiveParent == null) {
                        log.warn("目录 {} 超过最大层级且找不到父相册，跳过处理", toRelativePath(albumPath.toString()));
                    }
                }

                // 查找是否已存在该目录的相册记录，如果存在则删除
                Long scopedUserId = resolveManagedUserId(albumPath.toString(), null);
                Optional<Album> existingAlbum = findAlbumByManagedPath(albumPath, scopedUserId);

                if (existingAlbum.isPresent()) {
                    Album albumToDelete = existingAlbum.get();
                    String relativePath = toRelativePath(albumToDelete.getPath());
                    log.info("删除超出层级的相册 {} (深度: {}, 最大深度: {})", relativePath, depth, maxDepth);

                    // 将该相册的照片移动到父相册（如果有父相册）
                    if (effectiveParent != null) {
                        // 分页获取该相册的所有照片并移动到父相册
                        int pageSize = 1000;
                        int pageNumber = 0;
                        int totalMoved = 0;

                        while (true) {
                            Page<Photo> photoPage = photoRepository.findByAlbumId(albumToDelete.getId(), PageRequest.of(pageNumber, pageSize));
                            List<Photo> photos = photoPage.getContent();

                            if (photos.isEmpty()) {
                                break;
                            }

                            for (Photo photo : photos) {
                                photo.setAlbumId(effectiveParent.getId());
                                photoRepository.save(photo);
                                totalMoved++;
                            }

                            pageNumber++;
                            if (pageNumber >= photoPage.getTotalPages()) {
                                break;
                            }
                        }

                        String parentRelativePath = toRelativePath(effectiveParent.getPath());
                        log.info("已将 {} 张照片移动到父相册 {}", totalMoved, parentRelativePath);
                    }

                    // 删除相册
                    albumRepository.delete(albumToDelete);
                }

                // 将所有图片归属到父相册中，并递归处理子目录
                if (effectiveParent != null) {
                    log.debug("目录 {} 超过最大相册层级 {}，将其图片归属到父相册 {}", toRelativePath(albumPath.toString()), maxDepth, effectiveParent.getName());
                    processAlbumImagesRecursively(albumPath, effectiveParent, force);
                }
                return;
            }
            
            String albumPathStr = albumPath.toAbsolutePath().normalize().toString();
            Long scopedUserId = resolveManagedUserId(albumPathStr, null);
            String storedAlbumPath = toStoredManagedPath(albumPathStr, scopedUserId);
            String albumPathHash = calculateSha256(storedAlbumPath != null ? storedAlbumPath : albumPathStr);
            Album album = findAlbumByManagedPath(albumPath, scopedUserId)
                .orElseGet(() -> {
                    // 再次检查是否关闭
                    if (isShuttingDown.get()) {
                        throw new IllegalStateException("应用正在关闭");
                    }
                    Album newAlbum = new Album();
                    newAlbum.setName(albumPath.getFileName().toString());
                    newAlbum.setPath(storedAlbumPath);
                    newAlbum.setPathHash(albumPathHash);
                    newAlbum.setUserId(scopedUserId);
                    // 从路径中解析相册名日期（用于排序）- 优先当前文件夹名，如果没有则向上查找父目录
                    LocalDateTime albumNameDate = parseDateFromAlbumPath(albumPathStr);
                    if (albumNameDate == null) {
                        // 兼容旧逻辑：如果路径解析失败，尝试只用文件夹名
                        albumNameDate = parseDateFromFolderName(albumPath.getFileName().toString());
                    }
                    newAlbum.setAlbumNameDate(albumNameDate);
                    return albumRepository.save(newAlbum);
                });

            // 扫描目录中的图片文件
            List<File> imageFiles = findImageFiles(albumPath.toFile());
            String albumRelativePath = toRelativePath(album.getPath());

            // 检查是否已经处理过这个相册目录
            String albumKey = albumPath.toString() + "/";
            log.info("处理相册目录: {}", albumRelativePath);

            if (processedFiles.contains(albumKey)) {
                log.warn("相册目录重复处理，跳过: {} (key: {})", albumRelativePath, albumKey);
                return;
            }
            processedFiles.add(albumKey);

            log.info("相册 {}: {} 张图片", albumRelativePath, imageFiles.size());

            int processedCount = 0;
            int skippedCount = 0;
            for (File imageFile : imageFiles) {
                try {
                    ensureScanCanContinue(imageFile.getAbsolutePath());
                    if (shouldSkipForResume(imageFile.getAbsolutePath(), false)) {
                        continue;
                    }
                    processPhotoFile(imageFile, album, force);
                    processedCount++;
                } catch (Exception e) {
                    if (e instanceof ScanInterruptedException) {
                        throw e;
                    }
                    log.warn("处理文件失败，跳过: {} - {}", imageFile.getName(), e.getMessage());
                    skippedCount++;
                    String relPath = toRelativePath(imageFile.getAbsolutePath());
                    String shortMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    // 截断过长的错误信息
                    String detail = shortMsg.length() > 200 ? shortMsg.substring(0, 200) + "…" : shortMsg;
                    skippedFileRecords.add(buildSkippedFileRecord(imageFile.getAbsolutePath(), "处理失败", detail, imageFile.length()));
                    notifyScanFailure(imageFile.getAbsolutePath(), "FILE", detail, scanCurrent.get(), scanTotal.get());
                }
            }

            if (skippedCount > 0) {
                log.warn("相册 {} 处理完成: 成功 {}, 跳过 {}", albumRelativePath, processedCount, skippedCount);
            }

            // 注意：子目录的处理由scanDirectoryInternal的Files.walk统一管理，这里不再递归处理

            // 更新相册照片数量
            album.setPhotoCount(photoRepository.countByAlbumId(album.getId()).intValue());

            // 如果相册名日期为空，或者有新增照片（processedCount > 0），则尝试从照片中获取日期
            // 这样只在需要时（如新相册或添加了新照片）才计算日期，避免每次扫描都计算
            if (album.getAlbumNameDate() == null || processedCount > 0) {
                LocalDateTime calculatedDate = calculateAlbumDateFromPhotos(album.getId());
                if (calculatedDate != null) {
                    // 如果相册原来没有日期，或者新计算出的日期比现有日期更晚，则更新
                    if (album.getAlbumNameDate() == null || calculatedDate.isAfter(album.getAlbumNameDate())) {
                        album.setAlbumNameDate(calculatedDate);
                    }
                }
            }

            albumRepository.save(album);

            // 增量分析相册氛围（如果需要）
            try {
                if (atmosphereAnalysisService.needsAtmosphereUpdate(album.getId())) {
                    atmosphereAnalysisService.analyzeAlbumAtmosphere(album.getId());
                }
                if (atmosphereEffectsService.needsEffectsUpdate(album.getId())) {
                    atmosphereEffectsService.analyzeAlbumEffects(album.getId());
                }
            } catch (Exception e) {
                log.warn("相册 {} 氛围分析失败: {}", album.getName(), e.getMessage());
            }

            notifyScanProgress(albumPath.toString(), "DIRECTORY", scanCurrent.get(), scanTotal.get());

        } catch (IllegalStateException e) {
            // 应用关闭时的异常，静默处理
            if (e.getMessage() != null && e.getMessage().contains("关闭")) {
                log.debug("应用关闭，停止处理相册: {}", toRelativePath(albumPath.toString()));
            } else {
                log.warn("处理相册目录失败（应用状态异常）: {}", toRelativePath(albumPath.toString()), e);
            }
        } catch (org.springframework.context.ApplicationContextException e) {
            // Spring上下文异常，应用可能正在关闭
            log.debug("应用上下文异常，停止处理相册: {}", toRelativePath(albumPath.toString()));
        } catch (ScanInterruptedException e) {
            throw e;
        } catch (Exception e) {
            // 检查是否是应用关闭相关的异常
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMsg.contains("closed") || errorMsg.contains("shutdown") ||
                errorMsg.contains("context") && errorMsg.contains("close")) {
                log.debug("应用关闭，停止处理相册: {}", toRelativePath(albumPath.toString()));
            } else {
            log.error("处理相册目录失败: {}", toRelativePath(albumPath.toString()), e);
            }
        }
    }

    /**
     * 根据原始路径查找照片，处理可能存在的重复记录情况
     */
    private Optional<Photo> findPhotoByOriginalPath(String filePath) {
        for (String candidatePath : buildPhotoPathLookupCandidates(filePath)) {
            try {
                Optional<Photo> matched = photoRepository.findByOriginalPath(candidatePath);
                if (matched.isPresent()) {
                    return matched;
                }
            } catch (Exception e) {
                List<Photo> photos = photoRepository.findAllByOriginalPath(candidatePath);
                if (!photos.isEmpty()) {
                    Photo latestPhoto = photos.stream()
                        .max((p1, p2) -> Long.compare(p1.getId(), p2.getId()))
                        .orElse(photos.get(0));
                    log.warn("发现 {} 条重复记录使用相同路径 {}, 选择最新的记录 ID={}",
                        photos.size(), candidatePath, latestPhoto.getId());
                    return Optional.of(latestPhoto);
                }
            }
        }
        return Optional.empty();
    }

    private List<String> buildPhotoPathLookupCandidates(String filePath) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (filePath == null || filePath.isBlank()) {
            return new ArrayList<>();
        }
        candidates.add(filePath);
        Long userId = resolveManagedUserId(filePath, null);
        userPathService.tryBuildStoragePathReference(filePath, userId).ifPresent(candidates::add);
        return new ArrayList<>(candidates);
    }

    /**
     * 查找图片文件
     */
    private List<File> findImageFiles(File directory) {
        List<File> imageFiles = new ArrayList<>();
        Set<String> supportedSet = Arrays.stream(supportedFormats.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        // 跳过.thumbnails目录，避免处理缩略图
        if (directory.getName().equals(".thumbnails")) {
            return imageFiles;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    ensureScanCanContinue(file.getAbsolutePath());
                    if (shouldSkipForResume(file.getAbsolutePath(), file.isDirectory())) {
                        continue;
                    }
                    // 跳过.thumbnails目录
                    if (file.isDirectory() && file.getName().equals(".thumbnails")) {
                        continue;
                    }
                    if (file.isFile()) {
                        // 跳过缩略图文件（文件名包含_thumb）
                        if (file.getName().contains("_thumb")) {
                            continue;
                        }
                        String extension = FilenameUtils.getExtension(file.getName()).toLowerCase();
                        if (supportedSet.contains(extension)) {
                            // 额外检查文件是否可读，避免后续处理出错
                            if (file.exists() && file.canRead() && file.length() > 0) {
                                imageFiles.add(file);
                            } else {
                                // 判断具体原因
                                String reason, detail;
                                if (!file.exists()) {
                                    reason = "文件不存在";
                                    detail = "文件路径在扫描期间消失";
                                } else if (!file.canRead()) {
                                    reason = "无读取权限";
                                    detail = "文件存在但无法读取，可能是权限问题";
                                } else {
                                    reason = "文件为空";
                                    detail = "文件大小为 0 字节，无法作为有效图片处理";
                                }
                                log.debug("文件不可读或为空，跳过: {} ({})", file.getName(), reason);
                                // 预统计已将此文件计入 total，跳过时同样推进 current，保持进度一致
                                scanCurrent.incrementAndGet();
                                // 标记为已处理，避免补录差集时重复记录
                                processedFiles.add(file.getAbsolutePath());
                                // 记录跳过详情
                                String relPath = toRelativePath(file.getAbsolutePath());
                                skippedFileRecords.add(buildSkippedFileRecord(file.getAbsolutePath(), reason, detail, file.length()));
                                notifyScanSkip(file.getAbsolutePath(), file.isDirectory() ? "DIRECTORY" : "FILE", reason, detail, scanCurrent.get(), scanTotal.get());
                            }
                        }
                    }
                } catch (Exception e) {
                    if (e instanceof ScanInterruptedException) {
                        throw e;
                    }
                    log.warn("检查文件时出错，跳过: {} - {}", file.getName(), e.getMessage());
                }
            }
        } else {
            log.warn("无法读取目录内容: {}", toRelativePath(directory.getAbsolutePath()));
        }
        return imageFiles;
    }

    /**
     * 处理单张图片（支持断点续上）
     */
    @Transactional
    public void processPhotoFile(File imageFile, Album album, boolean force) {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            log.debug("应用关闭，跳过文件: {}", imageFile.getName());
            return;
        }
        ensureScanCanContinue(imageFile.getAbsolutePath());

        String filePath = imageFile.getAbsolutePath();
        String storedFilePath = toStoredManagedPath(filePath, album != null ? album.getUserId() : null);
        String pathHash = calculateSha256(storedFilePath != null ? storedFilePath : filePath);

        // 添加文件处理锁，防止同一文件被并发处理
        synchronized (filePath.intern()) {
            // 检查文件是否已经处理过，避免重复计数
            if (processedFiles.contains(filePath)) {
                log.debug("文件已处理过，跳过: {}", imageFile.getName());
                return;
            }

            // 无论后续走哪条路径，立即标记为已处理，确保补录差集逻辑不会误判为"未被扫描到"
            processedFiles.add(filePath);

            try {
                ensureScanCanContinue(filePath);
                // 跳过.thumbnails目录下的文件
                if (filePath.contains("/.thumbnails/") || filePath.contains("\\.thumbnails\\")) {
                    return;
                }

                // 跳过缩略图文件（文件名包含_thumb）
                if (imageFile.getName().contains("_thumb")) {
                return;
            }

            // 文件计数已在相册层面完成，这里不需要重复计数

            // 检查文件是否存在
            if (!imageFile.exists()) {
                log.warn("文件不存在，跳过处理: {}", toRelativePath(imageFile.getAbsolutePath()));
                return;
            }

            // 检查文件是否为支持的图片类型
            String extension = FilenameUtils.getExtension(imageFile.getName()).toLowerCase();
            Set<String> supportedSet = Arrays.stream(supportedFormats.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
            if (!supportedSet.contains(extension)) {
                log.warn("文件类型不支持，跳过处理: {} (支持的格式: {})", imageFile.getName(), supportedFormats);
                return;
            }

            // 计算内容哈希（SHA-256）
            String contentHash;
            try {
                contentHash = calculateSha256(imageFile);
            } catch (Exception e) {
                log.warn("无法计算文件内容哈希，跳过处理: {} - {}", imageFile.getName(), e.getMessage());
                return;
            }

            // 优先按内容哈希查找（为后续去重存储保留规范源），再按路径哈希，最后按原路径兜底
            Optional<Photo> photoByHash = contentHash == null ? Optional.empty() : photoRepository.findByContentHash(contentHash);
            Optional<Photo> photoByPathHash = pathHash == null ? Optional.empty() : photoRepository.findByPathHash(pathHash);
            if (photoByPathHash.isEmpty() && storedFilePath != null && !storedFilePath.equals(filePath)) {
                photoByPathHash = photoRepository.findByPathHash(calculateSha256(filePath));
            }
            Optional<Photo> resolvedPhotoByPathHash = photoByPathHash;

            // 处理可能存在重复originalPath的情况
            Optional<Photo> photoByPath = findPhotoByOriginalPath(filePath);

            Photo photo = resolvePhotoRecordForScan(photoByHash, resolvedPhotoByPathHash, photoByPath);

            boolean foundByContentHash = photoByHash.isPresent()
                && photo.getId() != null
                && resolvedPhotoByPathHash.isEmpty()
                && photoByPath.isEmpty()
                && photo.getCanonicalPhotoId() == null;
            boolean createdFromCanonicalPhoto = photo.getId() == null && photo.getCanonicalPhotoId() != null;

            // 所有到达这里的照片都算已遍历（无论是否需要处理）
            int currentCount = scanCurrent.incrementAndGet();
            notifyScanProgress(filePath, "FILE", currentCount, scanTotal.get());

            // 仅在实际处理时记录日志，输出相对路径
            String relativePath = toRelativePath(filePath);
            if (foundByContentHash && !force) {
                log.info("{}/{} 跳过: {}", currentCount, scanTotal.get(), relativePath != null ? relativePath : filePath);

                // 如果当前文件路径与数据库中已有照片路径不同，记录为内容重复
                String existingPath = photo.getOriginalPath();
                if (existingPath != null && !existingPath.equals(filePath)) {
                    String existingRelPath = toRelativePath(existingPath);
                    skippedFileRecords.add(buildSkippedFileRecord(
                        imageFile.getAbsolutePath(),
                        "内容重复",
                        "与已有照片内容相同: " + (existingRelPath != null ? existingRelPath : existingPath),
                        imageFile.length()
                    ));
                    notifyScanSkip(
                        filePath,
                        "FILE",
                        "内容重复",
                        "与已有照片内容相同: " + (existingRelPath != null ? existingRelPath : existingPath),
                        currentCount,
                        scanTotal.get()
                    );
                }
            } else if (createdFromCanonicalPhoto && !force) {
                Photo canonicalPhoto = photoByHash.orElse(null);
                String canonicalPath = canonicalPhoto == null ? null : canonicalPhoto.getOriginalPath();
                String canonicalRelPath = canonicalPath == null ? null : toRelativePath(canonicalPath);
                log.info("{}/{} 处理重复内容副本: {}", currentCount, scanTotal.get(), relativePath != null ? relativePath : filePath);
                if (canonicalPath != null) {
                    log.info("重复内容将保留独立照片记录，并关联到规范源: {}", canonicalRelPath != null ? canonicalRelPath : canonicalPath);
                }
            } else {
                log.info("{}/{} 处理: {}", currentCount, scanTotal.get(), relativePath != null ? relativePath : filePath);
            }

            // 检查是否需要重新处理或继续处理（基于处理状态）
            boolean needsReprocessing = photo.getId() == null || photo.needsContinuation(force);

            // 逐步处理每个步骤，支持断点续上
            processPhotoStepByStep(imageFile, photo, album, contentHash, pathHash, force, needsReprocessing, foundByContentHash);
            } catch (IllegalStateException e) {
            // 应用关闭时的异常，静默处理
            if (e.getMessage() != null && e.getMessage().contains("关闭")) {
                log.debug("应用关闭，停止处理图片: {}", imageFile.getName());
            } else {
                log.warn("处理图片失败（应用状态异常）: {}", toRelativePath(imageFile.getAbsolutePath()), e);
            }
        } catch (org.springframework.context.ApplicationContextException e) {
            // Spring上下文异常，应用可能正在关闭
            log.debug("应用上下文异常，停止处理图片: {}", imageFile.getName());
        } catch (Exception e) {
            // 检查是否是应用关闭相关的异常
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMsg.contains("closed") || errorMsg.contains("shutdown") ||
                (errorMsg.contains("context") && errorMsg.contains("close"))) {
                log.debug("应用关闭，停止处理图片: {}", imageFile.getName());
            } else {
                log.error("处理图片失败: {}", toRelativePath(imageFile.getAbsolutePath()), e);
                String relPath = toRelativePath(imageFile.getAbsolutePath());
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                String detail = msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
                skippedFileRecords.add(buildSkippedFileRecord(imageFile.getAbsolutePath(), "处理异常", detail, imageFile.length()));
                notifyScanFailure(imageFile.getAbsolutePath(), "FILE", detail, scanCurrent.get(), scanTotal.get());
            }
        }
        } // end synchronized block
    }

    private SkippedFileRecord buildSkippedFileRecord(String absolutePath, String reason, String detail, long fileSizeBytes) {
        String relPath = toRelativePath(absolutePath);
        Long userId = resolveSkippedRecordUserId(absolutePath);
        return new SkippedFileRecord(
            skippedFileIndex.incrementAndGet(),
            userId,
            relPath,
            reason,
            detail,
            fileSizeBytes,
            LocalDateTime.now()
        );
    }

    private Long resolveSkippedRecordUserId(String absolutePath) {
        Long storageUserId = currentStorageUserId.get();
        if (storageUserId != null) {
            return storageUserId;
        }
        if (absolutePath == null || absolutePath.isBlank()) {
            return null;
        }
        return userPathService.extractUserIdFromPath(absolutePath);
    }

    private void executeWithScanProgressListener(ScanProgressListener listener, Runnable runnable) {
        if (listener == null) {
            runnable.run();
            return;
        }

        currentScanProgressListener.set(listener);
        currentResumeAnchor.set(listener.getResumeFromPath());
        currentResumeAnchorType.set(listener.getResumeFromType());
        currentResumeAnchorReached.set(listener.getResumeFromPath() == null || listener.getResumeFromPath().isBlank());
        currentResumeSkipSubtree.remove();
        try {
            runnable.run();
        } finally {
            currentScanProgressListener.remove();
            currentResumeAnchor.remove();
            currentResumeAnchorType.remove();
            currentResumeAnchorReached.remove();
            currentResumeSkipSubtree.remove();
        }
    }

    private void notifyScanPrepared(String rootPath, boolean force, int total) {
        ScanProgressListener listener = currentScanProgressListener.get();
        if (listener != null) {
            listener.onScanPrepared(rootPath, force, total);
        }
    }

    private void notifyScanProgress(String absolutePath, String pathType, int current, int total) {
        ScanProgressListener listener = currentScanProgressListener.get();
        if (listener != null) {
            listener.onPathProcessed(absolutePath, pathType, current, total);
        }
    }

    private void notifyScanSkip(String absolutePath, String pathType, String reason, String detail, int current, int total) {
        ScanProgressListener listener = currentScanProgressListener.get();
        if (listener != null) {
            listener.onPathSkipped(absolutePath, pathType, reason, detail, current, total);
        }
    }

    private void notifyScanFailure(String absolutePath, String pathType, String errorMessage, int current, int total) {
        ScanProgressListener listener = currentScanProgressListener.get();
        if (listener != null) {
            listener.onPathFailed(absolutePath, pathType, errorMessage, current, total);
        }
    }

    private void notifyScanCompleted() {
        ScanProgressListener listener = currentScanProgressListener.get();
        if (listener != null) {
            listener.onScanCompleted(scanCurrent.get(), scanTotal.get(), skippedFileRecords.size(), 0);
        }
    }

    private void notifyScanFailed(Exception exception) {
        ScanProgressListener listener = currentScanProgressListener.get();
        if (listener != null) {
            listener.onScanFailed(exception, scanCurrent.get(), scanTotal.get());
        }
    }

    private void ensureScanCanContinue(String path) {
        ScanProgressListener listener = currentScanProgressListener.get();
        if (listener == null) {
            return;
        }
        ScanControlAction action = listener.getControlAction();
        if (action == ScanControlAction.PAUSE || action == ScanControlAction.CANCEL) {
            throw new ScanInterruptedException(action, path);
        }
    }

    private boolean shouldSkipForResume(String path, boolean directory) {
        String anchor = currentResumeAnchor.get();
        if (anchor == null || anchor.isBlank()) {
            return false;
        }
        String anchorType = currentResumeAnchorType.get();

        String normalizedPath = new File(path).toPath().toAbsolutePath().normalize().toString();
        String normalizedAnchor = new File(anchor).toPath().toAbsolutePath().normalize().toString();
        String skipSubtree = currentResumeSkipSubtree.get();
        if (skipSubtree != null && !skipSubtree.isBlank()) {
            String normalizedSkipSubtree = new File(skipSubtree).toPath().toAbsolutePath().normalize().toString();
            String subtreePrefix = normalizedSkipSubtree.endsWith(File.separator)
                ? normalizedSkipSubtree
                : normalizedSkipSubtree + File.separator;
            if (normalizedPath.equals(normalizedSkipSubtree) || normalizedPath.startsWith(subtreePrefix)) {
                return true;
            }
            currentResumeSkipSubtree.remove();
        }

        Boolean reached = currentResumeAnchorReached.get();
        if (Boolean.TRUE.equals(reached)) {
            return false;
        }

        if (normalizedPath.equals(normalizedAnchor)) {
            currentResumeAnchorReached.set(true);
            log.info("续扫断点已命中，从下一项继续: {}", normalizedPath);
            if ("DIRECTORY".equalsIgnoreCase(anchorType)) {
                currentResumeSkipSubtree.set(normalizedAnchor);
                return true;
            }
            return !directory;
        }

        if (directory) {
            String prefix = normalizedPath.endsWith(File.separator) ? normalizedPath : normalizedPath + File.separator;
            if (normalizedAnchor.startsWith(prefix)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 逐步处理照片的各个步骤，支持断点续上
     */
    private void processPhotoStepByStep(File imageFile, Photo photo, Album album, String contentHash,
                                       String pathHash, boolean force, boolean needsReprocessing,
                                       boolean foundByContentHash) {
        try {
            ensureScanCanContinue(imageFile.getAbsolutePath());
            // 步骤1: 设置基础信息
            if (photo.getProcessingStatus() == ProcessingStatus.PENDING || needsReprocessing) {
                processBasicInfo(photo, album, contentHash, pathHash, imageFile);
                photo.advanceProcessingStatus();
                photoRepository.save(photo);
            }

            // 步骤2: 确保相册标签已初始化
            ensureScanCanContinue(imageFile.getAbsolutePath());
            Album albumWithTags = albumRepository.findByIdWithTags(album.getId()).orElse(album);
            if (albumWithTags.getTags() == null) {
                albumWithTags.setTags(new ArrayList<>());
            }
            album = albumWithTags;

            // 对于内容重复副本，优先复用规范源已生成的基础元数据和派生资源，
            // 避免每次都重新跑 EXIF/缩略图/色彩分析。
            ensureScanCanContinue(imageFile.getAbsolutePath());
            if (!force && tryReuseCanonicalPhotoAssets(photo)) {
                photoRepository.save(photo);
            }

            // 步骤3: 提取EXIF信息
            ensureScanCanContinue(imageFile.getAbsolutePath());
            if (photo.getProcessingStatus() == ProcessingStatus.BASIC_INFO_DONE || needsReprocessing) {
                try {
                    extractExifData(imageFile, photo);
                    photo.advanceProcessingStatus();
                    photoRepository.save(photo);
                } catch (Exception e) {
                    photo.markProcessingFailed("EXIF提取失败: " + sanitizeVisibleMessage(e.getMessage()));
                    photoRepository.save(photo);
                    throw e;
                }
            }

            // 步骤4: 生成缩略图
            ensureScanCanContinue(imageFile.getAbsolutePath());
            if (photo.getProcessingStatus() == ProcessingStatus.BASIC_INFO_DONE ||
                photo.getProcessingStatus() == ProcessingStatus.THUMBNAILS_DONE || needsReprocessing) {
                try {
                    generateThumbnailAndWebP(imageFile, photo);
                    regenerateMissingThumbnails(imageFile, photo);
                    photo.setProcessingStatus(ProcessingStatus.THUMBNAILS_DONE);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    photo.markProcessingFailed("缩略图生成失败: " + sanitizeVisibleMessage(e.getMessage()));
                    photoRepository.save(photo);
                    throw e;
                }
            }

            // 步骤5: 分析色彩和质量评分
            ensureScanCanContinue(imageFile.getAbsolutePath());
            if (photo.getProcessingStatus() == ProcessingStatus.THUMBNAILS_DONE ||
                photo.getProcessingStatus() == ProcessingStatus.ANALYSIS_DONE || needsReprocessing) {
                try {
                    colorAnalysisService.analyzeColor(imageFile, photo);
                    calculateQualityScore(photo);
                    photo.setProcessingStatus(ProcessingStatus.ANALYSIS_DONE);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    photo.markProcessingFailed("色彩分析失败: " + sanitizeVisibleMessage(e.getMessage()));
                    photoRepository.save(photo);
                    throw e;
                }
            }

            // 步骤6: 人脸检测
            List<Face> faces = new ArrayList<>();
            ensureScanCanContinue(imageFile.getAbsolutePath());
            if (photo.getProcessingStatus() == ProcessingStatus.ANALYSIS_DONE ||
                photo.getProcessingStatus() == ProcessingStatus.FACES_DONE || needsReprocessing) {
                try {
                    faces = processFaces(imageFile, photo, force, foundByContentHash);
                    // 注意：不再直接修改photo的faces属性，避免Hibernate orphanRemoval问题
                    // faces已经在faceService.detectAndSaveFaces中保存到数据库了
                    photo.setProcessingStatus(ProcessingStatus.FACES_DONE);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                    // 对于ONNX Runtime相关错误，不标记为失败，继续处理
                    if (errorMsg.contains("ONNX") || errorMsg.contains("onnxruntime") ||
                        errorMsg.contains("NoClassDefFound") || errorMsg.contains("UnsatisfiedLinkError")) {
                        log.warn("人脸检测因ONNX Runtime问题跳过，继续处理其他步骤: {}", errorMsg);
                        photo.setProcessingStatus(ProcessingStatus.FACES_DONE); // 标记为已完成（跳过）
                        photoRepository.save(photo);
                    } else {
                        photo.markProcessingFailed("人脸检测失败: " + sanitizeVisibleMessage(e.getMessage()));
                        photoRepository.save(photo);
                        throw e;
                    }
                }
            } else if (photo.getProcessingStatus().ordinal() >= ProcessingStatus.FACES_DONE.ordinal()) {
                // 如果已经完成人脸检测，获取现有的人脸数据
                faces = faceService.getFacesByPhoto(photo.getId());
            }

            // 步骤7: 主体检测
            ensureScanCanContinue(imageFile.getAbsolutePath());
            if (photo.getProcessingStatus() == ProcessingStatus.FACES_DONE ||
                photo.getProcessingStatus() == ProcessingStatus.SUBJECT_DONE || needsReprocessing) {
                try {
                    subjectDetectionService.detectSubject(imageFile, photo, faces);
                    photo.setProcessingStatus(ProcessingStatus.SUBJECT_DONE);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    photo.markProcessingFailed("主体检测失败: " + sanitizeVisibleMessage(e.getMessage()));
                    photoRepository.save(photo);
                    throw e;
                }
            }

            // 步骤8: 智能标签
            ensureScanCanContinue(imageFile.getAbsolutePath());
            if (photo.getProcessingStatus() == ProcessingStatus.SUBJECT_DONE ||
                photo.getProcessingStatus() == ProcessingStatus.TAGS_DONE || needsReprocessing) {
                try {
                    processTags(photo, album, imageFile, faces.size(), force);
                    photo.setProcessingStatus(ProcessingStatus.TAGS_DONE);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    photo.markProcessingFailed("标签处理失败: " + sanitizeVisibleMessage(e.getMessage()));
                    photoRepository.save(photo);
                    throw e;
                }
            }

            // 步骤9: AI评分
            ensureScanCanContinue(imageFile.getAbsolutePath());
            if (photo.getProcessingStatus() == ProcessingStatus.TAGS_DONE ||
                photo.getProcessingStatus() == ProcessingStatus.AI_SCORING_DONE || needsReprocessing) {
                try {
                    // AI评分服务会重新加载完整的Photo对象，避免懒加载问题
                    aiScoringService.scorePhoto(photo);
                    photo.setProcessingStatus(ProcessingStatus.AI_SCORING_DONE);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    // AI评分失败不应该阻止图片完成处理，只是记录警告
                    log.warn("AI评分失败，但继续处理: {}", e.getMessage());
                    log.warn("AI评分失败详情 - Photo ID: {}, File: {}", photo.getId(), toRelativePath(photo.getOriginalPath()));
                    log.warn("完整错误堆栈:", e);
                    photo.setProcessingStatus(ProcessingStatus.AI_SCORING_DONE); // 仍标记为完成，避免重复尝试
                    photoRepository.save(photo);
                }
            }

            // 步骤10: 自动背景移除（如果开启）
            ensureScanCanContinue(imageFile.getAbsolutePath());
            if (autoBackgroundRemoval && backgroundRemovalService.isModelAvailable()) {
                if (photo.getBackgroundRemovedPath() == null || photo.getBackgroundRemovedPath().isEmpty()) {
                    try {
                        // 输出到 .thumbnails 文件夹，使用统一的命名规范
                        String thumbnailDir = new File(imageFile.getParent(), ".thumbnails").getAbsolutePath();
                        File outputFile = new File(thumbnailDir, "bg_removed_" + photo.getId() + ".png");
                        
                        // 获取图片尺寸（用于转换人脸坐标）
                        BufferedImage img = javax.imageio.ImageIO.read(imageFile);
                        final int imgWidth = img.getWidth();
                        final int imgHeight = img.getHeight();
                        
                        // 获取人脸区域用于优化（如果有人脸）- 转换归一化坐标到像素坐标
                        // 使用 faceService 获取人脸，避免懒加载问题
                        List<Rectangle> faceRegions = null;
                        try {
                            List<Face> existingFaces = faceService.getFacesByPhoto(photo.getId());
                            if (existingFaces != null && !existingFaces.isEmpty()) {
                                faceRegions = existingFaces.stream()
                                    .map(face -> new Rectangle(
                                        (int)(face.getX() * imgWidth),
                                        (int)(face.getY() * imgHeight),
                                        (int)(face.getWidth() * imgWidth),
                                        (int)(face.getHeight() * imgHeight)))
                                    .collect(java.util.stream.Collectors.toList());
                            }
                        } catch (Exception e) {
                            log.debug("获取人脸信息失败，跳过人脸优化: {}", e.getMessage());
                        }
                        
                        if (backgroundRemovalService.removeBackground(imageFile, outputFile, faceRegions)) {
                            photo.setBackgroundRemovedPath(toStoredManagedPath(outputFile.getAbsolutePath(), photo.getUserId()));
                            photoRepository.save(photo);
                            log.debug("自动背景移除完成: {}", photo.getId());
                        }
                    } catch (Exception e) {
                        log.warn("自动背景移除失败: {} - {}", photo.getId(), e.getMessage());
                        // 不阻止流程继续
                    }
                }
            }

            // 步骤11: 完成处理
            if (photo.getProcessingStatus() == ProcessingStatus.AI_SCORING_DONE) {
                photo.setProcessingStatus(ProcessingStatus.COMPLETED);
                photoRepository.save(photo);
            }

        } catch (Exception e) {
            // 如果是处理失败，记录错误但不抛出异常，确保其他图片能继续处理
            if (photo.getProcessingStatus() != ProcessingStatus.FAILED) {
                photo.markProcessingFailed("未知处理错误: " + sanitizeVisibleMessage(e.getMessage()));
                photoRepository.save(photo);
            }
            log.error("处理图片失败: {}", toRelativePath(imageFile.getAbsolutePath()), e);
        }
    }

    /**
     * 处理照片基础信息
     */
    private void processBasicInfo(Photo photo, Album album, String contentHash, String pathHash, File imageFile) {
        // 规范源照片保留内容哈希；重复内容副本通过 canonicalPhotoId 指向规范源，避免唯一索引冲突。
        if (photo.getCanonicalPhotoId() == null && photo.getId() == null) {
            photo.setContentHash(contentHash);
        } else if (photo.getCanonicalPhotoId() == null && (photo.getContentHash() == null || photo.getContentHash().isEmpty())) {
            photo.setContentHash(contentHash);
        }
        photo.setAlbumId(album.getId());
        photo.setUserId(album.getUserId());
        photo.setFilename(imageFile.getName());
        String storedOriginalPath = toStoredManagedPath(imageFile.getAbsolutePath(), album.getUserId());
        photo.setOriginalPath(storedOriginalPath);
        photo.setPathHash(calculateSha256(storedOriginalPath));
        photo.setFileSize(imageFile.length());
        photo.setProcessingStatus(ProcessingStatus.BASIC_INFO_DONE);
    }

    private Photo resolvePhotoRecordForScan(Optional<Photo> photoByHash,
                                            Optional<Photo> photoByPathHash,
                                            Optional<Photo> photoByPath) {
        if (photoByPathHash.isPresent()) {
            return photoByPathHash.get();
        }
        if (photoByPath.isPresent()) {
            return photoByPath.get();
        }
        if (photoByHash.isPresent()) {
            Photo canonicalPhoto = photoByHash.get();
            if (canonicalPhoto.getId() != null) {
                Photo duplicatePhoto = new Photo();
                duplicatePhoto.setCanonicalPhotoId(canonicalPhoto.getId());
                return duplicatePhoto;
            }
            return canonicalPhoto;
        }
        return new Photo();
    }

    private boolean tryReuseCanonicalPhotoAssets(Photo photo) {
        if (photo == null || photo.getCanonicalPhotoId() == null) {
            return false;
        }
        if (photo.getProcessingStatus() != ProcessingStatus.BASIC_INFO_DONE
            && photo.getProcessingStatus() != ProcessingStatus.PENDING
            && photo.getProcessingStatus() != ProcessingStatus.FAILED) {
            return false;
        }
        Photo canonicalPhoto = photoRepository.findById(photo.getCanonicalPhotoId()).orElse(null);
        if (canonicalPhoto == null) {
            return false;
        }
        boolean reused = copyReusableFieldsFromCanonical(photo, canonicalPhoto);
        if (reused) {
            photo.setProcessingStatus(ProcessingStatus.ANALYSIS_DONE);
        }
        return reused;
    }

    private boolean copyReusableFieldsFromCanonical(Photo target, Photo canonical) {
        if (target == null || canonical == null) {
            return false;
        }
        boolean reusableAssetsPresent = hasText(canonical.getThumbnailPath())
            || hasText(canonical.getWebpPath())
            || hasText(canonical.getSmallThumbPath())
            || hasText(canonical.getMediumThumbPath())
            || hasText(canonical.getLargeThumbPath());
        boolean reusableMetadataPresent = canonical.getWidth() != null
            || canonical.getHeight() != null
            || hasText(canonical.getFormat())
            || hasText(canonical.getExifData())
            || canonical.getTakenAt() != null
            || canonical.getQualityScore() != null
            || hasText(canonical.getDominantColor())
            || hasText(canonical.getColorCategory())
            || hasText(canonical.getColorPalette());
        if (!reusableAssetsPresent && !reusableMetadataPresent) {
            return false;
        }

        target.setWidth(canonical.getWidth());
        target.setHeight(canonical.getHeight());
        target.setFormat(canonical.getFormat());
        target.setExifData(canonical.getExifData());
        target.setCameraMake(canonical.getCameraMake());
        target.setCameraModel(canonical.getCameraModel());
        target.setLensModel(canonical.getLensModel());
        target.setFocalLength(canonical.getFocalLength());
        target.setAperture(canonical.getAperture());
        target.setShutterSpeed(canonical.getShutterSpeed());
        target.setIso(canonical.getIso());
        target.setShutterSpeedSeconds(canonical.getShutterSpeedSeconds());
        target.setFocalLengthMm(canonical.getFocalLengthMm());
        target.setApertureValue(canonical.getApertureValue());
        target.setTakenAt(canonical.getTakenAt());
        target.setQualityScore(canonical.getQualityScore());
        target.setFocusX(canonical.getFocusX());
        target.setFocusY(canonical.getFocusY());
        target.setDominantColor(canonical.getDominantColor());
        target.setColorCategory(canonical.getColorCategory());
        target.setColorPalette(canonical.getColorPalette());
        target.setThumbnailPath(canonical.getThumbnailPath());
        target.setWebpPath(canonical.getWebpPath());
        target.setSmallThumbPath(canonical.getSmallThumbPath());
        target.setMediumThumbPath(canonical.getMediumThumbPath());
        target.setLargeThumbPath(canonical.getLargeThumbPath());
        target.setBackgroundRemovedPath(canonical.getBackgroundRemovedPath());
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 处理人脸检测
     */
    private List<Face> processFaces(File imageFile, Photo photo, boolean force, boolean foundByContentHash) {
        List<Face> faces;

        if (foundByContentHash && !force && photo.getId() != null) {
            // 检查是否已有人脸数据
            long existingFaceCount = faceService.getFaceCountByPhoto(photo.getId());
            if (existingFaceCount > 0) {
                log.debug("照片已有人脸数据，跳过重新检测（photoId={}, faceCount={}）", photo.getId(), existingFaceCount);
                faces = faceService.getFacesByPhoto(photo.getId());
            } else {
                // 虽然通过contentHash找到，但没有人脸数据，需要检测
                faces = detectFacesSafely(imageFile, photo);
            }
        } else {
            // 新照片或强制扫描，重新检测人脸
            faces = detectFacesSafely(imageFile, photo);
        }

        return faces;
    }

    /**
     * 安全的人脸检测（处理异常）
     */
    private List<Face> detectFacesSafely(File imageFile, Photo photo) {
        try {
            boolean forceRebuild = photo != null && photo.getId() != null;
            return faceService.detectAndSaveFaces(imageFile, photo, false, forceRebuild, forceRebuild);
        } catch (UnsatisfiedLinkError e) {
            log.warn("人脸检测服务不可用（缺少系统依赖库），跳过人脸检测: {}。请安装 Microsoft Visual C++ Redistributable 或相关依赖。", imageFile.getName());
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("人脸检测失败，使用简单方法: {}", imageFile.getName(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 处理标签
     */
    private void processTags(Photo photo, Album album, File imageFile, int faceCount, boolean force) {
        // 合并相册标签到照片标签，便于搜索（复制一份避免懒加载问题）
        List<Tag> albumTags = album.getTags() == null ? new ArrayList<>() : new ArrayList<>(album.getTags());
        Set<String> albumTagNames = albumTags.stream()
            .map(Tag::getName)
            .collect(java.util.stream.Collectors.toSet());

        if (!albumTags.isEmpty()) {
            if (photo.getTags() == null) {
                photo.setTags(new HashSet<>());
            }
            // 添加相册标签，避免重复
            for (Tag albumTag : albumTags) {
                if (!photo.getTags().contains(albumTag)) {
                    photo.getTags().add(albumTag);
                }
            }
        }

        // 智能标签（含人脸信息）
        // 强制扫描时，删除旧智能标签后重新生成；普通扫描时追加
        // 传递相册标签名称，确保不会误删相册标签
        smartTagService.applySmartTags(imageFile, photo, faceCount, force, albumTagNames);
    }

    /**
     * 提取EXIF信息
     */
    private void extractExifData(File imageFile, Photo photo) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
            Map<String, Object> exifMap = new HashMap<>();

            // 提取相机信息
            ExifIFD0Directory ifd0Directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0Directory != null) {
                if (ifd0Directory.containsTag(ExifIFD0Directory.TAG_MAKE)) {
                    String make = ifd0Directory.getString(ExifIFD0Directory.TAG_MAKE);
                    photo.setCameraMake(make);
                    exifMap.put("make", make);
                }
                if (ifd0Directory.containsTag(ExifIFD0Directory.TAG_MODEL)) {
                    String model = ifd0Directory.getString(ExifIFD0Directory.TAG_MODEL);
                    photo.setCameraModel(model);
                    exifMap.put("model", model);
                }
            }

            // 提取拍摄参数
            ExifSubIFDDirectory subIfdDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (subIfdDirectory != null) {
                if (subIfdDirectory.containsTag(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)) {
                    String focalLength = subIfdDirectory.getString(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
                    photo.setFocalLength(focalLength);
                    exifMap.put("focalLength", focalLength);
                    // parse numeric mm value
                    Double focalMm = parseFocalLengthToMm(focalLength);
                    photo.setFocalLengthMm(focalMm);
                }
                if (subIfdDirectory.containsTag(ExifSubIFDDirectory.TAG_FNUMBER)) {
                    String aperture = subIfdDirectory.getString(ExifSubIFDDirectory.TAG_FNUMBER);
                    photo.setAperture(aperture);
                    exifMap.put("aperture", aperture);
                    // parse numeric aperture
                    Double apertureVal = parseApertureValue(aperture);
                    photo.setApertureValue(apertureVal);
                }
                if (subIfdDirectory.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)) {
                    String shutterSpeed = subIfdDirectory.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
                    photo.setShutterSpeed(shutterSpeed);
                    exifMap.put("shutterSpeed", shutterSpeed);
                    // parse exposure time to seconds
                    Double seconds = parseShutterToSeconds(shutterSpeed);
                    photo.setShutterSpeedSeconds(seconds);
                }
                if (subIfdDirectory.containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)) {
                    Integer iso = subIfdDirectory.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);
                    photo.setIso(iso);
                    exifMap.put("iso", iso);
                }
                if (subIfdDirectory.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)) {
                    Date dateTaken = subIfdDirectory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                    if (dateTaken != null) {
                        photo.setTakenAt(LocalDateTime.ofInstant(dateTaken.toInstant(), java.time.ZoneId.systemDefault()));
                        exifMap.put("takenAt", dateTaken.toString());
                    }
                }
            }

            // 提取所有EXIF标签（跳过ICC Profile等大体积无用目录和标签）
            Set<String> skipDirectories = Set.of(
                "ICC Profile", "Photoshop", "IPTC",
                "Adobe JPEG", "Adobe", "Huffman", "File Type"
            );
            for (Directory directory : metadata.getDirectories()) {
                if (skipDirectories.contains(directory.getName())) {
                    continue;
                }
                for (com.drew.metadata.Tag tag : directory.getTags()) {
                    String desc = tag.getDescription();
                    if (shouldFilterExifTag(tag.getTagName(), desc)) {
                        continue;
                    }
                    exifMap.put(tag.getTagName(), desc);
                }
            }

            // 尝试从 exifMap 中提取镜头型号（兼容多种键名）
            Object lensVal = exifMap.get("Lens Model");
            if (lensVal == null) lensVal = exifMap.get("Lens");
            if (lensVal == null) lensVal = exifMap.get("LensModel");
            if (lensVal != null) {
                String lensModel = String.valueOf(lensVal);
                photo.setLensModel(lensModel);
                exifMap.put("lensModel", lensModel);
            }

            // 保存为JSON
            photo.setExifData(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(exifMap));

        } catch (ImageProcessingException | IOException e) {
            log.warn("提取EXIF信息失败: {}", imageFile.getName(), e);
        }

        // 如果没有从EXIF中提取到拍摄时间，尝试从文件路径中提取
        if (photo.getTakenAt() == null) {
            LocalDateTime pathTime = parseDateFromFilePath(imageFile.getAbsolutePath());
            if (pathTime != null) {
                photo.setTakenAt(pathTime);
                log.debug("从文件路径提取到拍摄时间: {} -> {}", imageFile.getName(), pathTime);
            } else {
                // 最后使用文件的创建时间
                try {
                    java.nio.file.Path filePath = imageFile.toPath();
                    java.nio.file.attribute.BasicFileAttributes attrs = java.nio.file.Files.readAttributes(filePath, java.nio.file.attribute.BasicFileAttributes.class);
                    java.time.Instant instant = attrs.creationTime().toInstant();
                    LocalDateTime fileCreationTime = LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
                    photo.setTakenAt(fileCreationTime);
                    log.debug("使用文件创建时间作为拍摄时间: {} -> {}", imageFile.getName(), fileCreationTime);
                } catch (Exception e) {
                    log.warn("无法获取文件创建时间: {}", imageFile.getName(), e);
                }
            }
        }
    }

    /**
     * Parse shutter/exposure string to seconds (e.g. "1/80" -> 0.0125, "0.5" -> 0.5)
     */
    private Double parseShutterToSeconds(String shutter) {
        if (shutter == null) return null;
        try {
            String s = shutter.trim().toLowerCase();
            // remove "sec" or "s"
            s = s.replaceAll("sec", "").replaceAll("s", "").trim();
            if (s.contains("/")) {
                String[] parts = s.split("/");
                if (parts.length == 2) {
                    double num = Double.parseDouble(parts[0]);
                    double den = Double.parseDouble(parts[1]);
                    if (den != 0) return num / den;
                }
            }
            // try parse as decimal
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseFocalLengthToMm(String focal) {
        if (focal == null) return null;
        try {
            String s = focal.trim().toLowerCase();
            s = s.replaceAll("mm", "").trim();
            // sometimes "28" or "28.0"
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseApertureValue(String aperture) {
        if (aperture == null) return null;
        try {
            String s = aperture.trim().toLowerCase();
            s = s.replaceAll("f/", "").replaceAll("f", "").trim();
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Update EXIF fields for all photos by parsing existing exifData or string fields.
     * Includes both numeric fields (shutter speed seconds, focal length mm, aperture value)
     * and string fields (ISO, lens model).
     */
    @Transactional
    public void updateAllExifNumericFields() {
        log.info("开始批量更新所有照片的 EXIF 字段...");
        int total = countPhotosForCurrentScope();
        AtomicInteger count = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();
        String tid = currentTaskId.get();
        if (tid != null) {
            setTaskProgress(tid, 0, total);
            appendTaskLog(tid, "开始遍历 " + total + " 张照片");
        }
        forEachPhotoInCurrentScope(photo -> {
            boolean changed = false;
            // try use existing string fields first
            if (photo.getShutterSpeedSeconds() == null) {
                Double sec = null;
                String s = photo.getShutterSpeed();
                if (s != null) sec = parseShutterToSeconds(s);
                // fallback to exifData JSON
                if (sec == null && photo.getExifData() != null) {
                    try {
                        Map m = new com.fasterxml.jackson.databind.ObjectMapper().readValue(photo.getExifData(), Map.class);
                        Object val = m.get("Exposure Time");
                        if (val == null) val = m.get("ExposureTime");
                        if (val == null) val = m.get("shutterSpeed");
                        if (val != null) sec = parseShutterToSeconds(String.valueOf(val));
                    } catch (Exception ignored) {}
                }
                if (sec != null) {
                    photo.setShutterSpeedSeconds(sec);
                    changed = true;
                }
            }

            if (photo.getFocalLengthMm() == null) {
                Double fl = null;
                String s = photo.getFocalLength();
                if (s != null) {
                    fl = parseFocalLengthToMm(s);
                    if (fl == null) {
                        log.debug("Failed to parse focal length: {}", s);
                    }
                }
                if (fl == null && photo.getExifData() != null) {
                    try {
                        Map m = new com.fasterxml.jackson.databind.ObjectMapper().readValue(photo.getExifData(), Map.class);
                        Object val = m.get("Focal Length");
                        if (val == null) val = m.get("focalLength");
                        if (val != null) {
                            fl = parseFocalLengthToMm(String.valueOf(val));
                            if (fl == null) {
                                log.debug("Failed to parse focal length from EXIF: {}", val);
                            }
                        }
                    } catch (Exception ignored) {}
                }
                if (fl != null) {
                    photo.setFocalLengthMm(fl);
                    changed = true;
                }
            }

            if (photo.getApertureValue() == null) {
                Double ap = null;
                String s = photo.getAperture();
                if (s != null) ap = parseApertureValue(s);
                if (ap == null && photo.getExifData() != null) {
                    try {
                        Map m = new com.fasterxml.jackson.databind.ObjectMapper().readValue(photo.getExifData(), Map.class);
                        Object val = m.get("F-Number");
                        if (val == null) val = m.get("aperture");
                        if (val != null) ap = parseApertureValue(String.valueOf(val));
                    } catch (Exception ignored) {}
                }
                if (ap != null) {
                    photo.setApertureValue(ap);
                    changed = true;
                }
            }

            // Update ISO if not set
            if (photo.getIso() == null && photo.getExifData() != null) {
                try {
                    Map m = new com.fasterxml.jackson.databind.ObjectMapper().readValue(photo.getExifData(), Map.class);
                    Object isoVal = m.get("ISO Speed Ratings");
                    if (isoVal == null) isoVal = m.get("ISO");
                    if (isoVal == null) isoVal = m.get("iso");
                    if (isoVal != null) {
                        try {
                            Integer iso = Integer.valueOf(String.valueOf(isoVal));
                            photo.setIso(iso);
                            changed = true;
                        } catch (NumberFormatException e) {
                            // ignore invalid ISO values
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Update lens model if not set
            if ((photo.getLensModel() == null || photo.getLensModel().isEmpty()) && photo.getExifData() != null) {
                try {
                    Map m = new com.fasterxml.jackson.databind.ObjectMapper().readValue(photo.getExifData(), Map.class);
                    Object lensVal = m.get("Lens Model");
                    if (lensVal == null) lensVal = m.get("Lens");
                    if (lensVal == null) lensVal = m.get("LensModel");
                    if (lensVal == null) lensVal = m.get("lensModel");
                    if (lensVal != null) {
                        String lensModel = String.valueOf(lensVal);
                        if (!lensModel.trim().isEmpty()) {
                            photo.setLensModel(lensModel.trim());
                            changed = true;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 清理 exifData JSON 中的 ICC Profile 等大体积无用字段
            if (photo.getExifData() != null) {
                try {
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> exifMap = mapper.readValue(photo.getExifData(), Map.class);
                    int before = exifMap.size();
                    exifMap.entrySet().removeIf(e ->
                        shouldFilterExifTag(e.getKey(), e.getValue() instanceof String ? (String) e.getValue() : null));
                    if (exifMap.size() < before) {
                        photo.setExifData(mapper.writeValueAsString(exifMap));
                        changed = true;
                        log.debug("照片 {} 清理了 {} 个EXIF无用字段", photo.getId(), before - exifMap.size());
                    }
                } catch (Exception e) {
                    log.warn("清理照片 {} 的EXIF数据失败: {}", photo.getId(), e.getMessage());
                }
            }

            if (changed) {
                photoRepository.save(photo);
                count.incrementAndGet();
            }
            int current = processed.incrementAndGet();
            if (tid != null && (current % 10 == 0 || current == total)) {
                setTaskProgress(tid, current, total);
                appendTaskLog(tid, "已处理 " + current + " / " + total + " 张");
            }
        });
        if (tid != null) {
            setTaskProgress(tid, processed.get(), total);
            appendTaskLog(tid, "处理结束, 共有 " + count.get() + " 张照片被更新");
        }
        log.info("EXIF 字段更新完成, 更新 {} 张照片", count.get());
    }

    /**
     * 重新计算所有照片的颜色相关属性
     */
    public void recalculateAllPhotoColors() {
        log.info("开始重新计算所有照片的颜色相关属性...");
        int total = countPhotosForCurrentScope();
        AtomicInteger count = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();
        String tid = currentTaskId.get();
        if (tid != null) {
            setTaskProgress(tid, 0, total);
            appendTaskLog(tid, "开始遍历 " + total + " 张照片");
        }

        forEachPhotoInCurrentScope(photo -> {
            boolean changed = false;

            // 重新分析照片颜色
            try {
                File imageFile = resolveOriginalFile(photo);
                if (imageFile.exists()) {
                    // 清除现有的颜色数据
                    photo.setDominantColor(null);
                    photo.setColorPalette(null);
                    photo.setColorCategory(null);

                    // 重新分析颜色
                    colorAnalysisService.analyzeColor(imageFile, photo);

                    // 如果有主色调，设置颜色分类
                    if (photo.getDominantColor() != null) {
                        String category = ColorAnalysisService.classifyColor(photo.getDominantColor());
                        photo.setColorCategory(category);
                    }

                    changed = true;
                } else {
                    log.warn("照片文件不存在: {}", toRelativePath(photo.getOriginalPath()));
                }
            } catch (Exception e) {
                log.warn("重新分析照片 {} 的颜色失败: {}", photo.getId(), e.getMessage());
            }

            if (changed) {
                photoRepository.save(photo);
                count.incrementAndGet();
            }

            int current = processed.incrementAndGet();
            if (tid != null && (current % 10 == 0 || current == total)) {
                setTaskProgress(tid, current, total);
                appendTaskLog(tid, "已处理 " + current + " / " + total + " 张");
            }
        });

        if (tid != null) {
            setTaskProgress(tid, processed.get(), total);
            appendTaskLog(tid, "照片颜色重新计算完成，开始更新相册氛围");
        }

        // 更新所有相册的氛围
        try {
            updateAllAlbumAtmospheres();
            if (tid != null) {
                appendTaskLog(tid, "相册氛围更新完成");
            }
        } catch (Exception e) {
            log.error("更新相册氛围失败", e);
            if (tid != null) {
                appendTaskLog(tid, "相册氛围更新失败: " + sanitizeVisibleMessage(e.getMessage()));
            }
        }

        log.info("颜色重新计算完成, 更新 {} 张照片", count.get());
    }

    /**
     * 更新所有相册的氛围
     */
    private void updateAllAlbumAtmospheres() {
        log.info("开始更新所有相册的氛围...");
        AtomicInteger albumCount = new AtomicInteger();
        forEachAlbumInCurrentScope(album -> {
            try {
                atmosphereAnalysisService.analyzeAlbumAtmosphere(album.getId());
                albumCount.incrementAndGet();
            } catch (Exception e) {
                log.warn("更新相册 {} 的氛围失败: {}", album.getId(), e.getMessage());
            }
        });

        log.info("相册氛围更新完成, 更新了 {} 个相册", albumCount.get());
    }

    /**
     * 批量更新所有照片的颜色分类
     */
    public void updateAllColorCategories() {
        log.info("开始批量更新所有照片的颜色分类...");
        int total = countPhotosForCurrentScope();
        AtomicInteger count = new AtomicInteger();
        AtomicInteger processed = new AtomicInteger();
        String tid = currentTaskId.get();
        if (tid != null) {
            setTaskProgress(tid, 0, total);
            appendTaskLog(tid, "开始遍历 " + total + " 张照片");
        }

        forEachPhotoInCurrentScope(photo -> {
            boolean changed = false;

            // 为有主色调但没有颜色分类的照片设置颜色分类
            if (photo.getDominantColor() != null && (photo.getColorCategory() == null || photo.getColorCategory().isEmpty())) {
                try {
                    String category = com.photoexhibition.service.ColorAnalysisService.classifyColor(photo.getDominantColor());
                    photo.setColorCategory(category);
                    changed = true;
                } catch (Exception e) {
                    log.warn("为照片 {} 设置颜色分类失败: {}", photo.getId(), e.getMessage());
                }
            }

            if (changed) {
                photoRepository.save(photo);
                count.incrementAndGet();
            }

            int current = processed.incrementAndGet();
            if (tid != null && (current % 10 == 0 || current == total)) {
                setTaskProgress(tid, current, total);
                appendTaskLog(tid, "已处理 " + current + " / " + total + " 张");
            }
        });

        if (tid != null) {
            setTaskProgress(tid, processed.get(), total);
            appendTaskLog(tid, "处理结束, 共有 " + count.get() + " 张照片被更新");
        }
        log.info("颜色分类更新完成, 更新 {} 张照片", count.get());
    }

    /**
     * 生成三级缩略图和WebP
     */
    private void generateThumbnailAndWebP(File imageFile, Photo photo) throws IOException {
        String baseDir = new File(imageFile.getParent(), ".thumbnails").getAbsolutePath();
        Files.createDirectories(Paths.get(baseDir));

        String baseName = FilenameUtils.getBaseName(imageFile.getName());
        BufferedImage originalImage = ImageIO.read(imageFile);

        if (originalImage != null) {
            photo.setWidth(originalImage.getWidth());
            photo.setHeight(originalImage.getHeight());
            photo.setFormat(FilenameUtils.getExtension(imageFile.getName()));

            // 生成三级缩略图
            generateThumbnailLevels(imageFile, photo, baseDir, baseName, originalImage);

            // 生成WebP（需要WebP库支持，暂时禁用）
            // 如需启用WebP支持，可以：
            // 1. 安装系统WebP工具 (cwebp) 并通过命令行调用
            // 2. 使用ImageIO扩展插件
            // 3. 集成其他WebP处理库
            // try {
            //     File webpFile = new File(baseDir, baseName + ".webp");
            //     // WebP转换逻辑
            //     photo.setWebpPath(webpFile.getAbsolutePath());
            // } catch (Exception e) {
            //     log.warn("生成WebP失败: {}", imageFile.getName(), e);
            // }
        }
    }

    /**
     * 生成三级缩略图
     */
    private void generateThumbnailLevels(File imageFile, Photo photo, String baseDir, String baseName, BufferedImage originalImage) throws IOException {
        long originalFileSize = imageFile.length();

        // 1. 生成小缩略图（用于封面和缩略图列表）
        File smallThumbFile = new File(baseDir, baseName + "_small.jpg");
        generateThumbnailWithQuality(originalImage, smallThumbFile, smallThumbnailWidth, smallThumbnailHeight, smallThumbnailQuality);
        photo.setSmallThumbPath(toStoredManagedPath(smallThumbFile.getAbsolutePath(), photo.getUserId()));
        long smallSizeKB = smallThumbFile.length() / 1024;

        // 2. 生成中缩略图（用于瀑布流显示）
        File mediumThumbFile = new File(baseDir, baseName + "_medium.jpg");
        generateThumbnailWithQuality(originalImage, mediumThumbFile, mediumThumbnailWidth, mediumThumbnailHeight, mediumThumbnailQuality);
        photo.setMediumThumbPath(toStoredManagedPath(mediumThumbFile.getAbsolutePath(), photo.getUserId()));
        long mediumSizeKB = mediumThumbFile.length() / 1024;

        // 3. 生成大缩略图（用于PhotoViewer大图显示）
        // 只有当原图足够大且能够显著压缩时才生成大缩略图
        File largeThumbFile = new File(baseDir, baseName + "_large.jpg");
        boolean shouldGenerateLargeThumb = shouldGenerateLargeThumbnail(originalImage, largeThumbnailWidth, largeThumbnailHeight, originalFileSize);

        String largeInfo;
        if (shouldGenerateLargeThumb) {
            generateThumbnailWithQuality(originalImage, largeThumbFile, largeThumbnailWidth, largeThumbnailHeight, largeThumbnailQuality);
            photo.setLargeThumbPath(toStoredManagedPath(largeThumbFile.getAbsolutePath(), photo.getUserId()));
            long largeSizeKB = largeThumbFile.length() / 1024;
            largeInfo = largeSizeKB + "KB";
        } else {
            // 如果无法进一步压缩，使用原图路径（不生成大缩略图文件）
            photo.setLargeThumbPath(null); // 表示使用原图
            largeInfo = "使用原图";
        }

        // 记录缩略图生成信息
        log.info("生成缩略图 - 小图:{}KB, 中图:{}KB, 大图:{} ({})",
            smallSizeKB, mediumSizeKB, largeInfo, imageFile.getName());

        // 兼容性：设置原有thumbnailPath为小缩略图路径
        photo.setThumbnailPath(toStoredManagedPath(smallThumbFile.getAbsolutePath(), photo.getUserId()));
    }

    /**
     * 生成指定尺寸和质量的缩略图
     */
    private void generateThumbnailWithQuality(BufferedImage originalImage, File outputFile, int maxWidth, int maxHeight, float quality) throws IOException {
        // 保持宽高比，计算合适的目标尺寸
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        double scaleX = (double) maxWidth / originalWidth;
        double scaleY = (double) maxHeight / originalHeight;
        double scale = Math.min(scaleX, scaleY);

        // 如果原图比目标尺寸小，直接缩小到原图尺寸（但不超过目标尺寸）
        if (scale >= 1.0) {
            scale = 1.0;
        }

        int targetWidth = (int) (originalWidth * scale);
        int targetHeight = (int) (originalHeight * scale);

        Thumbnails.of(originalImage)
            .size(targetWidth, targetHeight)
            .outputFormat("jpg")
            .outputQuality(quality)
            .toFile(outputFile);
    }

    /**
     * 判断是否应该生成大缩略图
     */
    private boolean shouldGenerateLargeThumbnail(BufferedImage originalImage, int maxWidth, int maxHeight, long originalFileSize) {
        if (!largeThumbnailSkipIfNoBenefit) {
            return true; // 如果配置为不跳过，直接生成
        }

        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // 如果原图尺寸小于等于大缩略图尺寸，不需要生成
        if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
            return false;
        }

        // 计算预期的大缩略图文件大小（粗略估计）
        // 假设大缩略图的像素数是原图的 (maxWidth/originalWidth * maxHeight/originalHeight) 倍
        double scaleX = (double) maxWidth / originalWidth;
        double scaleY = (double) maxHeight / originalHeight;
        double scale = Math.min(scaleX, scaleY);

        if (scale >= 0.9) {
            // 如果缩放比例大于90%，说明压缩效果不明显，跳过生成
            return false;
        }

        // 粗略估算压缩后的文件大小
        // JPEG压缩后的大小与像素数量和质量有关，这里使用简单的比例估算
        long estimatedSize = (long) (originalFileSize * scale * scale * largeThumbnailQuality);

        // 如果预计压缩后的大小大于原图的80%，说明压缩效果不佳，跳过生成
        if (estimatedSize > originalFileSize * 0.8) {
            return false;
        }

        return true;
    }

    /**
     * 检查并重新生成缺失的缩略图
     */
    private void regenerateMissingThumbnails(File imageFile, Photo photo) {
        String baseDir = new File(imageFile.getParent(), ".thumbnails").getAbsolutePath();
        String baseName = FilenameUtils.getBaseName(imageFile.getName());

        try {
            // 检查小缩略图
            if (photo.getSmallThumbPath() == null || photo.getSmallThumbPath().isEmpty()) {
                File smallThumbFile = new File(baseDir, baseName + "_small.jpg");
                if (!smallThumbFile.exists()) {
                    log.info("重新生成缺失的小缩略图: {}", imageFile.getName());
                    BufferedImage originalImage = ImageIO.read(imageFile);
                    if (originalImage != null) {
                        generateThumbnailWithQuality(originalImage, smallThumbFile, smallThumbnailWidth, smallThumbnailHeight, smallThumbnailQuality);
                        photo.setSmallThumbPath(toStoredManagedPath(smallThumbFile.getAbsolutePath(), photo.getUserId()));
                    }
                } else {
                    photo.setSmallThumbPath(toStoredManagedPath(smallThumbFile.getAbsolutePath(), photo.getUserId()));
                }
            }

            // 检查中缩略图
            if (photo.getMediumThumbPath() == null || photo.getMediumThumbPath().isEmpty()) {
                File mediumThumbFile = new File(baseDir, baseName + "_medium.jpg");
                if (!mediumThumbFile.exists()) {
                    log.info("重新生成缺失的中缩略图: {}", imageFile.getName());
                    BufferedImage originalImage = ImageIO.read(imageFile);
                    if (originalImage != null) {
                        generateThumbnailWithQuality(originalImage, mediumThumbFile, mediumThumbnailWidth, mediumThumbnailHeight, mediumThumbnailQuality);
                        photo.setMediumThumbPath(toStoredManagedPath(mediumThumbFile.getAbsolutePath(), photo.getUserId()));
                    }
                } else {
                    photo.setMediumThumbPath(toStoredManagedPath(mediumThumbFile.getAbsolutePath(), photo.getUserId()));
                }
            }

            // 检查大缩略图
            if (photo.getLargeThumbPath() == null || photo.getLargeThumbPath().isEmpty()) {
                File largeThumbFile = new File(baseDir, baseName + "_large.jpg");
                if (!largeThumbFile.exists()) {
                    log.info("检查是否需要重新生成大缩略图: {}", imageFile.getName());
                    BufferedImage originalImage = ImageIO.read(imageFile);
                    if (originalImage != null && shouldGenerateLargeThumbnail(originalImage, largeThumbnailWidth, largeThumbnailHeight, imageFile.length())) {
                        generateThumbnailWithQuality(originalImage, largeThumbFile, largeThumbnailWidth, largeThumbnailHeight, largeThumbnailQuality);
                        photo.setLargeThumbPath(toStoredManagedPath(largeThumbFile.getAbsolutePath(), photo.getUserId()));
                    } else {
                        // 如果不应该生成，设置为空（表示使用原图）
                        photo.setLargeThumbPath(null);
                    }
                } else {
                    photo.setLargeThumbPath(toStoredManagedPath(largeThumbFile.getAbsolutePath(), photo.getUserId()));
                }
            }

            // 检查原有缩略图路径（兼容性）
            if (photo.getThumbnailPath() == null || photo.getThumbnailPath().isEmpty()) {
                if (photo.getSmallThumbPath() != null) {
                    photo.setThumbnailPath(photo.getSmallThumbPath());
                } else {
                    // 如果小缩略图也不存在，创建一个
                    File thumbFile = new File(baseDir, baseName + "_thumb.jpg");
                    if (!thumbFile.exists()) {
                        log.info("重新生成缺失的兼容性缩略图: {}", imageFile.getName());
                        BufferedImage originalImage = ImageIO.read(imageFile);
                        if (originalImage != null) {
                            generateThumbnailWithQuality(originalImage, thumbFile, thumbnailWidth, thumbnailHeight, 0.85f);
                            photo.setThumbnailPath(toStoredManagedPath(thumbFile.getAbsolutePath(), photo.getUserId()));
                        }
                    } else {
                        photo.setThumbnailPath(toStoredManagedPath(thumbFile.getAbsolutePath(), photo.getUserId()));
                    }
                }
            }

        } catch (Exception e) {
            log.warn("重新生成缩略图失败: {}", imageFile.getName(), e);
        }
    }

    /**
     * 计算质量评分
     */
    private void calculateQualityScore(Photo photo) {
        double score = 50.0; // 基础分

        // 根据分辨率加分
        if (photo.getWidth() != null && photo.getHeight() != null) {
            int pixels = photo.getWidth() * photo.getHeight();
            if (pixels > 20000000) score += 20; // 2000万像素以上
            else if (pixels > 10000000) score += 15; // 1000万像素以上
            else if (pixels > 5000000) score += 10; // 500万像素以上
        }

        // 根据EXIF信息完整性加分
        if (photo.getCameraModel() != null) score += 5;
        if (photo.getAperture() != null) score += 5;
        if (photo.getShutterSpeed() != null) score += 5;
        if (photo.getIso() != null) score += 5;

        photo.setQualityScore(Math.min(100.0, score));
    }

    /**
     * 计算文件的 SHA-256
     */
    private String calculateSha256(File file) {
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + toRelativePath(file.getAbsolutePath()));
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("不是文件: " + toRelativePath(file.getAbsolutePath()));
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException("文件无法读取: " + toRelativePath(file.getAbsolutePath()));
        }

        try (InputStream is = Files.newInputStream(file.toPath());
             DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("SHA-256"))) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // streaming hash
            }
            byte[] digest = dis.getMessageDigest().digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("计算文件哈希失败: {}", file.getName(), e);
            throw new RuntimeException("计算文件哈希失败: " + file.getName() + " - " + e.getMessage(), e);
        }
    }

    /**
     * 计算字符串的 SHA-256（用于路径哈希等）
     */
    private String calculateSha256(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("计算字符串哈希失败", e);
            return null;
        }
    }

    /**
     * 转换为相对 base-path 的路径（用于日志显示）
     */
    private String toRelativePath(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) return absolutePath;
        try {
            String displayPath = userPathService.toDisplayPath(absolutePath, true);
            if (!absolutePath.equals(displayPath)) {
                return displayPath;
            }
        } catch (Exception e) {
        }
        String normalized = absolutePath.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private String sanitizeVisibleMessage(String message) {
        if (message == null || message.isBlank()) {
            return "系统异常";
        }
        String sanitized = userPathService.toDisplayPath(message, true);
        if (!message.equals(sanitized)) {
            return sanitized;
        }
        return toRelativePath(message);
    }

    /**
     * 计算相册目录的层级深度
     * 深度计算规则：base-path/分类/顶级相册名/1级层级/2级层级/...
     * 从"1级层级"开始计算深度，返回相对于base-path的深度（从第三级开始计数）
     */
    private int calculateAlbumDepth(Path albumPath) {
        try {
            return userPathService.calculateLogicalAlbumDepth(albumPath.toString());
        } catch (Exception e) {
            log.warn("计算相册深度失败: {}", toRelativePath(albumPath.toString()), e);
            return 0;
        }
    }

    /**
     * 递归处理目录及其子目录中的所有图片到指定相册
     */
    private void processAlbumImagesRecursively(Path directory, Album album, boolean force) {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            return;
        }
        ensureScanCanContinue(directory.toString());
        if (shouldSkipForResume(directory.toString(), true)) {
            return;
        }

        // 检查是否已经处理过这个目录
        String dirKey = directory.toString() + "/";
        if (processedFiles.contains(dirKey)) {
            log.debug("递归目录已处理过，跳过: {}", directory.getFileName());
            return;
        }
        processedFiles.add(dirKey);

        try {
            // 处理当前目录的图片
            List<File> imageFiles = findImageFiles(directory.toFile());
            for (File imageFile : imageFiles) {
                ensureScanCanContinue(imageFile.getAbsolutePath());
                if (shouldSkipForResume(imageFile.getAbsolutePath(), false)) {
                    continue;
                }
                processPhotoFile(imageFile, album, force);
            }

            // 递归处理子目录
            try (Stream<Path> subPaths = Files.list(directory)) {
                subPaths.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(".thumbnails"))
                    .forEach(p -> {
                        ensureScanCanContinue(p.toString());
                        processAlbumImagesRecursively(p, album, force);
                    });
            }
            notifyScanProgress(directory.toString(), "DIRECTORY", scanCurrent.get(), scanTotal.get());
        } catch (Exception e) {
            if (e instanceof ScanInterruptedException) {
                throw (ScanInterruptedException) e;
            }
            log.warn("递归处理目录图片失败: {}", directory, e);
        }
    }

    /**
     * 解析基础路径为绝对路径
     */
    private Path resolveBasePath() {
        return userPathService.resolvePhotoBasePath();
    }

    /**
     * 清空所有缩略图文件
     */
    @Transactional
    public Map<String, Object> clearAllThumbnails() {
        Map<String, Object> result = new HashMap<>();
        try {
            Path basePathObj = resolveCurrentFilesystemScopeRoot();
            if (basePathObj == null) {
                result.put("error", "当前存储上下文不是本地文件系统，无法清理缩略图");
                return result;
            }
            if (!Files.exists(basePathObj) || !Files.isDirectory(basePathObj)) {
                String displayPath = userPathService.toDisplayPath(basePathObj.toAbsolutePath().normalize().toString(), true);
                result.put("error", "基础路径不存在或不是目录: " + displayPath);
                return result;
            }

            // 遍历所有.thumbnails目录并删除文件
            int deletedFiles = clearThumbnailsInDirectory(basePathObj);

            // 更新数据库中的缩略图路径为null
            photoRepository.clearAllThumbnailPaths();

            result.put("message", "缩略图清理完成");
            result.put("deletedFiles", deletedFiles);
            result.put("success", true);
            log.info("缩略图清理完成，共删除 {} 个缩略图文件", deletedFiles);

        } catch (Exception e) {
            result.put("error", "清理缩略图失败: " + sanitizeVisibleMessage(e.getMessage()));
            result.put("success", false);
            log.error("清理缩略图失败", e);
        }
        return result;
    }

    /**
     * 清空所有人脸数据
     */
    @Transactional
    public Map<String, Object> clearAllFaces() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 删除所有人脸记录
            faceService.clearAllFaces();

            // 清空照片中的人脸关联（通过数据库更新）
            photoRepository.clearAllFaceAssociations();

            result.put("message", "人脸数据清理完成");
            result.put("success", true);
            log.info("人脸数据清理完成");

        } catch (Exception e) {
            result.put("error", "清理人脸数据失败: " + sanitizeVisibleMessage(e.getMessage()));
            result.put("success", false);
            log.error("清理人脸数据失败", e);
        }
        return result;
    }

    /**
     * 清空所有智能标签
     */
    @Transactional
    public Map<String, Object> clearAllSmartTags() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 删除所有智能标签关联（保留手动添加的标签）
            tagService.clearAllSmartTags();

            result.put("message", "智能标签清理完成");
            result.put("success", true);
            log.info("智能标签清理完成");

        } catch (Exception e) {
            result.put("error", "清理智能标签失败: " + sanitizeVisibleMessage(e.getMessage()));
            result.put("success", false);
            log.error("清理智能标签失败", e);
        }
        return result;
    }

    /**
     * 清理已删除文件的残留数据
     * 扫描数据库中的所有照片记录，检查文件是否还存在，不存在的记录及其关联数据将被删除
     */
    @Transactional
    public Map<String, Object> cleanupOrphanedData() {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("开始清理已删除文件的残留数据...");

            // 获取所有照片记录
            List<Long> photosToDelete = new ArrayList<>();
            AtomicInteger processedCount = new AtomicInteger();
            AtomicInteger orphanedCount = new AtomicInteger();

            forEachPhotoInCurrentScope(photo -> {
                processedCount.incrementAndGet();

                // 检查文件是否还存在
                if (photo.getOriginalPath() == null || photo.getOriginalPath().isEmpty()) {
                    log.warn("照片记录原始路径为空，标记为孤儿记录: photoId={}", photo.getId());
                    photosToDelete.add(photo.getId());
                    orphanedCount.incrementAndGet();
                    return;
                }

                try {
                    File imageFile = resolveOriginalFile(photo);
                    if (!imageFile.exists()) {
                        log.info("图片文件不存在，标记为孤儿记录: {} (photoId={})",
                            toRelativePath(photo.getOriginalPath()), photo.getId());
                        photosToDelete.add(photo.getId());
                        orphanedCount.incrementAndGet();
                    }
                } catch (IOException e) {
                    log.warn("解析照片文件失败，标记为孤儿记录: photoId={}, error={}", photo.getId(), e.getMessage());
                    photosToDelete.add(photo.getId());
                    orphanedCount.incrementAndGet();
                }
            });

            // 删除孤儿照片记录（级联删除会自动清理Face和Tag关联）
            if (!photosToDelete.isEmpty()) {
                log.info("删除 {} 个孤儿照片记录", photosToDelete.size());
                for (Long photoId : photosToDelete) {
                    try {
                        photoRepository.deleteById(photoId);
                    } catch (Exception e) {
                        log.warn("删除孤儿照片记录失败: photoId={}, error={}", photoId, e.getMessage());
                    }
                }
            }

            // 检查并清理空的相册或路径不存在的相册
            AtomicInteger emptyAlbumsDeleted = new AtomicInteger();
            AtomicInteger nonExistentPathAlbumsDeleted = new AtomicInteger();

            forEachAlbumInCurrentScope(album -> {
                try {
                    if (album.getPath() != null && !album.getPath().isEmpty()) {
                        Optional<Path> resolvedAlbumDir = userPathService.tryResolveLocalStoredPhotoPath(album.getPath());
                        if (resolvedAlbumDir.isEmpty()) {
                            try {
                                resolvedAlbumDir = Optional.ofNullable(userPathService.normalizeAbsolutePath(album.getPath()));
                            } catch (Exception ignored) {
                                resolvedAlbumDir = Optional.empty();
                            }
                        }
                        if (resolvedAlbumDir.isPresent() && !resolvedAlbumDir.get().toFile().exists()) {
                            log.info("相册文件夹不存在，删除相册记录: {} (albumId={})", toRelativePath(album.getPath()), album.getId());
                            albumRepository.deleteById(album.getId());
                            nonExistentPathAlbumsDeleted.incrementAndGet();
                            return;
                        }
                    }

                    long photoCount = photoRepository.countByAlbumId(album.getId());
                    if (photoCount == 0) {
                        log.info("相册 {} 已没有照片，删除相册记录", album.getId());
                        albumRepository.deleteById(album.getId());
                        emptyAlbumsDeleted.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.warn("检查相册失败: albumId={}, error={}", album.getId(), e.getMessage());
                }
            });

            result.put("message", String.format("清理完成！处理了 %d 个照片记录，发现 %d 个孤儿记录，删除了 %d 个空相册和 %d 个路径不存在的相册",
                processedCount.get(), orphanedCount.get(), emptyAlbumsDeleted.get(), nonExistentPathAlbumsDeleted.get()));
            result.put("processedPhotos", processedCount.get());
            result.put("orphanedPhotos", orphanedCount.get());
            result.put("emptyAlbumsDeleted", emptyAlbumsDeleted.get());
            result.put("nonExistentPathAlbumsDeleted", nonExistentPathAlbumsDeleted.get());
            result.put("success", true);

            log.info("清理完成！处理了 {} 个照片记录，发现 {} 个孤儿记录，删除了 {} 个空相册和 {} 个路径不存在的相册",
                processedCount.get(), orphanedCount.get(), emptyAlbumsDeleted.get(), nonExistentPathAlbumsDeleted.get());

        } catch (Exception e) {
            result.put("error", "清理残留数据失败: " + sanitizeVisibleMessage(e.getMessage()));
            result.put("success", false);
            log.error("清理残留数据失败", e);
        }
        return result;
    }

    /**
     * 递归删除目录中的缩略图文件
     */
    private int clearThumbnailsInDirectory(Path directory) throws IOException {
        int deletedCount = 0;

        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> thumbnailFiles = paths
                .filter(Files::isRegularFile)
                .filter(path -> {
                    String fileName = path.getFileName().toString();
                    // 删除所有缩略图文件（包括新三级缩略图系统）
                    return fileName.endsWith("_thumb.jpg") ||
                           fileName.endsWith("_small.jpg") ||
                           fileName.endsWith("_medium.jpg") ||
                           fileName.endsWith("_large.jpg");
                })
                .collect(Collectors.toList());

            for (Path thumbnailFile : thumbnailFiles) {
                try {
                    Files.delete(thumbnailFile);
                    deletedCount++;
                } catch (Exception e) {
                    log.warn("删除缩略图文件失败: {}", thumbnailFile, e);
                }
            }
        }

        return deletedCount;
    }

    /**
     * 批量更新所有照片的时间信息
     * 重新从EXIF信息中提取拍摄时间
     */
    @Async
    @Transactional
    public void updateAllPhotoTimesAsync() {
        log.info("开始批量更新所有照片的时间信息...");
        AtomicInteger updatedCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);

        forEachPhotoInCurrentScope(photo -> {
            try {
                // 获取照片文件路径
                String originalPath = photo.getOriginalPath();
                if (originalPath == null || originalPath.isEmpty()) {
                    log.warn("照片 {} 没有原始路径，跳过", photo.getId());
                    processedCount.incrementAndGet();
                    return;
                }

                var resolvedPath = userPathService.tryResolveLocalStoredPhotoPath(originalPath);
                if (resolvedPath.isEmpty()) {
                    log.warn("照片路径不是本地可解析路径，跳过更新时间: {}", originalPath);
                    processedCount.incrementAndGet();
                    return;
                }

                File imageFile = resolvedPath.get().toFile();
                if (!imageFile.exists()) {
                    log.warn("照片文件不存在: {}", originalPath);
                    processedCount.incrementAndGet();
                    return;
                }

                // 提取EXIF信息并更新时间
                extractExifData(imageFile, photo);
                photoRepository.save(photo);

                updatedCount.incrementAndGet();
                processedCount.incrementAndGet();

                // 每处理100张照片记录一次日志
                if (processedCount.get() % 100 == 0) {
                    log.info("已处理 {} 张照片，更新了 {} 张", processedCount.get(), updatedCount.get());
                }

            } catch (Exception e) {
                log.error("更新照片 {} 时间失败: {}", photo.getId(), e.getMessage());
                processedCount.incrementAndGet();
            }
        });

        log.info("批量更新照片时间完成，总共处理 {} 张照片，成功更新 {} 张", processedCount.get(), updatedCount.get());
    }

    /**
     * 获取后台异步任务的状态与日志
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        Map<String, Object> resp = new HashMap<>();
        TaskStatus ts = tasks.get(taskId);
        if (ts == null) {
            resp.put("found", false);
            resp.put("taskId", taskId);
            return resp;
        }
        resp.put("found", true);
        resp.put("taskId", ts.taskId);
        resp.put("taskName", ts.taskName);
        resp.put("status", ts.status);
        resp.put("current", ts.current);
        resp.put("total", ts.total);
        resp.put("complete", ts.complete);
        resp.put("startTime", ts.startTime != null ? ts.startTime.toString() : null);
        resp.put("endTime", ts.endTime != null ? ts.endTime.toString() : null);
        resp.put("logs", new ArrayList<>(ts.logs));
        return resp;
    }

    public Map<String, Object> getAsyncTaskOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> recentTasks = tasks.values().stream()
            .sorted((left, right) -> compareDateTimeDesc(left.endTime != null ? left.endTime : left.startTime, right.endTime != null ? right.endTime : right.startTime))
            .limit(8)
            .map(this::toAsyncTaskMap)
            .collect(Collectors.toList());

        long runningCount = tasks.values().stream()
            .filter(task -> task != null && !task.complete && !"failed".equalsIgnoreCase(task.status) && !"stopped".equalsIgnoreCase(task.status))
            .count();

        Map<String, Object> scanStatus = getScanStatus();
        result.put("threadType", "PHOTO_ASYNC");
        result.put("label", "图片处理异步任务");
        result.put("runningTaskCount", runningCount);
        result.put("scanning", scanStatus.get("scanning"));
        result.put("activeScanCount", activeScanCount.get());
        result.put("scanStatus", scanStatus);
        result.put("recentTasks", recentTasks);
        return result;
    }

    private Map<String, Object> toAsyncTaskMap(TaskStatus task) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("taskId", task.taskId);
        item.put("taskName", task.taskName);
        item.put("status", task.status);
        item.put("current", task.current);
        item.put("total", task.total);
        item.put("complete", task.complete);
        item.put("stopped", task.stopped);
        item.put("progressPercent", task.total > 0 ? Math.min(100, Math.max(0, (int) Math.round(task.current * 100.0 / task.total))) : (task.complete ? 100 : 0));
        item.put("startTime", task.startTime);
        item.put("endTime", task.endTime);
        item.put("latestLog", task.logs.isEmpty() ? null : task.logs.get(task.logs.size() - 1));
        return item;
    }

    private int compareDateTimeDesc(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    /**
     * 停止任务
     */
    public Map<String, Object> stopTask(String taskId) {
        Map<String, Object> resp = new HashMap<>();
        TaskStatus ts = tasks.get(taskId);
        if (ts == null) {
            resp.put("found", false);
            resp.put("taskId", taskId);
            return resp;
        }
        ts.stopped = true;
        ts.status = "stopped";
        ts.complete = true;
        ts.endTime = LocalDateTime.now();
        ts.logs.add("任务已被用户停止");
        log.info("任务 {} 已停止", taskId);
        resp.put("found", true);
        resp.put("taskId", taskId);
        resp.put("status", "stopped");
        return resp;
    }

    /**
     * 同步版本：批量更新所有照片的时间信息
     */
    @Transactional
    public Map<String, Object> updateAllPhotoTimes() {
        log.info("开始批量更新所有照片的时间信息...");
        AtomicInteger updatedCount = new AtomicInteger();
        AtomicInteger processedCount = new AtomicInteger();

        forEachPhotoInCurrentScope(photo -> {
            try {
                // 获取照片文件路径
                String originalPath = photo.getOriginalPath();
                if (originalPath == null || originalPath.isEmpty()) {
                    log.warn("照片 {} 没有原始路径，跳过", photo.getId());
                    processedCount.incrementAndGet();
                    return;
                }

                var resolvedPath = userPathService.tryResolveLocalStoredPhotoPath(originalPath);
                if (resolvedPath.isEmpty()) {
                    log.warn("照片路径不是本地可解析路径，跳过更新时间: {}", originalPath);
                    processedCount.incrementAndGet();
                    return;
                }

                File imageFile = resolvedPath.get().toFile();
                if (!imageFile.exists()) {
                    log.warn("照片文件不存在: {}", originalPath);
                    processedCount.incrementAndGet();
                    return;
                }

                // 提取EXIF信息并更新时间
                extractExifData(imageFile, photo);
                photoRepository.save(photo);

                updatedCount.incrementAndGet();
                int current = processedCount.incrementAndGet();

                if (current % 100 == 0) {
                    log.info("已处理 {} 张照片，更新了 {} 张", current, updatedCount.get());
                }

            } catch (Exception e) {
                log.error("更新照片 {} 时间失败: {}", photo.getId(), e.getMessage());
                processedCount.incrementAndGet();
            }
        });

        log.info("批量更新照片时间完成，总共处理 {} 张照片，成功更新 {} 张", processedCount.get(), updatedCount.get());

        Map<String, Object> result = new HashMap<>();
        result.put("totalProcessed", processedCount.get());
        result.put("totalUpdated", updatedCount.get());
        result.put("message", String.format("成功处理 %d 张照片，更新了 %d 张照片的时间信息", processedCount.get(), updatedCount.get()));

        return result;
    }

    /**
     * 从文件路径中解析拍摄时间（优先最深层文件夹）
     * 类似相册时间的逻辑，但针对单个文件路径
     */
    private LocalDateTime parseDateFromFilePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return null;
        }

        try {
            // 获取相对于base-path的路径部分
            String relativePath = getRelativeFilePath(filePath);
            if (relativePath == null || relativePath.isEmpty()) {
                return null;
            }

            // 分割路径为各级目录
            String[] pathParts = relativePath.split("[/\\\\]");
            if (pathParts.length == 0) {
                return null;
            }

            // 从最深层开始向上查找（优先使用最接近文件的文件夹名称）
            for (int i = pathParts.length - 1; i >= 0; i--) {
                String folderName = pathParts[i].trim();
                if (!folderName.isEmpty()) {
                    LocalDateTime parsedTime = parseDateFromFolderName(folderName);
                    if (parsedTime != null) {
                        return parsedTime;
                    }
                }
            }

        } catch (Exception e) {
            log.debug("从文件路径解析时间失败: {}", filePath, e);
        }

        return null;  // 没有找到匹配的时间格式
    }

    /**
     * 从相册路径中解析时间（优先当前文件夹名，向上查找父目录）
     * 用于新建相册时设置 albumNameDate
     */
    private LocalDateTime parseDateFromAlbumPath(String albumPath) {
        if (albumPath == null || albumPath.trim().isEmpty()) {
            return null;
        }

        try {
            // 获取相对于base-path的路径部分
            String relativePath = getRelativeFilePath(albumPath);
            if (relativePath == null || relativePath.isEmpty()) {
                return null;
            }

            // 分割路径为各级目录
            String[] pathParts = relativePath.split("[/\\\\]");
            if (pathParts.length == 0) {
                return null;
            }

            // 从最深层开始向上查找（优先使用当前相册名称的时间）
            for (int i = pathParts.length - 1; i >= 0; i--) {
                String folderName = pathParts[i].trim();
                if (!folderName.isEmpty()) {
                    LocalDateTime parsedTime = parseDateFromFolderName(folderName);
                    if (parsedTime != null) {
                        return parsedTime;
                    }
                }
            }

        } catch (Exception e) {
            log.debug("从相册路径解析时间失败: {}", albumPath, e);
        }

        return null;  // 没有找到匹配的时间格式
    }

    /**
     * 计算相册的日期：优先最晚EXIF时间，否则用最晚文件修改时间
     * 用于当路径解析不出日期时
     */
    private LocalDateTime calculateAlbumDateFromPhotos(Long albumId) {
        try {
            // 优先使用最晚的EXIF拍摄时间
            Optional<Photo> photoWithMaxTakenAt = photoRepository.findTopByAlbumIdOrderByTakenAtDesc(albumId);
            if (photoWithMaxTakenAt.isPresent()) {
                Photo photo = photoWithMaxTakenAt.get();
                if (photo.getTakenAt() != null) {
                    log.debug("相册 {} 使用最晚EXIF时间: {}", albumId, photo.getTakenAt());
                    return photo.getTakenAt();
                }
            }

            // 没有EXIF时间，使用最晚的文件修改时间
            Optional<Photo> photoWithMaxCreatedAt = photoRepository.findTopByAlbumIdOrderByCreatedAtDesc(albumId);
            if (photoWithMaxCreatedAt.isPresent()) {
                Photo photo = photoWithMaxCreatedAt.get();
                if (photo.getCreatedAt() != null) {
                    log.debug("相册 {} 使用最晚文件修改时间: {}", albumId, photo.getCreatedAt());
                    return photo.getCreatedAt();
                }
            }
        } catch (Exception e) {
            log.warn("计算相册 {} 日期失败: {}", albumId, e.getMessage());
        }
        return null;
    }

    /**
     * 获取相对于base-path的文件路径
     */
    private String getRelativeFilePath(String fullPath) {
        if (fullPath == null) {
            return null;
        }

        String tenantRelativePath = userPathService.extractTenantRelativePhotoPath(fullPath);
        if (tenantRelativePath != null) {
            return tenantRelativePath;
        }
        return null;  // 如果都匹配失败，返回null
    }

    /**
     * 从单个文件夹名称中解析时间（复用AlbumService的逻辑）
     */
    private LocalDateTime parseDateFromFolderName(String folderName) {
        if (folderName == null || folderName.trim().isEmpty()) {
            return null;
        }

        // 定义多种时间格式的正则表达式和对应的DateTimeFormatter
        List<Object[]> patterns = new ArrayList<>();

        // 高优先级：带时间的格式（更具体的匹配）
        patterns.add(new Object[]{"(\\d{4})[\\.-](\\d{1,2})[\\.-](\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})", "yyyy-MM-dd HH:mm:ss"}); // 2023.08.08 10:30:45
        patterns.add(new Object[]{"(\\d{4})[\\.-](\\d{1,2})[\\.-](\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2})", "yyyy-MM-dd HH:mm"}); // 2023.08.08 10:30
        patterns.add(new Object[]{"(\\d{4})/(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})", "yyyy-MM-dd HH:mm:ss"}); // 2023/08/08 10:30:45
        patterns.add(new Object[]{"(\\d{4})/(\\d{1,2})/(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2})", "yyyy-MM-dd HH:mm"}); // 2023/08/08 10:30
        patterns.add(new Object[]{"(\\d{4})年(\\d{1,2})月(\\d{1,2})日\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2})", "yyyy-MM-dd HH:mm:ss"}); // 2023年08月08日 10:30:45
        patterns.add(new Object[]{"(\\d{4})年(\\d{1,2})月(\\d{1,2})日\\s+(\\d{1,2}):(\\d{1,2})", "yyyy-MM-dd HH:mm"}); // 2023年08月08日 10:30

        // 中优先级：只包含日期的格式（按特殊性和长度排序）
        patterns.add(new Object[]{"(\\d{4})年(\\d{1,2})月(\\d{1,2})日", "yyyy-MM-dd"}); // 2023年08月08日 (最特殊)
        patterns.add(new Object[]{"(\\d{8})", "yyyyMMdd"}); // 20230808 (紧凑格式，8位数字)
        patterns.add(new Object[]{"(\\d{4})[\\.-](\\d{1,2})[\\.-](\\d{1,2})", "yyyy-MM-dd"}); // 2023.08.08 或 2023-08-08 (年月日)
        patterns.add(new Object[]{"(\\d{4})/(\\d{1,2})/(\\d{1,2})", "yyyy-MM-dd"}); // 2023/08/08 (年月日)
        patterns.add(new Object[]{"(\\d{1,2})[\\.-/](\\d{1,2})[\\.-/](\\d{4})", "MM-dd-yyyy"}); // 08-08-2023 或 08/08/2023 或 08.08.2023 (日月年)
        patterns.add(new Object[]{"(\\d{1,2})[\\.-/](\\d{1,2})[\\.-/](\\d{2})", "MM-dd-yy"}); // 08-08-23 (两位数年份，日月年)

        // 低优先级：年.月或年-月格式（无日期，默认该月1号）
        patterns.add(new Object[]{"(\\d{4})[\\.-](\\d{1,2})$", "yyyy-MM"}); // 2024.1 或 2024-1 或 2024.01 或 2024-01 (年.月，无日期，默认该月1号)
        patterns.add(new Object[]{"(\\d{4})年(\\d{1,2})月$", "yyyy-MM"}); // 2024年1月 (年.月，无日期，默认该月1号)

        String normalizedName = folderName.trim();

        for (Object[] pattern : patterns) {
            Pattern p = Pattern.compile((String) pattern[0]);
            Matcher m = p.matcher(normalizedName);
            String patternStr = (String) pattern[1];

            if (m.find()) {
                try {
                    // 根据不同的模式构建标准格式的日期字符串
                    StringBuilder dateStr = new StringBuilder();

                    if (patternStr.equals("yyyyMMdd")) {
                        // 紧凑格式：20230808 -> 2023-08-08
                        String datePart = m.group(1);
                        if (datePart.length() == 8) {
                            dateStr.append(datePart.substring(0, 4)).append("-")
                                   .append(datePart.substring(4, 6)).append("-")
                                   .append(datePart.substring(6, 8));
                        }
                    } else if (patternStr.equals("MM-dd-yyyy") || patternStr.equals("MM/dd/yyyy") ||
                               patternStr.equals("MM-dd-yy") || patternStr.equals("MM/dd/yyyy")) {
                        // 日月年格式：08-08-2023 -> 2023-08-08
                        dateStr.append(m.group(3)).append("-")  // 年
                               .append(String.format("%02d", Integer.parseInt(m.group(1)))).append("-")  // 月
                               .append(String.format("%02d", Integer.parseInt(m.group(2))));  // 日
                    } else if (patternStr.equals("yyyy-MM")) {
                        // 年.月或年-月格式（无日期，默认该月1号）：2024.1 -> 2024-01-01
                        dateStr.append(m.group(1)).append("-")  // 年
                               .append(String.format("%02d", Integer.parseInt(m.group(2)))).append("-")  // 月
                               .append("01");  // 默认该月1号
                    } else {
                        // 标准年月日格式
                        dateStr.append(m.group(1)).append("-")  // 年
                               .append(String.format("%02d", Integer.parseInt(m.group(2)))).append("-")  // 月
                               .append(String.format("%02d", Integer.parseInt(m.group(3))));  // 日

                        // 检查是否有时间部分（基于原始pattern判断）
                        if (patternStr.contains("HH:mm")) {
                            if (m.groupCount() >= 5) {
                                dateStr.append(" ")
                                       .append(String.format("%02d", Integer.parseInt(m.group(4)))).append(":")
                                       .append(String.format("%02d", Integer.parseInt(m.group(5))));

                                if (m.groupCount() >= 6 && m.group(6) != null) {
                                    dateStr.append(":").append(String.format("%02d", Integer.parseInt(m.group(6))));
                                } else {
                                    dateStr.append(":00");
                                }
                            } else {
                                // 如果正则表达式应该有时间但没有匹配，记录错误
                                log.warn("时间格式pattern '{}' 应该包含时间但groupCount只有{}", patternStr, m.groupCount());
                            }
                        }
                    }

                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(patternStr);

                    // 根据模式确定如何解析
                    if (patternStr.contains("HH:mm")) {
                        // 有时间的格式，直接解析为LocalDateTime
                        return LocalDateTime.parse(dateStr.toString(), formatter);
                    } else if (patternStr.equals("yyyy-MM")) {
                        // 年.月格式，使用yyyy-MM-dd格式解析
                        return LocalDateTime.parse(dateStr.toString(), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    } else {
                        // 只有日期的格式，解析为LocalDate然后转换为LocalDateTime
                        java.time.LocalDate date = java.time.LocalDate.parse(dateStr.toString(), formatter);
                        return date.atStartOfDay();
                    }
                } catch (java.time.format.DateTimeParseException | NumberFormatException e) {
                    // 解析失败，继续尝试下一个模式
                    continue;
                }
            }
        }

        return null;  // 没有找到匹配的时间格式
    }

    /**
     * 判断 EXIF 标签是否应该被过滤掉（ICC Profile、超长值等无用大字段）
     */
    public static boolean shouldFilterExifTag(String tagName, String tagValue) {
        if (tagName == null) return true;
        // 包含关键词的标签
        if (tagName.contains("TRC") || tagName.contains("Colorant")) return true;
        // 精确匹配的无用标签
        switch (tagName) {
            case "Media White Point":
            case "Thumbnail Data":
            case "Padding":
            case "Profile Description":
            case "Technology":
            case "Rendering Intent":
            case "Profile Size":
            case "CMM Type":
            case "Profile Date/Time":
            case "Profile Connection Space":
            case "Primary Platform":
            case "Device Manufacturer":
            case "XYZ values":
            case "Tag Count":
            case "Signature":
            case "Copyright":
            case "Profile Class":
            case "Color space":
            case "Connection Space":
                return true;
            default:
                break;
        }
        // 超长值
        if (tagValue != null && tagValue.length() > 256) return true;
        return false;
    }
}
