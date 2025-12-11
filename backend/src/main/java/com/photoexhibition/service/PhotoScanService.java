package com.photoexhibition.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.photoexhibition.entity.Album;
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
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public PhotoScanService(AlbumRepository albumRepository,
                           PhotoRepository photoRepository,
                           TagRepository tagRepository,
                           ColorAnalysisService colorAnalysisService,
                           SubjectDetectionService subjectDetectionService) {
        this.albumRepository = albumRepository;
        this.photoRepository = photoRepository;
        this.tagRepository = tagRepository;
        this.colorAnalysisService = colorAnalysisService;
        this.subjectDetectionService = subjectDetectionService;
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
        if (directoryPath == null || directoryPath.isEmpty()) {
            directoryPath = basePath;
        }
        try {
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

            // 扫描所有子文件夹，跳过.thumbnails目录
            try (Stream<Path> paths = Files.walk(path)) {
                paths.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals(".thumbnails"))  // 跳过.thumbnails目录
                    .forEach(this::processAlbumDirectory);
            }
        } catch (Exception e) {
            log.error("扫描目录失败: {}", directoryPath, e);
            throw new RuntimeException("扫描目录失败: " + (e.getMessage() == null ? directoryPath : e.getMessage()), e);
        }
    }

    /**
     * 处理相册目录
     */
    private void processAlbumDirectory(Path albumPath) {
        try {
            // 跳过.thumbnails目录
            if (albumPath.getFileName().toString().equals(".thumbnails")) {
                return;
            }
            
            String albumPathStr = albumPath.toString();
            Album album = albumRepository.findByPath(albumPathStr)
                .orElseGet(() -> {
                    Album newAlbum = new Album();
                    newAlbum.setName(albumPath.getFileName().toString());
                    newAlbum.setPath(albumPathStr);
                    // 从路径提取标签（包含层级继承）
                    extractTagsFromPath(newAlbum, albumPath);
                    return albumRepository.save(newAlbum);
                });

            // 扫描目录中的图片文件
            List<File> imageFiles = findImageFiles(albumPath.toFile());
            log.info("相册 {}: {} 张图片", album.getName(), imageFiles.size());

            for (File imageFile : imageFiles) {
                processPhotoFile(imageFile, album);
            }

            // 更新相册照片数量
            album.setPhotoCount(photoRepository.countByAlbumId(album.getId()).intValue());
            albumRepository.save(album);

        } catch (Exception e) {
            log.error("处理相册目录失败: {}", albumPath, e);
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
    public void processPhotoFile(File imageFile, Album album) {
        try {
            String filePath = imageFile.getAbsolutePath();
            
            // 跳过.thumbnails目录下的文件
            if (filePath.contains("/.thumbnails/") || filePath.contains("\\.thumbnails\\")) {
            return;
        }
        
        // 跳过缩略图文件（文件名包含_thumb）
        if (imageFile.getName().contains("_thumb")) {
                return;
            }
            
            // 检查是否已存在
            Optional<Photo> existingPhoto = photoRepository.findByOriginalPath(filePath);
            if (existingPhoto.isPresent()) {
                // 检查文件是否更新
                if (imageFile.lastModified() <= existingPhoto.get().getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) {
                    return; // 文件未更新，跳过
                }
            }

            Photo photo = existingPhoto.orElseGet(Photo::new);
            photo.setAlbumId(album.getId());
            photo.setFilename(imageFile.getName());
            photo.setOriginalPath(filePath);
            photo.setFileSize(imageFile.length());

            // 提取EXIF信息
            extractExifData(imageFile, photo);

            // 生成缩略图和WebP
            generateThumbnailAndWebP(imageFile, photo);

            // 分析色彩
            colorAnalysisService.analyzeColor(imageFile, photo);

            // 检测主体位置
            try {
                subjectDetectionService.detectSubject(imageFile, photo);
            } catch (Exception e) {
                log.warn("检测主体位置失败: {}", imageFile.getName(), e);
            }

            // 计算质量评分
            calculateQualityScore(photo);

            photoRepository.save(photo);

        } catch (Exception e) {
            log.error("处理图片失败: {}", imageFile.getAbsolutePath(), e);
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
     * 从文件夹名提取标签
     */
    private void extractTagsFromPath(Album album, Path albumPath) {
        Set<String> tags = new java.util.HashSet<>();

        try {
            // 计算相对于basePath的子路径
            Path base = Paths.get(basePath).toAbsolutePath().normalize();
            Path relative;
            try {
                relative = base.relativize(albumPath.toAbsolutePath().normalize());
            } catch (Exception e) {
                relative = albumPath.getFileName();
            }

            int idx = 0;
            for (Path part : relative) {
                // 跳过base-path下的第一级目录（作为大分类，不参与标签）
                if (idx == 0) {
                    idx++;
                    continue;
                }
                String name = part.getFileName().toString().trim();
                if (name.isEmpty()) continue;

                // 去掉日期前缀，如 2025.11.01 xxx 或 2025-11-01 xxx
                name = name.replaceFirst("^\\d{4}[\\.-]\\d{2}[\\.-]\\d{2}\\s*", "");
                if (name.isEmpty()) continue;

                // 通过多种分隔符拆分，获取关键词（空格、下划线、横线、顿号、逗号等）
                String[] keywords = name.split("[\\s_\\-、，,·———/]+");
                for (String kw : keywords) {
                    String keyword = kw.trim();
                    if (keyword.length() > 0 && !keyword.matches("^\\.+$") && !keyword.equals("." ) && !keyword.equals("..")) {
                        tags.add(keyword);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("从路径提取标签失败: {}", albumPath, e);
        }

        // 落库并关联
        for (String keyword : tags) {
            Tag tag = tagRepository.findByName(keyword)
                .orElseGet(() -> {
                    Tag newTag = new Tag();
                    newTag.setName(keyword);
                    return tagRepository.save(newTag);
                });
            if (album.getTags() == null) {
                album.setTags(new ArrayList<>());
            }
            if (!album.getTags().contains(tag)) {
                album.getTags().add(tag);
            }
        }
    }
}

