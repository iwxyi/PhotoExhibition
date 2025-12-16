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
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final TagRepository tagRepository;
    private final ColorAnalysisService colorAnalysisService;
    private final SubjectDetectionService subjectDetectionService;
    private final FaceService faceService;
    private final SmartTagService smartTagService;
    private final AtomicInteger activeScanCount = new AtomicInteger(0);
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private final AtomicInteger scanCurrent = new AtomicInteger(0);
    private final AtomicInteger scanTotal = new AtomicInteger(0);
    private volatile LocalDateTime lastScanStart = null;
    private volatile LocalDateTime lastScanEnd = null;

    public PhotoScanService(AlbumRepository albumRepository,
                           PhotoRepository photoRepository,
                           TagRepository tagRepository,
                           ColorAnalysisService colorAnalysisService,
                           SubjectDetectionService subjectDetectionService,
                           FaceService faceService,
                           SmartTagService smartTagService) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.tagRepository = tagRepository;
        this.colorAnalysisService = colorAnalysisService;
        this.subjectDetectionService = subjectDetectionService;
        this.faceService = faceService;
        this.smartTagService = smartTagService;
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
     */
    @Scheduled(fixedDelayString = "${photo.scan.scan-interval}000")
    @Async
    public void scheduledScan() {
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
     * 异步触发强制扫描（跳过更新校验）
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
        // 可选：启动时立即扫描一次
        // scanDirectory(basePath);
    }

    /**
     * 手动触发扫描
     */
    @Transactional
    public void scanDirectory(String directoryPath) {
        scanDirectoryInternal(directoryPath, false);
    }

    /**
     * 强制重新扫描（总是重建缩略图、人脸、标签）
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
        status.put("current", scanCurrent.get());
        status.put("total", scanTotal.get());
        status.put("lastScanStart", lastScanStart);
        status.put("lastScanEnd", lastScanEnd);
        return status;
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

            Path path = Paths.get(directoryPath);
            
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
            
            if (!Files.exists(path)) {
                throw new IllegalArgumentException("目录不存在: " + path);
            }
            
            if (!Files.isDirectory(path)) {
                throw new IllegalArgumentException("路径不是文件夹: " + path);
            }

            // 预统计总数用于进度显示
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
        // 检查应用是否正在关闭
        if (isShuttingDown.get()) {
            log.debug("应用正在关闭，跳过处理: {}", albumPath);
            return;
        }
        
        try {
            // 跳过.thumbnails目录
            if (albumPath.getFileName().toString().equals(".thumbnails")) {
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
            log.info("相册 {}: {} 张图片", album.getName(), imageFiles.size());

            for (File imageFile : imageFiles) {
                processPhotoFile(imageFile, album, force);
            }

            // 更新相册照片数量
            album.setPhotoCount(photoRepository.countByAlbumId(album.getId()).intValue());
            albumRepository.save(album);

        } catch (IllegalStateException e) {
            // 应用关闭时的异常，静默处理
            if (e.getMessage() != null && e.getMessage().contains("关闭")) {
                log.debug("应用关闭，停止处理相册: {}", albumPath);
            } else {
                log.warn("处理相册目录失败（应用状态异常）: {}", albumPath, e);
            }
        } catch (org.springframework.context.ApplicationContextException e) {
            // Spring上下文异常，应用可能正在关闭
            log.debug("应用上下文异常，停止处理相册: {}", albumPath);
        } catch (Exception e) {
            // 检查是否是应用关闭相关的异常
            String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMsg.contains("closed") || errorMsg.contains("shutdown") || 
                errorMsg.contains("context") && errorMsg.contains("close")) {
                log.debug("应用关闭，停止处理相册: {}", albumPath);
            } else {
            log.error("处理相册目录失败: {}", albumPath, e);
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
        
        try {
            String filePath = imageFile.getAbsolutePath();
            String pathHash = calculateSha256(filePath);
            
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

            // 始终更新哈希值（确保数据一致性）
            photo.setContentHash(contentHash);
            photo.setPathHash(pathHash);
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
                    faces = faceService.detectAndSaveFaces(imageFile, photo, false);
                }
            } else {
                // 新照片或强制扫描，重新检测人脸
                faces = faceService.detectAndSaveFaces(imageFile, photo, false);
            }

            // 检测主体位置（使用已检测的人脸信息）
            try {
                subjectDetectionService.detectSubject(imageFile, photo, faces);
            } catch (Exception e) {
                log.warn("检测主体位置失败: {}", imageFile.getName(), e);
            }

            // 保存更新后的焦点位置
            photoRepository.save(photo);

            // 合并相册标签到照片标签，便于搜索（复制一份避免懒加载问题）
            List<Tag> albumTags = album.getTags() == null ? new ArrayList<>() : new ArrayList<>(album.getTags());
            Set<String> albumTagNames = albumTags.stream()
                .map(Tag::getName)
                .collect(java.util.stream.Collectors.toSet());
            
            if (!albumTags.isEmpty()) {
                if (photo.getTags() == null) {
                    photo.setTags(new java.util.HashSet<>());
                }
                photo.getTags().addAll(albumTags);
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
    }

    /**
     * 生成缩略图和WebP
     */
    private void generateThumbnailAndWebP(File imageFile, Photo photo) throws IOException {
        String baseDir = new File(imageFile.getParent(), ".thumbnails").getAbsolutePath();
        Files.createDirectories(Paths.get(baseDir));

        String baseName = FilenameUtils.getBaseName(imageFile.getName());
        
        // 生成缩略图
        File thumbnailFile = new File(baseDir, baseName + "_thumb.jpg");
        BufferedImage originalImage = ImageIO.read(imageFile);
        if (originalImage != null) {
            photo.setWidth(originalImage.getWidth());
            photo.setHeight(originalImage.getHeight());
            photo.setFormat(FilenameUtils.getExtension(imageFile.getName()));

            Thumbnails.of(originalImage)
                .size(thumbnailWidth, thumbnailHeight)
                .outputFormat("jpg")
                .outputQuality(0.85f)
                .toFile(thumbnailFile);
            
            photo.setThumbnailPath(thumbnailFile.getAbsolutePath());

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
}

