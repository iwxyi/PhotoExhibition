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
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
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

@Slf4j
@Service
public class PhotoScanService {

    // 简单的任务状态记录结构（用于后台异步任务的进度与日志查询）
    private static class TaskStatus {
        public String taskId;
        public String status;
        public final List<String> logs = Collections.synchronizedList(new ArrayList<>());
        public int current = 0;
        public int total = 0;
        public boolean complete = false;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
    }

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
        this.objectMapper = objectMapper;
    }
    
    private void createTask(String taskId, String initialMessage) {
        TaskStatus ts = new TaskStatus();
        ts.taskId = taskId;
        ts.status = "running";
        ts.startTime = LocalDateTime.now();
        if (initialMessage != null) ts.logs.add(LocalDateTime.now().toString() + " " + initialMessage);
        tasks.put(taskId, ts);
    }

    private void appendTaskLog(String taskId, String msg) {
        if (taskId == null) return;
        TaskStatus ts = tasks.get(taskId);
        if (ts != null) {
            ts.logs.add(LocalDateTime.now().toString() + " " + msg);
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
            if (finalMessage != null) ts.logs.add(LocalDateTime.now().toString() + " " + finalMessage);
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
            List<Photo> photos = photoRepository.findAll();
            log.info("开始初始化照片处理状态，总计 {} 张", photos.size());
            int updatedCount = 0;

            for (Photo photo : photos) {
                if (photo.getProcessingStatus() == ProcessingStatus.PENDING) {
                    // 对于没有处理状态的照片，假设它们已经完成处理
                    photo.setProcessingStatus(ProcessingStatus.COMPLETED);
                    photoRepository.save(photo);
                    updatedCount++;
                }
            }

            log.info("初始化照片处理状态完成，更新了 {} 张照片", updatedCount);
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
            List<Photo> photos = photoRepository.findAll();
            log.info("开始回填哈希，总计 {} 张", photos.size());
            for (Photo photo : photos) {
                try {
                    if (photo.getContentHash() != null && !photo.getContentHash().isEmpty()) {
                        continue;
                    }
                    File file = new File(photo.getOriginalPath());
                    if (!file.exists()) {
                        log.warn("文件不存在，跳过哈希回填: {}", photo.getOriginalPath());
                        continue;
                    }
                    String hash = calculateSha256(file);
                    photo.setContentHash(hash);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    log.warn("回填哈希失败: {}", photo.getOriginalPath(), e);
                }
            }
            log.info("哈希回填完成");
        } catch (Exception e) {
            log.error("回填哈希任务失败", e);
        }
    }

    /**
     * 定时扫描文件夹
     * initialDelay设置为扫描间隔时间，确保应用启动后不会立即执行第一次扫描
     */
    @Scheduled(fixedDelayString = "${photo.scan.scan-interval}000", initialDelayString = "${photo.scan.scan-interval}000")
    @Async
    public void scheduledScan() {
        // 检查是否已有扫描在进行
        if (isScanning.get() || activeScanCount.get() > 0) {
            log.info("定时扫描跳过：已有扫描任务正在执行 (activeScanCount={}, isScanning={})", 
                    activeScanCount.get(), isScanning.get());
            return;
        }
        
        log.info("定时扫描: {}", basePath);
        scanDirectory(basePath);
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

                    File imageFile = new File(photo.getOriginalPath());
                    if (!imageFile.exists()) {
                        log.warn("照片文件不存在: {}", photo.getOriginalPath());
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
            Path basePath = resolveBasePath();
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
            long databaseTotal = photoRepository.count();

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
            result.put("error", e.getMessage());
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

            // 获取数据库中的所有照片路径
            List<Photo> allPhotos = photoRepository.findAll();
            Set<String> databasePaths = allPhotos.stream()
                .map(Photo::getOriginalPath)
                .collect(Collectors.toSet());

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
            analysis.put("error", e.getMessage());
        }

        return analysis;
    }

    /**
     * 获取文件系统中所有照片的路径
     */
    private Set<String> getAllFilesystemPhotoPaths() {
        Set<String> paths = new HashSet<>();

        try {
            Path basePath = resolveBasePath();
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
                appendTaskLog(taskId, "筛选选项更新失败: " + e.getMessage());
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
                appendTaskLog(taskId, "筛选选项更新失败: " + e.getMessage());
                // 不抛出异常，避免影响整体任务
            }
            completeTask(taskId, "已完成");
            log.info("异步任务 {}: 更新完成", taskId);
        } catch (Exception e) {
            appendTaskLog(taskId, "发生异常: " + e.getMessage());
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
        log.info("扫描服务初始化: {}", basePath);

        // 初始化现有照片的处理状态
        initializeProcessingStatusAsync();

        // 检查是否有需要重新处理的照片
        checkAndRetryIncompletePhotos();

        // 检查是否需要初始化扫描：如果数据库中没有任何相册，则执行一次扫描
        try {
            long albumCount = albumRepository.count();
            if (albumCount == 0) {
                log.info("数据库中没有任何相册，执行初始化扫描");
                scanDirectoryAsync(basePath);
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
            long failedCount = photoRepository.countFailedPhotos();
            long incompleteCount = photoRepository.countIncompletePhotos();

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
        scanDirectoryInternal(directoryPath, false);
    }

    /**
     * 强制重新扫描（重新处理所有图片，重建缩略图、人脸、标签）
     */
    @Transactional
    public void rescanDirectory(String directoryPath) {
        scanDirectoryInternal(directoryPath, true);
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
        File imageFile = new File(photo.getOriginalPath());
        if (!imageFile.exists()) {
            result.put("error", "文件不存在: " + photo.getOriginalPath());
            return result;
        }
        // 调用现有人脸检测流程（单张重建时开启详细日志）
        List<Face> faces = faceService.detectAndSaveFaces(imageFile, photo, true);
        int count = faces == null ? 0 : faces.size();

        // 安全更新关联集合，避免 orphan 触发
        try {
            List<Face> managedFaces = faces == null ? Collections.emptyList() : faces;
            if (photo.getFaces() != null) {
                photo.getFaces().clear();
                photo.getFaces().addAll(managedFaces);
            } else {
                photo.setFaces(new ArrayList<>(managedFaces));
            }
            photoRepository.save(photo);
        } catch (Exception e) {
            log.warn("更新照片人脸关联失败: photoId={}, err={}", photoId, e.getMessage());
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
            long totalPhotos = photoRepository.count();
            long failedCount = photoRepository.countFailedPhotos();
            long completedCount = photoRepository.countPhotosByProcessingStatus(ProcessingStatus.COMPLETED);
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

                // 根据扫描状态设置进度显示
                boolean isCurrentlyScanning = isScanning.get() || activeScanCount.get() > 0;
                if (isCurrentlyScanning) {
                    // 扫描进行中：显示本次遍历的照片数量 / 文件系统总照片数量
                    int traversedThisScan = scanCurrent.get();  // 本次扫描已遍历的照片数量
                    status.put("current", traversedThisScan);
                    status.put("total", filesystemTotal);
                    status.put("scanMode", "scanning"); // 表示正在扫描文件
                } else {
                    // 扫描未进行：显示已扫描数量 / 总的照片数量
                    status.put("current", totalPhotos);  // 已扫描数量（数据库中的照片）
                    status.put("total", filesystemTotal); // 总的照片数量（文件系统中的照片）
                    status.put("scanMode", "completed"); // 表示扫描已完成
                }

                status.put("filesystemStats", Map.of(
                    "total", filesystemTotal,
                    "scanned", totalPhotos,
                    "unscanned", Math.max(0, filesystemTotal - totalPhotos)
                ));
            } catch (Exception e) {
                log.debug("统计文件系统照片数量失败", e);
                // 如果无法获取文件系统统计，则使用数据库统计作为fallback
                status.put("current", totalPhotos);
                status.put("total", totalPhotos); // fallback：已扫描数量作为总数
                status.put("scanMode", "fallback");
                status.put("filesystemStats", Map.of("error", "无法获取文件系统统计"));
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
            List<Photo> failedPhotos = photoRepository.findFailedPhotos();
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

                    File imageFile = new File(photo.getOriginalPath());
                    if (!imageFile.exists()) {
                        log.warn("照片文件不存在，标记为失败: {}", photo.getOriginalPath());
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
                        log.info("重试成功: {}", photo.getOriginalPath());
                    } else {
                        retryFailCount++;
                        log.warn("重试仍然失败: {}", photo.getOriginalPath());
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
            atmosphereAnalysisService.analyzeAllAlbumsAtmosphere();
            atmosphereEffectsService.analyzeAllAlbumsEffects();
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
            throw new RuntimeException("设置特效失败: " + e.getMessage());
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
            throw new RuntimeException("获取特效配置失败: " + e.getMessage());
        }

        return result;
    }

    private void scanDirectoryInternal(String directoryPath, boolean force) {
        if (directoryPath == null || directoryPath.isEmpty()) {
            directoryPath = basePath;
        }
        activeScanCount.incrementAndGet();
        try {
            // 只有在没有其他扫描进行时才重置计数器和设置扫描状态
            if (activeScanCount.get() == 1) {
                isScanning.set(true);
                scanCurrent.set(0);
                scanTotal.set(0);
                lastScanStart = LocalDateTime.now();
            } else {
                // 如果有并发扫描，为了避免计数混乱，我们不显示进度
                // 或者可以为每个扫描任务分配独立的计数器
                log.warn("检测到并发扫描 (activeScanCount={}), 进度显示可能不准确", activeScanCount.get());
            }

            Path path;
            if (directoryPath == null || directoryPath.isEmpty() || directoryPath.equals(basePath)) {
                // 使用默认基础路径
                path = resolveBasePath();
            } else {
                // 处理自定义路径
                path = Paths.get(directoryPath);
            
            // 处理相对路径：如果是相对路径，转换为绝对路径
            if (!path.isAbsolute()) {
                // 获取项目根目录
                // 方法1: 从当前工作目录推断
                String projectRoot = System.getProperty("user.dir");
                
                // 如果当前在backend目录，需要回到项目根目录
                if (projectRoot.endsWith("backend")) {
                    projectRoot = new File(projectRoot).getParent();
                }
                
                // 方法2: 尝试从类路径推断项目根目录（更可靠）
                // 如果方法1失败，可以尝试这个方法
                if (projectRoot == null || projectRoot.isEmpty()) {
                    try {
                        String classPath = PhotoScanService.class.getProtectionDomain()
                            .getCodeSource().getLocation().getPath();
                        // 从target/classes或jar文件推断项目根目录
                        if (classPath.contains("target/classes")) {
                            projectRoot = new File(classPath).getParentFile().getParentFile().getParent();
                        }
                    } catch (Exception e) {
                        log.warn("无法从类路径推断项目根目录", e);
                    }
                }
                
                // 处理以 ./ 开头的相对路径
                String cleanPath = directoryPath.startsWith("./") 
                    ? directoryPath.substring(2) 
                    : directoryPath;
                
                path = Paths.get(projectRoot, cleanPath).toAbsolutePath().normalize();
                }
            }
            
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("目录不存在: " + path);
            }
            
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException("路径不是文件夹: " + path);
            }

            // 取消优先处理逻辑，所有照片都在正常的目录遍历中处理

            // 预统计所有要扫描的文件总数
            long totalFilesToScan = countPhotosInFilesystem();
            log.info("文件系统统计结果: {} 张照片", totalFilesToScan);
            log.info("本次扫描将处理 {} 张照片", totalFilesToScan);

            // 重置扫描计数器，为扫描做准备
            // 注意：只有在没有并发扫描时才重置，避免干扰其他扫描的进度
            if (activeScanCount.get() == 1) {
                scanCurrent.set(0);  // 从0开始计数已遍历的文件
                scanTotal.set((int) totalFilesToScan);    // 设置总数，不限制上限
                processedFiles.clear();  // 清空已处理文件跟踪
                log.info("设置扫描总数: {}", totalFilesToScan);
            }

            // 预统计总数用于进度显示
            // 注意：这里统计所有文件，包括可能被递归处理的超过层级的目录中的文件
            try (Stream<Path> paths = Files.walk(path)) {
                Set<String> supportedSet = Arrays.stream(supportedFormats.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
                int total = (int) paths
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

                // 设置扫描总数（只有第一个扫描任务才设置）
                if (activeScanCount.get() == 1) {
                    scanTotal.set(total);
                    scanCurrent.set(0); // 从0开始计数
                } else {
                    // 并发情况下，累积总数，但这可能不准确
                    scanTotal.addAndGet(total);
                }

                log.info("预统计待扫描图片数量: {}", total);
            } catch (Exception e) {
                log.warn("统计待扫描图片数量失败: {}", e.getMessage());
                if (activeScanCount.get() == 1) {
                    scanTotal.set(0);
                    scanCurrent.set(0);
                }
            }

            // 扫描所有子文件夹，跳过.thumbnails目录
            try (Stream<Path> paths = Files.walk(path)) {
                paths.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(".thumbnails"))  // 跳过.thumbnails目录
                    .forEach(p -> processAlbumDirectory(p, force));
            }
        } catch (Exception e) {
            log.error("扫描目录失败: {}", directoryPath, e);
            throw new RuntimeException("扫描目录失败: " + (e.getMessage() == null ? directoryPath : e.getMessage()), e);
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
            }
        }
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
                // 查找是否已存在该目录的相册记录，如果存在则删除
                String albumPathStr = albumPath.toString();
                String albumPathHash = calculateSha256(albumPathStr);
                Optional<Album> existingAlbum = albumRepository.findByPathHash(albumPathHash);

                if (existingAlbum.isPresent()) {
                    Album albumToDelete = existingAlbum.get();
                    String relativePath = toRelativePath(albumToDelete.getPath());
                    log.info("删除超出层级的相册 {} (深度: {}, 最大深度: {})", relativePath, depth, maxDepth);

                    // 将该相册的照片移动到父相册（如果有父相册）
                    if (parentAlbum != null) {
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
                                photo.setAlbumId(parentAlbum.getId());
                                photoRepository.save(photo);
                                totalMoved++;
                            }

                            pageNumber++;
                            if (pageNumber >= photoPage.getTotalPages()) {
                                break;
                            }
                        }

                        String parentRelativePath = toRelativePath(parentAlbum.getPath());
                        log.info("已将 {} 张照片移动到父相册 {}", totalMoved, parentRelativePath);
                    }

                    // 删除相册
                    albumRepository.delete(albumToDelete);
                }

                // 将所有图片归属到父相册中，并递归处理子目录
                if (parentAlbum != null) {
                    log.debug("目录 {} 超过最大相册层级 {}，将其图片归属到父相册 {}", toRelativePath(albumPath.toString()), maxDepth, parentAlbum.getName());
                    processAlbumImagesRecursively(albumPath, parentAlbum, force);
                }
                return;
            }
            
            String albumPathStr = albumPath.toString();
            String albumPathHash = calculateSha256(albumPathStr);
            Album album = albumRepository.findByPathHash(albumPathHash)
                .orElseGet(() -> {
                    // 再次检查是否关闭
                    if (isShuttingDown.get()) {
                        throw new IllegalStateException("应用正在关闭");
                    }
                    Album newAlbum = new Album();
                    newAlbum.setName(albumPath.getFileName().toString());
                    newAlbum.setPath(albumPathStr);
                    newAlbum.setPathHash(albumPathHash);
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
                    processPhotoFile(imageFile, album, force);
                    processedCount++;
                } catch (Exception e) {
                    log.warn("处理文件失败，跳过: {} - {}", imageFile.getName(), e.getMessage());
                    skippedCount++;
                }
            }

            if (skippedCount > 0) {
                log.warn("相册 {} 处理完成: 成功 {}, 跳过 {}", albumRelativePath, processedCount, skippedCount);
            }

            // 注意：子目录的处理由scanDirectoryInternal的Files.walk统一管理，这里不再递归处理

            // 更新相册照片数量
            album.setPhotoCount(photoRepository.countByAlbumId(album.getId()).intValue());
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
                        imageFiles.add(file);
                    }
                }
            }
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

        String filePath = imageFile.getAbsolutePath();
        String pathHash = calculateSha256(filePath);

        // 添加文件处理锁，防止同一文件被并发处理
        synchronized (filePath.intern()) {
            // 检查文件是否已经处理过，避免重复计数
            if (processedFiles.contains(filePath)) {
                log.debug("文件已处理过，跳过: {}", imageFile.getName());
                return;
            }

            try {
                // 跳过.thumbnails目录下的文件
                if (filePath.contains("/.thumbnails/") || filePath.contains("\\.thumbnails\\")) {
                    return;
                }

                // 跳过缩略图文件（文件名包含_thumb）
                if (imageFile.getName().contains("_thumb")) {
                return;
            }

            // 文件计数已在相册层面完成，这里不需要重复计数

            // 计算内容哈希（SHA-256）
            String contentHash = calculateSha256(imageFile);

            // 优先按内容哈希查找（支持复制图片复用数据），再按路径哈希，最后按原路径兜底
            Optional<Photo> photoByHash = contentHash == null ? Optional.empty() : photoRepository.findByContentHash(contentHash);
            Optional<Photo> photoByPathHash = pathHash == null ? Optional.empty() : photoRepository.findByPathHash(pathHash);
            Optional<Photo> photoByPath = photoRepository.findByOriginalPath(filePath);

            // 优先使用contentHash找到的照片（支持复制图片复用）
            Photo photo = photoByHash
                .orElseGet(() -> photoByPathHash
                .orElseGet(() -> photoByPath.orElseGet(Photo::new)));

            // 记录是否通过contentHash找到的（已存在且内容相同）
            boolean foundByContentHash = photoByHash.isPresent() && photo.getId() != null;

            // 所有到达这里的照片都算已遍历（无论是否需要处理）
            int previousCount = scanCurrent.get();
            int currentCount = scanCurrent.incrementAndGet();

            // 将文件标记为已处理，避免重复计数
            processedFiles.add(filePath);

            // 详细日志：记录每次增加的原因和当前进度
            String reason = "遍历文件";
            if (!force && photo.getId() != null && photo.canSkipProcessing(force)) {
                reason = "跳过已处理文件";
            } else {
                reason = "处理新文件或强制处理";
            }

            // 检查是否超过总数
            if (currentCount > scanTotal.get()) {
                log.warn("计数超出: current({}) > total({}), 文件: {}", currentCount, scanTotal.get(), imageFile.getName());
            }

            // 仅在实际处理时记录日志，输出相对路径
            String relativePath = toRelativePath(filePath);
            if (foundByContentHash && !force) {
                log.info("{}/{} 跳过: {}", currentCount, scanTotal.get(), relativePath != null ? relativePath : filePath);
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
                log.warn("处理图片失败（应用状态异常）: {}", imageFile.getAbsolutePath(), e);
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
            log.error("处理图片失败: {}", imageFile.getAbsolutePath(), e);
            }
        }
        } // end synchronized block
    }

    /**
     * 逐步处理照片的各个步骤，支持断点续上
     */
    private void processPhotoStepByStep(File imageFile, Photo photo, Album album, String contentHash,
                                       String pathHash, boolean force, boolean needsReprocessing,
                                       boolean foundByContentHash) {
        try {
            // 步骤1: 设置基础信息
            if (photo.getProcessingStatus() == ProcessingStatus.PENDING || needsReprocessing) {
                processBasicInfo(photo, album, contentHash, pathHash, imageFile);
                photo.advanceProcessingStatus();
                photoRepository.save(photo);
            }

            // 步骤2: 确保相册标签已初始化
            Album albumWithTags = albumRepository.findByIdWithTags(album.getId()).orElse(album);
            if (albumWithTags.getTags() == null) {
                albumWithTags.setTags(new ArrayList<>());
            }
            album = albumWithTags;

            // 步骤3: 提取EXIF信息
            if (photo.getProcessingStatus() == ProcessingStatus.BASIC_INFO_DONE || needsReprocessing) {
                try {
                    extractExifData(imageFile, photo);
                    photo.advanceProcessingStatus();
                    photoRepository.save(photo);
                } catch (Exception e) {
                    photo.markProcessingFailed("EXIF提取失败: " + e.getMessage());
                    photoRepository.save(photo);
                    throw e;
                }
            }

            // 步骤4: 生成缩略图
            if (photo.getProcessingStatus() == ProcessingStatus.BASIC_INFO_DONE ||
                photo.getProcessingStatus() == ProcessingStatus.THUMBNAILS_DONE || needsReprocessing) {
                try {
                    generateThumbnailAndWebP(imageFile, photo);
                    regenerateMissingThumbnails(imageFile, photo);
                    photo.setProcessingStatus(ProcessingStatus.THUMBNAILS_DONE);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    photo.markProcessingFailed("缩略图生成失败: " + e.getMessage());
                    photoRepository.save(photo);
                    throw e;
                }
            }

            // 步骤5: 分析色彩和质量评分
            if (photo.getProcessingStatus() == ProcessingStatus.THUMBNAILS_DONE ||
                photo.getProcessingStatus() == ProcessingStatus.ANALYSIS_DONE || needsReprocessing) {
                try {
                    colorAnalysisService.analyzeColor(imageFile, photo);
                    calculateQualityScore(photo);
                    photo.setProcessingStatus(ProcessingStatus.ANALYSIS_DONE);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    photo.markProcessingFailed("色彩分析失败: " + e.getMessage());
                    photoRepository.save(photo);
                    throw e;
                }
            }

            // 步骤6: 人脸检测
            List<Face> faces = new ArrayList<>();
            if (photo.getProcessingStatus() == ProcessingStatus.ANALYSIS_DONE ||
                photo.getProcessingStatus() == ProcessingStatus.FACES_DONE || needsReprocessing) {
                try {
                    faces = processFaces(imageFile, photo, force, foundByContentHash);
                    photo.setProcessingStatus(ProcessingStatus.FACES_DONE);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    photo.markProcessingFailed("人脸检测失败: " + e.getMessage());
                    photoRepository.save(photo);
                    throw e;
                }
            } else if (photo.getProcessingStatus().ordinal() >= ProcessingStatus.FACES_DONE.ordinal()) {
                // 如果已经完成人脸检测，获取现有的人脸数据
                faces = faceService.getFacesByPhoto(photo.getId());
            }

            // 步骤7: 主体检测
            if (photo.getProcessingStatus() == ProcessingStatus.FACES_DONE ||
                photo.getProcessingStatus() == ProcessingStatus.SUBJECT_DONE || needsReprocessing) {
                try {
                    subjectDetectionService.detectSubject(imageFile, photo, faces);
                    photo.setProcessingStatus(ProcessingStatus.SUBJECT_DONE);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    photo.markProcessingFailed("主体检测失败: " + e.getMessage());
                    photoRepository.save(photo);
                    throw e;
                }
            }

            // 步骤8: 智能标签
            if (photo.getProcessingStatus() == ProcessingStatus.SUBJECT_DONE ||
                photo.getProcessingStatus() == ProcessingStatus.TAGS_DONE || needsReprocessing) {
                try {
                    processTags(photo, album, imageFile, faces.size(), force);
                    photo.setProcessingStatus(ProcessingStatus.TAGS_DONE);
                    photoRepository.save(photo);
                } catch (Exception e) {
                    photo.markProcessingFailed("标签处理失败: " + e.getMessage());
                    photoRepository.save(photo);
                    throw e;
                }
            }

            // 步骤9: 完成处理
            if (photo.getProcessingStatus() == ProcessingStatus.TAGS_DONE) {
                photo.setProcessingStatus(ProcessingStatus.COMPLETED);
                photoRepository.save(photo);
            }

        } catch (Exception e) {
            // 如果是处理失败，记录错误但不抛出异常，确保其他图片能继续处理
            if (photo.getProcessingStatus() != ProcessingStatus.FAILED) {
                photo.markProcessingFailed("未知处理错误: " + e.getMessage());
                photoRepository.save(photo);
            }
            log.error("处理图片失败: {}", imageFile.getAbsolutePath(), e);
        }
    }

    /**
     * 处理照片基础信息
     */
    private void processBasicInfo(Photo photo, Album album, String contentHash, String pathHash, File imageFile) {
        // 设置哈希值（只有新创建的照片才设置，避免重复）
        if (photo.getId() == null) {
            // 新照片，设置所有哈希值
            photo.setContentHash(contentHash);
            photo.setPathHash(pathHash);
        } else if (photo.getContentHash() == null || photo.getContentHash().isEmpty()) {
            // 哈希值为空时，更新哈希值
            photo.setContentHash(contentHash);
            photo.setPathHash(pathHash);
        }
        photo.setAlbumId(album.getId());
        photo.setFilename(imageFile.getName());
        photo.setOriginalPath(imageFile.getAbsolutePath());
        photo.setFileSize(imageFile.length());
        photo.setProcessingStatus(ProcessingStatus.BASIC_INFO_DONE);
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
            return faceService.detectAndSaveFaces(imageFile, photo, false);
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

            // 提取所有EXIF标签
            for (Directory directory : metadata.getDirectories()) {
                for (com.drew.metadata.Tag tag : directory.getTags()) {
                    exifMap.put(tag.getTagName(), tag.getDescription());
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
        Iterable<Photo> all = photoRepository.findAll();
        int total = (int) photoRepository.count();
        int count = 0;
        int processed = 0;
        String tid = currentTaskId.get();
        if (tid != null) {
            setTaskProgress(tid, 0, total);
            appendTaskLog(tid, "开始遍历 " + total + " 张照片");
        }
        for (Photo photo : all) {
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

            if (changed) {
                photoRepository.save(photo);
                count++;
            }
            // 进度上报（每10张汇报一次）
            processed++;
            if (tid != null && (processed % 10 == 0 || processed == total)) {
                setTaskProgress(tid, processed, total);
                appendTaskLog(tid, "已处理 " + processed + " / " + total + " 张");
            }
        }
        if (tid != null) {
            setTaskProgress(tid, processed, total);
            appendTaskLog(tid, "处理结束, 共有 " + count + " 张照片被更新");
        }
        log.info("EXIF 字段更新完成, 更新 {} 张照片", count);
    }

    /**
     * 重新计算所有照片的颜色相关属性
     */
    public void recalculateAllPhotoColors() {
        log.info("开始重新计算所有照片的颜色相关属性...");
        Iterable<Photo> all = photoRepository.findAll();
        int total = (int) photoRepository.count();
        int count = 0;
        int processed = 0;
        String tid = currentTaskId.get();
        if (tid != null) {
            setTaskProgress(tid, 0, total);
            appendTaskLog(tid, "开始遍历 " + total + " 张照片");
        }

        for (Photo photo : all) {
            boolean changed = false;

            // 重新分析照片颜色
            try {
                File imageFile = new File(photo.getOriginalPath());
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
                    log.warn("照片文件不存在: {}", photo.getOriginalPath());
                }
            } catch (Exception e) {
                log.warn("重新分析照片 {} 的颜色失败: {}", photo.getId(), e.getMessage());
            }

            if (changed) {
                photoRepository.save(photo);
                count++;
            }

            // 进度上报（每10张汇报一次）
            processed++;
            if (tid != null && (processed % 10 == 0 || processed == total)) {
                setTaskProgress(tid, processed, total);
                appendTaskLog(tid, "已处理 " + processed + " / " + total + " 张");
            }
        }

        if (tid != null) {
            setTaskProgress(tid, processed, total);
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
                appendTaskLog(tid, "相册氛围更新失败: " + e.getMessage());
            }
        }

        log.info("颜色重新计算完成, 更新 {} 张照片", count);
    }

    /**
     * 更新所有相册的氛围
     */
    private void updateAllAlbumAtmospheres() {
        log.info("开始更新所有相册的氛围...");
        Iterable<Album> allAlbums = albumRepository.findAll();
        int albumCount = 0;

        for (Album album : allAlbums) {
            try {
                atmosphereAnalysisService.analyzeAlbumAtmosphere(album.getId());
                albumCount++;
            } catch (Exception e) {
                log.warn("更新相册 {} 的氛围失败: {}", album.getId(), e.getMessage());
            }
        }

        log.info("相册氛围更新完成, 更新了 {} 个相册", albumCount);
    }

    /**
     * 批量更新所有照片的颜色分类
     */
    public void updateAllColorCategories() {
        log.info("开始批量更新所有照片的颜色分类...");
        Iterable<Photo> all = photoRepository.findAll();
        int total = (int) photoRepository.count();
        int count = 0;
        int processed = 0;
        String tid = currentTaskId.get();
        if (tid != null) {
            setTaskProgress(tid, 0, total);
            appendTaskLog(tid, "开始遍历 " + total + " 张照片");
        }

        for (Photo photo : all) {
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
                count++;
            }

            // 进度上报（每10张汇报一次）
            processed++;
            if (tid != null && (processed % 10 == 0 || processed == total)) {
                setTaskProgress(tid, processed, total);
                appendTaskLog(tid, "已处理 " + processed + " / " + total + " 张");
            }
        }

        if (tid != null) {
            setTaskProgress(tid, processed, total);
            appendTaskLog(tid, "处理结束, 共有 " + count + " 张照片被更新");
        }
        log.info("颜色分类更新完成, 更新 {} 张照片", count);
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
        photo.setSmallThumbPath(smallThumbFile.getAbsolutePath());
        long smallSizeKB = smallThumbFile.length() / 1024;

        // 2. 生成中缩略图（用于瀑布流显示）
        File mediumThumbFile = new File(baseDir, baseName + "_medium.jpg");
        generateThumbnailWithQuality(originalImage, mediumThumbFile, mediumThumbnailWidth, mediumThumbnailHeight, mediumThumbnailQuality);
        photo.setMediumThumbPath(mediumThumbFile.getAbsolutePath());
        long mediumSizeKB = mediumThumbFile.length() / 1024;

        // 3. 生成大缩略图（用于PhotoViewer大图显示）
        // 只有当原图足够大且能够显著压缩时才生成大缩略图
        File largeThumbFile = new File(baseDir, baseName + "_large.jpg");
        boolean shouldGenerateLargeThumb = shouldGenerateLargeThumbnail(originalImage, largeThumbnailWidth, largeThumbnailHeight, originalFileSize);

        String largeInfo;
        if (shouldGenerateLargeThumb) {
            generateThumbnailWithQuality(originalImage, largeThumbFile, largeThumbnailWidth, largeThumbnailHeight, largeThumbnailQuality);
            photo.setLargeThumbPath(largeThumbFile.getAbsolutePath());
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
        photo.setThumbnailPath(smallThumbFile.getAbsolutePath());
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
                        photo.setSmallThumbPath(smallThumbFile.getAbsolutePath());
                    }
                } else {
                    photo.setSmallThumbPath(smallThumbFile.getAbsolutePath());
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
                        photo.setMediumThumbPath(mediumThumbFile.getAbsolutePath());
                    }
                } else {
                    photo.setMediumThumbPath(mediumThumbFile.getAbsolutePath());
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
                        photo.setLargeThumbPath(largeThumbFile.getAbsolutePath());
                    } else {
                        // 如果不应该生成，设置为空（表示使用原图）
                        photo.setLargeThumbPath(null);
                    }
                } else {
                    photo.setLargeThumbPath(largeThumbFile.getAbsolutePath());
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
                            photo.setThumbnailPath(thumbFile.getAbsolutePath());
                        }
                    } else {
                        photo.setThumbnailPath(thumbFile.getAbsolutePath());
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
            return null;
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
            String base = basePath;
            if (!Paths.get(base).isAbsolute()) {
                String projectRoot = System.getProperty("user.dir");
                if (projectRoot.endsWith("backend")) {
                    projectRoot = new File(projectRoot).getParent();
                }
                String cleanPath = base.startsWith("./") ? base.substring(2) : base;
                base = new File(projectRoot, cleanPath).getAbsolutePath();
            }
            base = Paths.get(base).normalize().toString();
            String normalized = Paths.get(absolutePath).normalize().toString();
            if (!normalized.startsWith(base)) {
                return absolutePath;
            }
            String rel = normalized.substring(base.length());
            if (!rel.startsWith(File.separator)) {
                rel = File.separator + rel;
            }
            return rel.replace("\\", "/");
        } catch (Exception e) {
            return absolutePath;
        }
    }

    /**
     * 计算相册目录的层级深度
     * 深度计算规则：base-path/分类/顶级相册名/1级层级/2级层级/...
     * 从"1级层级"开始计算深度，返回相对于base-path的深度（从第三级开始计数）
     */
    private int calculateAlbumDepth(Path albumPath) {
        try {
            // 获取base-path的绝对路径
            Path basePathResolved = Paths.get(basePath);
            if (!basePathResolved.isAbsolute()) {
                String projectRoot = System.getProperty("user.dir");
                if (projectRoot.endsWith("backend")) {
                    projectRoot = new File(projectRoot).getParent();
                }
                String cleanPath = basePath.startsWith("./") ? basePath.substring(2) : basePath;
                basePathResolved = Paths.get(new File(projectRoot, cleanPath).getAbsolutePath());
            }
            basePathResolved = basePathResolved.normalize();

            // 如果当前路径不在base-path下，返回0
            if (!albumPath.startsWith(basePathResolved)) {
                return 0;
            }

            // 计算相对路径的层级数
            Path relative = basePathResolved.relativize(albumPath);
            int nameCount = relative.getNameCount();

            // 深度 = 层级数 - 2（减去base-path和第一级分类目录）
            // 例如：base-path/分类/顶级相册名/1级层级 -> 深度为1
            return Math.max(0, nameCount - 2);
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
                processPhotoFile(imageFile, album, force);
            }

            // 递归处理子目录
            try (Stream<Path> subPaths = Files.list(directory)) {
                subPaths.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(".thumbnails"))
                    .forEach(p -> processAlbumImagesRecursively(p, album, force));
            }
        } catch (Exception e) {
            log.warn("递归处理目录图片失败: {}", directory, e);
        }
    }

    /**
     * 解析基础路径为绝对路径
     */
    private Path resolveBasePath() {
        Path path = Paths.get(basePath);

        // 处理相对路径：如果是相对路径，转换为绝对路径
        if (!path.isAbsolute()) {
            // 获取项目根目录
            // 方法1: 从当前工作目录推断
            String projectRoot = System.getProperty("user.dir");

            // 如果当前在backend目录，需要回到项目根目录
            if (projectRoot.endsWith("backend")) {
                projectRoot = new File(projectRoot).getParent();
            }

            // 方法2: 尝试从类路径推断项目根目录（更可靠）
            // 如果方法1失败，可以尝试这个方法
            if (projectRoot == null || projectRoot.isEmpty()) {
                try {
                    String classPath = PhotoScanService.class.getProtectionDomain()
                        .getCodeSource().getLocation().getPath();
                    // 从target/classes或jar文件推断项目根目录
                    if (classPath.contains("target/classes")) {
                        projectRoot = new File(classPath).getParentFile().getParentFile().getParent();
                    }
                } catch (Exception e) {
                    log.warn("无法从类路径推断项目根目录", e);
                }
            }

            // 处理以 ./ 开头的相对路径
            String cleanPath = basePath.startsWith("./")
                ? basePath.substring(2)
                : basePath;

            path = Paths.get(projectRoot, cleanPath).toAbsolutePath().normalize();
        }

        return path;
    }

    /**
     * 清空所有缩略图文件
     */
    @Transactional
    public Map<String, Object> clearAllThumbnails() {
        Map<String, Object> result = new HashMap<>();
        try {
            Path basePathObj = resolveBasePath();
            if (!Files.exists(basePathObj) || !Files.isDirectory(basePathObj)) {
                result.put("error", "基础路径不存在或不是目录: " + basePathObj);
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
            result.put("error", "清理缩略图失败: " + e.getMessage());
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
            result.put("error", "清理人脸数据失败: " + e.getMessage());
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
            result.put("error", "清理智能标签失败: " + e.getMessage());
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
            List<Photo> allPhotos = photoRepository.findAll();
            List<Long> photosToDelete = new ArrayList<>();
            List<Long> albumsToCheck = new ArrayList<>();

            int processedCount = 0;
            int orphanedCount = 0;

            for (Photo photo : allPhotos) {
                processedCount++;

                // 检查文件是否还存在
                if (photo.getOriginalPath() == null || photo.getOriginalPath().isEmpty()) {
                    log.warn("照片记录原始路径为空，标记为孤儿记录: photoId={}", photo.getId());
                    photosToDelete.add(photo.getId());
                    orphanedCount++;
                    continue;
                }

                File imageFile = new File(photo.getOriginalPath());
                if (!imageFile.exists()) {
                    log.info("图片文件不存在，标记为孤儿记录: {} (photoId={})",
                        photo.getOriginalPath(), photo.getId());
                    photosToDelete.add(photo.getId());
                    albumsToCheck.add(photo.getAlbumId());
                    orphanedCount++;
                }
            }

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
            Set<Long> albumsToCheckSet = new HashSet<>(albumsToCheck);
            int emptyAlbumsDeleted = 0;
            int nonExistentPathAlbumsDeleted = 0;

            // 首先检查所有相册（不仅仅是照片被删除的相册）
            List<Album> allAlbums = albumRepository.findAll();
            for (Album album : allAlbums) {
                try {
                    // 检查相册路径是否存在
                    if (album.getPath() != null && !album.getPath().isEmpty()) {
                        File albumDir = new File(album.getPath());
                        if (!albumDir.exists()) {
                            log.info("相册文件夹不存在，删除相册记录: {} (albumId={})", album.getPath(), album.getId());
                            albumRepository.deleteById(album.getId());
                            nonExistentPathAlbumsDeleted++;
                            continue; // 跳过后续检查
                        }
                    }

                    // 检查相册是否有照片（只对路径存在的相册进行此检查）
                    long photoCount = photoRepository.countByAlbumId(album.getId());
                    if (photoCount == 0) {
                        log.info("相册 {} 已没有照片，删除相册记录", album.getId());
                        albumRepository.deleteById(album.getId());
                        emptyAlbumsDeleted++;
                    }
                } catch (Exception e) {
                    log.warn("检查相册失败: albumId={}, error={}", album.getId(), e.getMessage());
                }
            }

            result.put("message", String.format("清理完成！处理了 %d 个照片记录，发现 %d 个孤儿记录，删除了 %d 个空相册和 %d 个路径不存在的相册",
                processedCount, orphanedCount, emptyAlbumsDeleted, nonExistentPathAlbumsDeleted));
            result.put("processedPhotos", processedCount);
            result.put("orphanedPhotos", orphanedCount);
            result.put("emptyAlbumsDeleted", emptyAlbumsDeleted);
            result.put("nonExistentPathAlbumsDeleted", nonExistentPathAlbumsDeleted);
            result.put("success", true);

            log.info("清理完成！处理了 {} 个照片记录，发现 {} 个孤儿记录，删除了 {} 个空相册和 {} 个路径不存在的相册",
                processedCount, orphanedCount, emptyAlbumsDeleted, nonExistentPathAlbumsDeleted);

        } catch (Exception e) {
            result.put("error", "清理残留数据失败: " + e.getMessage());
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

        List<Photo> allPhotos = photoRepository.findAll();
        AtomicInteger updatedCount = new AtomicInteger(0);
        AtomicInteger processedCount = new AtomicInteger(0);

        for (Photo photo : allPhotos) {
            try {
                // 获取照片文件路径
                String originalPath = photo.getOriginalPath();
                if (originalPath == null || originalPath.isEmpty()) {
                    log.warn("照片 {} 没有原始路径，跳过", photo.getId());
                    processedCount.incrementAndGet();
                    continue;
                }

                File imageFile = new File(originalPath);
                if (!imageFile.exists()) {
                    log.warn("照片文件不存在: {}", originalPath);
                    processedCount.incrementAndGet();
                    continue;
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
        }

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
        resp.put("status", ts.status);
        resp.put("current", ts.current);
        resp.put("total", ts.total);
        resp.put("complete", ts.complete);
        resp.put("startTime", ts.startTime != null ? ts.startTime.toString() : null);
        resp.put("endTime", ts.endTime != null ? ts.endTime.toString() : null);
        resp.put("logs", new ArrayList<>(ts.logs));
        return resp;
    }

    /**
     * 同步版本：批量更新所有照片的时间信息
     */
    @Transactional
    public Map<String, Object> updateAllPhotoTimes() {
        log.info("开始批量更新所有照片的时间信息...");

        List<Photo> allPhotos = photoRepository.findAll();
        int updatedCount = 0;
        int processedCount = 0;

        for (Photo photo : allPhotos) {
            try {
                // 获取照片文件路径
                String originalPath = photo.getOriginalPath();
                if (originalPath == null || originalPath.isEmpty()) {
                    log.warn("照片 {} 没有原始路径，跳过", photo.getId());
                    processedCount++;
                    continue;
                }

                File imageFile = new File(originalPath);
                if (!imageFile.exists()) {
                    log.warn("照片文件不存在: {}", originalPath);
                    processedCount++;
                    continue;
                }

                // 提取EXIF信息并更新时间
                extractExifData(imageFile, photo);
                photoRepository.save(photo);

                updatedCount++;
                processedCount++;

                // 每处理100张照片记录一次日志
                if (processedCount % 100 == 0) {
                    log.info("已处理 {} 张照片，更新了 {} 张", processedCount, updatedCount);
                }

            } catch (Exception e) {
                log.error("更新照片 {} 时间失败: {}", photo.getId(), e.getMessage());
                processedCount++;
            }
        }

        log.info("批量更新照片时间完成，总共处理 {} 张照片，成功更新 {} 张", processedCount, updatedCount);

        Map<String, Object> result = new HashMap<>();
        result.put("totalProcessed", processedCount);
        result.put("totalUpdated", updatedCount);
        result.put("message", String.format("成功处理 %d 张照片，更新了 %d 张照片的时间信息", processedCount, updatedCount));

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
     * 获取相对于base-path的文件路径
     */
    private String getRelativeFilePath(String fullPath) {
        if (fullPath == null) {
            return null;
        }

        // 尝试多种方式匹配base-path
        String[] possibleBasePaths = {
            basePath,  // 原始配置路径
            Paths.get(basePath).toAbsolutePath().toString(),  // 绝对路径
            Paths.get(System.getProperty("user.dir"), basePath).toString(),  // 相对于工作目录
            Paths.get("./data/photos").toAbsolutePath().toString(),  // 硬编码绝对路径
        };

        for (String basePath : possibleBasePaths) {
            if (fullPath.startsWith(basePath)) {
                String relativePath = fullPath.substring(basePath.length());
                // 移除开头的路径分隔符
                if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                    relativePath = relativePath.substring(1);
                }
                return relativePath;
            }
        }

        // 如果都没有匹配，尝试从"data/photos"开始截取
        String dataPhotosPath = "data" + File.separator + "photos";
        int dataIndex = fullPath.indexOf(dataPhotosPath);
        if (dataIndex >= 0) {
            String relativePath = fullPath.substring(dataIndex + dataPhotosPath.length());
            if (relativePath.startsWith("/") || relativePath.startsWith("\\")) {
                relativePath = relativePath.substring(1);
            }
            return relativePath;
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
}

