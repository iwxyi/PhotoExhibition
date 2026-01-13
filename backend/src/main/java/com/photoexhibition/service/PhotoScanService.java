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
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.TagRepository;
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
    private final AtomicInteger activeScanCount = new AtomicInteger(0);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private final AtomicInteger scanCurrent = new AtomicInteger(0);
    private final AtomicInteger scanTotal = new AtomicInteger(0);
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
        this.objectMapper = objectMapper;
    }
    
    @PreDestroy
    public void onShutdown() {
        isShuttingDown.set(true);
        log.info("扫描服务正在关闭，停止所有异步任务");
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
     * 应用启动后执行一次扫描
     */
    @PostConstruct
    public void init() {
        log.info("扫描服务初始化: {}", basePath);

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

        // 确保current不会超过total（处理递归处理导致的计数问题）
        int current = scanCurrent.get();
        int total = scanTotal.get();
        if (current > total && total > 0) {
            current = total;
        }

        status.put("current", current);
        status.put("total", total);
        status.put("lastScanStart", lastScanStart);
        status.put("lastScanEnd", lastScanEnd);
        return status;
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
            isScanning.set(true);
            scanCurrent.set(0);
            scanTotal.set(0);
            lastScanStart = LocalDateTime.now();

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
                scanTotal.set(total);
                log.info("预统计待扫描图片数量: {}", total);
            } catch (Exception e) {
                log.warn("统计待扫描图片数量失败: {}", e.getMessage());
                scanTotal.set(0);
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
            log.info("相册 {}: {} 张图片", albumRelativePath, imageFiles.size());

            for (File imageFile : imageFiles) {
                processPhotoFile(imageFile, album, force);
            }

            // 如果当前目录的深度没有超过最大层级，则递归处理子目录
            if (depth <= maxDepth) {
                try (Stream<Path> subPaths = Files.list(albumPath)) {
                    subPaths.filter(Files::isDirectory)
                        .filter(p -> !p.getFileName().toString().equals(".thumbnails"))
                        .forEach(p -> processAlbumDirectory(p, force, album));
                }
            }

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
     * 处理单张图片
     */
    @Transactional
    public void processPhotoFile(File imageFile, Album album, boolean force) {
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            return;
        }

        String filePath = imageFile.getAbsolutePath();
        String pathHash = calculateSha256(filePath);

        // 添加文件处理锁，防止同一文件被并发处理
        synchronized (filePath.intern()) {
            try {
                // 跳过.thumbnails目录下的文件
                if (filePath.contains("/.thumbnails/") || filePath.contains("\\.thumbnails\\")) {
                    return;
                }

                // 跳过缩略图文件（文件名包含_thumb）
                if (imageFile.getName().contains("_thumb")) {
                return;
            }
            
            // 进度累加（进入处理流程即视为已处理一个文件）
            scanCurrent.incrementAndGet();
            
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
            
            if (!force && photo.getUpdatedAt() != null && imageFile.lastModified() <= photo.getUpdatedAt()
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) {
                // 文件未更新且非强制，跳过
                return;
            }

            // 仅在实际处理时记录日志，输出相对路径
            String relativePath = toRelativePath(filePath);
            if (foundByContentHash && !force) {
                log.info("处理图片（复用数据）: {}", relativePath != null ? relativePath : filePath);
            } else {
                log.info("处理图片: {}", relativePath != null ? relativePath : filePath);
            }

            // 设置哈希值（只有新创建的照片才设置，避免重复）
            if (photo.getId() == null) {
                // 新照片，设置所有哈希值
            photo.setContentHash(contentHash);
            photo.setPathHash(pathHash);
            } else if (force || photo.getContentHash() == null || photo.getContentHash().isEmpty()) {
                // 强制扫描或哈希值为空时，更新哈希值
                photo.setContentHash(contentHash);
                photo.setPathHash(pathHash);
            }
            photo.setAlbumId(album.getId());
            photo.setFilename(imageFile.getName());
            // 更新路径（即使照片被移动，Face仍然通过photo_id关联，不受影响）
            photo.setOriginalPath(filePath);
            photo.setFileSize(imageFile.length());

            // 确保相册标签已初始化，避免懒加载异常（带标签查询）
            Album albumWithTags = albumRepository.findByIdWithTags(album.getId()).orElse(album);
            if (albumWithTags.getTags() == null) {
                albumWithTags.setTags(new ArrayList<>());
            }
            album = albumWithTags;

            // 提取EXIF信息
            extractExifData(imageFile, photo);

            // 生成缩略图和WebP
            generateThumbnailAndWebP(imageFile, photo);

            // 检查并重新生成缺失的缩略图
            regenerateMissingThumbnails(imageFile, photo);

            // 分析色彩
            colorAnalysisService.analyzeColor(imageFile, photo);

            // 计算质量评分
            calculateQualityScore(photo);

            // 先保存一次，确保有ID用于人脸关联
            photoRepository.save(photo);

            // 人脸检测与保存（在主体检测之前，以便主体检测可以使用人脸信息）
            // 如果通过contentHash找到照片且已有人脸数据，且非强制扫描，可以跳过人脸检测（复用已有数据）
            List<Face> faces;
            if (foundByContentHash && !force && photo.getId() != null) {
                // 检查是否已有人脸数据
                long existingFaceCount = faceService.getFaceCountByPhoto(photo.getId());
                if (existingFaceCount > 0) {
                    log.debug("照片已有人脸数据，跳过重新检测（photoId={}, faceCount={}）", photo.getId(), existingFaceCount);
                    faces = faceService.getFacesByPhoto(photo.getId());
                } else {
                    // 虽然通过contentHash找到，但没有人脸数据，需要检测
                    try {
                        faces = faceService.detectAndSaveFaces(imageFile, photo, false);
                    } catch (UnsatisfiedLinkError e) {
                        log.warn("人脸检测服务不可用（缺少系统依赖库），跳过人脸检测: {}。请安装 Microsoft Visual C++ Redistributable 或相关依赖。", imageFile.getName());
                        faces = new ArrayList<>();
                    } catch (Exception e) {
                        log.warn("人脸检测失败，使用简单方法: {}", imageFile.getName(), e);
                        faces = new ArrayList<>();
                    }
                }
            } else {
                // 新照片或强制扫描，重新检测人脸
                try {
                    faces = faceService.detectAndSaveFaces(imageFile, photo, false);
                } catch (UnsatisfiedLinkError e) {
                    log.warn("人脸检测服务不可用（缺少系统依赖库），跳过人脸检测: {}。请安装 Microsoft Visual C++ Redistributable 或相关依赖。", imageFile.getName());
                    faces = new ArrayList<>();
                } catch (Exception e) {
                    log.warn("人脸检测失败，使用简单方法: {}", imageFile.getName(), e);
                    faces = new ArrayList<>();
                }
            }

            // 检测主体位置（使用已检测的人脸信息）
            try {
                subjectDetectionService.detectSubject(imageFile, photo, faces);
            } catch (Exception e) {
                log.warn("检测主体位置失败: {}", imageFile.getName(), e);
            }

            // 合并相册标签到照片标签，便于搜索（复制一份避免懒加载问题）
            List<Tag> albumTags = album.getTags() == null ? new ArrayList<>() : new ArrayList<>(album.getTags());
            Set<String> albumTagNames = albumTags.stream()
                .map(Tag::getName)
                .collect(java.util.stream.Collectors.toSet());
            
            if (!albumTags.isEmpty()) {
                if (photo.getTags() == null) {
                    photo.setTags(new java.util.HashSet<>());
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
                smartTagService.applySmartTags(imageFile, photo, faces.size(), force, albumTagNames);

                photoRepository.save(photo);
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
                }
                if (subIfdDirectory.containsTag(ExifSubIFDDirectory.TAG_FNUMBER)) {
                    String aperture = subIfdDirectory.getString(ExifSubIFDDirectory.TAG_FNUMBER);
                    photo.setAperture(aperture);
                    exifMap.put("aperture", aperture);
                }
                if (subIfdDirectory.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)) {
                    String shutterSpeed = subIfdDirectory.getString(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
                    photo.setShutterSpeed(shutterSpeed);
                    exifMap.put("shutterSpeed", shutterSpeed);
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

