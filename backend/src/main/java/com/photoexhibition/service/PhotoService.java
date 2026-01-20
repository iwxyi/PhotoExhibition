package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.dto.FilterRequest;
import com.photoexhibition.dto.PhotoDTO;
import com.photoexhibition.dto.FaceDTO;
import com.photoexhibition.dto.TagDTO;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PhotoAIScoringRepository;
import com.photoexhibition.repository.PhotoAssignmentRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.PersonProfileRepository;
import com.photoexhibition.service.FilterOptionService;
import com.photoexhibition.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final FaceRepository faceRepository;
    private final AlbumRepository albumRepository;
    private final PhotoAIScoringRepository aiScoringRepository;
    private final FilterOptionService filterOptionService;
    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;
    private final PhotoAssignmentRepository photoAssignmentRepository;
    private final PersonProfileRepository personProfileRepository;

    @Value("${photo.scan.base-path}")
    private String photoBasePath;

    /**
     * 获取所有图片（图墙模式）
     * 注意：Page对象不缓存，因为反序列化会有问题
     */
    public Page<PhotoDTO> getAllPhotos(Pageable pageable) {
        // 如果pageable已经有排序，使用传入的排序；否则使用系统配置的排序
        Pageable sortedPageable = pageable;
        if (pageable.getSort().isEmpty()) {
            Sort sort = getPhotoSort();
            sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        }

        Page<Photo> photos = photoRepository.findAll(sortedPageable);
        return photos.map(this::convertToDTO);
    }

    /**
     * 获取随机高质量图片
     */
    public Page<PhotoDTO> getRandomHighQualityPhotos(double minQualityScore, Pageable pageable) {
        List<Photo> photos = photoRepository.findRandomHighQualityPhotos(minQualityScore, pageable);
        Long total = photoRepository.countByQualityScoreGreaterThanEqual(minQualityScore);
        return new org.springframework.data.domain.PageImpl<>(
            photos.stream().map(this::convertToDTO).collect(Collectors.toList()),
            pageable,
            total
        );
    }

    /**
     * 获取相册中的图片
     * 注意：Page对象不缓存，因为反序列化会有问题
     */
    public Page<PhotoDTO> getPhotosByAlbum(Long albumId, Pageable pageable) {
        // 应用相册或系统配置的排序方式
        Sort sort = getPhotoSort(albumId);
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        // 检查相册是否开启了聚合下级相册
        List<Long> albumIds = getAggregatedAlbumIds(albumId);

        if (albumIds.size() == 1) {
            // 没有聚合，直接查询单个相册
            Page<Photo> photos = photoRepository.findByAlbumId(albumId, sortedPageable);
            return photos.map(this::convertToDTO);
        } else {
            // 聚合模式，查询多个相册的照片
            return getPhotosByAlbumIds(albumIds, sortedPageable);
        }
    }

    /**
     * 获取相册的所有照片（不分页）
     */
    public Page<PhotoDTO> getAllPhotosByAlbum(Long albumId) {
        // 检查相册是否开启了聚合下级相册
        boolean isAggregated = albumRepository.findById(albumId)
            .map(album -> {
                Boolean agg = album.getAggregateSubAlbums();
                return agg != null && agg;
            })
            .orElse(false);

        List<Photo> allPhotos;
        if (!isAggregated) {
            // 没有聚合，直接查询单个相册的所有照片
            allPhotos = new java.util.ArrayList<>(photoRepository.findByAlbumId(albumId, PageRequest.of(0, 1000)).getContent());
        } else {
            // 聚合模式：查找所有相关的相册ID，然后查询所有照片
            List<Long> albumIds = getAggregatedAlbumIds(albumId);


            allPhotos = new java.util.ArrayList<>();
            for (Long id : albumIds) {
                List<Photo> albumPhotos = photoRepository.findByAlbumId(id, PageRequest.of(0, 1000)).getContent();
                allPhotos.addAll(albumPhotos);
            }

        }

        // 排序
        Sort sort = getPhotoSort(albumId);
        allPhotos.sort((p1, p2) -> {
            for (org.springframework.data.domain.Sort.Order order : sort) {
                int cmp = 0;
                String property = order.getProperty();
                switch (property) {
                    case "takenAt":
                        cmp = compareNullable(p1.getTakenAt(), p2.getTakenAt());
                        break;
                    case "filename":
                        cmp = compareNullable(p1.getFilename(), p2.getFilename());
                        break;
                    case "createdAt":
                        cmp = compareNullable(p1.getCreatedAt(), p2.getCreatedAt());
                        break;
                    default:
                        cmp = 0;
                }
                if (cmp != 0) {
                    return order.isAscending() ? cmp : -cmp;
                }
            }
            return 0;
        });

        List<PhotoDTO> dtos = allPhotos.stream().map(this::convertToDTO).collect(java.util.stream.Collectors.toList());
        return new org.springframework.data.domain.PageImpl<>(dtos, PageRequest.of(0, allPhotos.size()), allPhotos.size());
    }

    /**
     * 获取筛选选项
     */
    public Map<String, Object> getFilterOptions() {
        return filterOptionService.getFilterOptions();
    }

    /**
     * 高级筛选
     */
    public Page<PhotoDTO> filterPhotos(FilterRequest request, Pageable pageable) {
        Page<Photo> photos;

        // 预处理空字符串为 null，避免空值触发 EXIF 查询导致 SQL 拼接异常
        String cameraModel = (request.getCameraModel() != null && !request.getCameraModel().isBlank())
                ? request.getCameraModel()
                : null;
        String lensModel = (request.getLensModel() != null && !request.getLensModel().isBlank())
                ? request.getLensModel()
                : null;
        Double minAperture = request.getMinAperture();
        Double maxAperture = request.getMaxAperture();
        Double minFocalLength = request.getMinFocalLength();
        Double maxFocalLength = request.getMaxFocalLength();
        Double minShutterSpeed = request.getMinShutterSpeed();
        Double maxShutterSpeed = request.getMaxShutterSpeed();
        Integer minIso = request.getMinIso();
        Integer maxIso = request.getMaxIso();
        String colorCategory = request.getColorCategory();
        String category = request.getCategory();
        Double minQualityScore = request.getMinQualityScore();
        List<Long> excludePhotoIds = request.getExcludePhotoIds();

        // 按人物筛选（优先级最高）
        if (request.getPersonId() != null) {
            photos = photoRepository.findByPersonId(request.getPersonId(), pageable);
        }
        // 标签筛选
        else if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            photos = photoRepository.findByTagIds(request.getTagIds(), pageable);
        }
        // 颜色筛选（独立筛选条件）
        else if (colorCategory != null && !colorCategory.trim().isEmpty()) {
            photos = photoRepository.findByColorCategory(colorCategory, minQualityScore, excludePhotoIds, pageable);
        }
        // 分类筛选（独立筛选条件）
        else if (category != null && !category.trim().isEmpty()) {
            // 获取匹配分类的所有相册ID
            List<Album> albums = albumRepository.findAll();
            List<Long> matchingAlbumIds = albums.stream()
                .filter(album -> {
                    try {
                        String albumPath = album.getPath();
                        if (albumPath == null || albumPath.isEmpty()) return false;

                        Path basePathResolved = Paths.get(photoBasePath);
                        if (!basePathResolved.isAbsolute()) {
                            String projectRoot = System.getProperty("user.dir");
                            if (projectRoot.endsWith("backend")) {
                                projectRoot = new File(projectRoot).getParent();
                            }
                            String cleanPath = photoBasePath.startsWith("./")
                                ? photoBasePath.substring(2)
                                : photoBasePath;
                            basePathResolved = Paths.get(new File(projectRoot, cleanPath).getAbsolutePath());
                        }
                        basePathResolved = basePathResolved.normalize();

                        Path albumRealPath = Paths.get(albumPath).normalize();
                        if (!albumRealPath.startsWith(basePathResolved)) {
                            return false;
                        }
                        Path relative = basePathResolved.relativize(albumRealPath);
                        if (relative.getNameCount() > 0) {
                            return relative.getName(0).toString().equals(category);
                        }
                        return false;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .map(Album::getId)
                .collect(java.util.stream.Collectors.toList());

            if (!matchingAlbumIds.isEmpty()) {
                photos = photoRepository.findByAlbumIds(matchingAlbumIds, pageable);
            } else {
                photos = Page.empty(pageable);
            }
        }
        // EXIF筛选
        else if (cameraModel != null || lensModel != null ||
                 minAperture != null || maxAperture != null ||
                 minFocalLength != null || maxFocalLength != null ||
                 minShutterSpeed != null || maxShutterSpeed != null ||
                 minIso != null || maxIso != null) {
            photos = photoRepository.findByExifFilters(
                cameraModel,
                lensModel,
                minAperture,
                maxAperture,
                minFocalLength,
                maxFocalLength,
                minShutterSpeed,
                maxShutterSpeed,
                minIso,
                maxIso,
                colorCategory,
                minQualityScore,
                excludePhotoIds,
                pageable
            );
        }
        // 默认获取所有
        else {
            // 检查是否为随机排序
            boolean isRandomOrder = pageable.getSort().stream()
                .anyMatch(order -> "RAND()".equals(order.getProperty()));

            if (isRandomOrder) {
                // 使用自定义的随机查询方法
                photos = photoRepository.findAllRandom(pageable);
            } else {
                photos = photoRepository.findAll(pageable);
            }
        }

        // 转换为DTO（所有筛选条件已在数据库查询中处理）
        List<PhotoDTO> dtos = photos.getContent().stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, photos.getTotalElements());
    }

    /**
     * 获取图片详情
     * 注意：暂时不缓存，避免反序列化问题
     */
    public PhotoDTO getPhotoById(Long id) {
        Photo photo = photoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("图片不存在"));
        return convertToDTO(photo);
    }

    public void deletePhoto(Long id) {
        if (!photoRepository.existsById(id)) {
            throw new RuntimeException("图片不存在");
        }

        // 删除图片时，同时删除相关的人脸记录
        List<Face> faces = faceRepository.findByPhotoId(id);
        if (!faces.isEmpty()) {
            log.info("删除图片 {} 时，同时删除 {} 个人脸记录", id, faces.size());
            faceRepository.deleteAll(faces);
        }

        // 删除相关的图片指派记录
        photoAssignmentRepository.deleteByPhotoId(id);

        photoRepository.deleteById(id);
    }

    /**
     * 将图片指派给人物（非人脸绑定）
     */
    @Transactional
    public PhotoDTO assignPhotoToPerson(Long photoId, Long personId) {
        Photo photo = photoRepository.findById(photoId).orElseThrow(() -> new RuntimeException("图片不存在"));
        com.photoexhibition.entity.PersonProfile person = personProfileRepository.findById(personId)
            .orElseThrow(() -> new RuntimeException("人物不存在"));

        java.util.Optional<com.photoexhibition.entity.PhotoAssignment> existing = photoAssignmentRepository.findByPhotoId(photoId);
        log.info("assignPhotoToPerson - existing assignment: {}", existing.isPresent());

        com.photoexhibition.entity.PhotoAssignment savedPa;
        if (existing.isPresent()) {
            com.photoexhibition.entity.PhotoAssignment pa = existing.get();
            pa.setPersonId(personId);
            savedPa = photoAssignmentRepository.save(pa);
            log.info("Updated PhotoAssignment for photo {} to person {}, saved ID: {}", photoId, personId, savedPa.getId());
        } else {
            com.photoexhibition.entity.PhotoAssignment pa = new com.photoexhibition.entity.PhotoAssignment();
            pa.setPhotoId(photoId);
            pa.setPersonId(personId);
            savedPa = photoAssignmentRepository.save(pa);
            log.info("Created PhotoAssignment for photo {} to person {}, saved ID: {}", photoId, personId, savedPa.getId());
        }

        // 验证保存是否成功
        long countAfterSave = photoAssignmentRepository.count();
        log.info("PhotoAssignment count after save: {}", countAfterSave);

        PhotoDTO result = convertToDTO(photo);
        log.info("assignPhotoToPerson result - photoId: {}, assignedPersonId: {}", photoId, result.getAssignedPersonId());
        return result;
    }

    @Transactional
    public void unassignPhoto(Long photoId) {
        // 仅删除图片级别的指派记录，不对图片中的人脸做任何解绑处理。
        photoAssignmentRepository.deleteByPhotoId(photoId);
    }

    public Page<PhotoDTO> listPhotosAssignedToPerson(Long personId, Pageable pageable) {
        Page<com.photoexhibition.entity.PhotoAssignment> page = photoAssignmentRepository.findByPersonId(personId, pageable);
        List<PhotoDTO> dtos = page.getContent().stream()
            .map(pa -> {
                Photo photo = photoRepository.findById(pa.getPhotoId()).orElse(null);
                return photo != null ? convertToDTO(photo) : null;
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    /**
     * 增加查看次数
     */
    @Transactional
    public void incrementViewCount(Long id) {
        Photo photo = photoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("图片不存在"));
        photo.setViewCount(photo.getViewCount() + 1);
        photoRepository.save(photo);
    }

    /**
     * 点赞（增加 likeCount）
     */
    @Transactional
    public int incrementLike(Long id) {
        Photo photo = photoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("图片不存在"));
        Integer lc = photo.getLikeCount();
        if (lc == null) lc = 0;
        lc = lc + 1;
        photo.setLikeCount(lc);
        photoRepository.save(photo);
        return lc;
    }

    /**
     * 取消点赞（减少 likeCount，最低为 0）
     */
    @Transactional
    public int decrementLike(Long id) {
        Photo photo = photoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("图片不存在"));
        Integer lc = photo.getLikeCount();
        if (lc == null) lc = 0;
        lc = Math.max(0, lc - 1);
        photo.setLikeCount(lc);
        photoRepository.save(photo);
        return lc;
    }

    /**
     * 获取点赞数
     */
    public int getLikeCount(Long id) {
        return photoRepository.findById(id).map(p -> p.getLikeCount() == null ? 0 : p.getLikeCount()).orElse(0);
    }

    /**
     * 转换为DTO
     */
    /**
     * 将快门秒数转换为分数形式显示
     * 示例：0.06666666666666667 -> "1/15", 0.5 -> "1/2", 2.0 -> "2"
     */
    private String formatShutterSpeedFromSeconds(Double seconds) {
        if (seconds == null || seconds == 0) return "0";
        if (seconds >= 1) return String.valueOf(Math.round(seconds));  // 超出1秒显示整数
        if (seconds >= 0.1) return String.format("%.1f", seconds);  // 0.1秒到1秒之间显示小数
        // 小于一秒显示倒数，分母取整
        int denominator = (int) Math.round(1.0 / seconds);
        return "1/" + denominator;
    }

    /**
     * 测试快门速度格式化方法（开发调试用）
     */
    public void testShutterSpeedFormatting() {
        double[] testValues = {0.06666666666666667, 0.5, 2.0, 0.25, 0.125, 0.03333333333333333, 1.5, 0.1, 0.05};
        log.info("快门速度格式化测试:");
        for (double value : testValues) {
            String formatted = formatShutterSpeedFromSeconds(value);
            log.info("  {} -> {}", value, formatted);
        }
    }

    private PhotoDTO convertToDTO(Photo photo) {
        PhotoDTO dto = new PhotoDTO();
        dto.setId(photo.getId());
        dto.setAlbumId(photo.getAlbumId());
        dto.setFilename(photo.getFilename());
        // 将绝对路径转换为相对路径（相对于base-path）
        dto.setOriginalPath(convertToRelativePath(photo.getOriginalPath()));
        dto.setThumbnailPath(convertToRelativePath(photo.getThumbnailPath()));
        dto.setWebpPath(convertToRelativePath(photo.getWebpPath()));
        dto.setSmallThumbPath(convertToRelativePath(photo.getSmallThumbPath()));
        dto.setMediumThumbPath(convertToRelativePath(photo.getMediumThumbPath()));
        dto.setLargeThumbPath(convertToRelativePath(photo.getLargeThumbPath()));
        dto.setFileSize(photo.getFileSize());
        dto.setContentHash(photo.getContentHash());
        dto.setWidth(photo.getWidth());
        dto.setHeight(photo.getHeight());
        dto.setFormat(photo.getFormat());
        dto.setDominantColor(photo.getDominantColor());
        dto.setColorCategory(photo.getColorCategory());

        // 解析颜色调色板
        if (photo.getColorPalette() != null) {
            try {
                List<String> palette = objectMapper.readValue(photo.getColorPalette(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                dto.setColorPalette(palette);
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        // 解析EXIF数据
        if (photo.getExifData() != null) {
            try {
                java.util.Map<String, Object> exifMap = objectMapper.readValue(photo.getExifData(),
                    objectMapper.getTypeFactory().constructMapType(java.util.Map.class, String.class, Object.class));
                dto.setExifData(exifMap);
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        dto.setCameraMake(photo.getCameraMake());
        dto.setCameraModel(photo.getCameraModel());
        dto.setLensModel(photo.getLensModel());
        dto.setFocalLength(photo.getFocalLength());
        dto.setFocalLengthMm(photo.getFocalLengthMm());
        dto.setAperture(photo.getAperture());
        dto.setApertureValue(photo.getApertureValue());

        // 优先使用原始快门字符串，如果没有则从秒数转换为分数形式
        String shutterSpeedDisplay = photo.getShutterSpeed();
        if (shutterSpeedDisplay == null && photo.getShutterSpeedSeconds() != null) {
            shutterSpeedDisplay = formatShutterSpeedFromSeconds(photo.getShutterSpeedSeconds());
        }
        dto.setShutterSpeed(shutterSpeedDisplay);
        dto.setShutterSpeedSeconds(photo.getShutterSpeedSeconds());

        dto.setIso(photo.getIso());
        dto.setTakenAt(photo.getTakenAt());
        dto.setQualityScore(photo.getQualityScore());
        dto.setFocusX(photo.getFocusX());
        dto.setFocusY(photo.getFocusY());
        dto.setViewCount(photo.getViewCount());
        dto.setLikeCount(photo.getLikeCount() == null ? 0 : photo.getLikeCount());
        dto.setIsFeatured(photo.getIsFeatured());
        if (photo.getTags() != null) {
            dto.setTags(photo.getTags().stream().map(this::toTagDTO).collect(Collectors.toList()));
        }
        // faces 需要额外查询，避免懒加载问题
        List<Face> faces = photo.getId() != null
            ? faceRepository.findByPhotoId(photo.getId())
            : java.util.Collections.emptyList();
        dto.setFaces(faces.stream().map(this::toFaceDTO).collect(Collectors.toList()));
        dto.setCreatedAt(photo.getCreatedAt());

        // 检查是否有图片级别的指派（非人脸）
        if (photo.getId() != null) {
            java.util.Optional<com.photoexhibition.entity.PhotoAssignment> pa = photoAssignmentRepository.findByPhotoId(photo.getId());
            if (pa.isPresent()) {
                dto.setAssignedPersonId(pa.get().getPersonId());
                // 尝试获取人物名称（如果需要的话）
                try {
                    java.util.Optional<com.photoexhibition.entity.PersonProfile> personOpt = personProfileRepository.findById(pa.get().getPersonId());
                    if (personOpt.isPresent()) {
                        dto.setAssignedPersonName(personOpt.get().getName());
                    }
                } catch (Exception e) {
                    log.warn("Failed to load person name for photo assignment: {}", e.getMessage());
                }
            }
        }

        // 设置AI评分信息
        if (photo.getId() != null) {
            try {
                java.util.Optional<com.photoexhibition.entity.PhotoAIScoring> aiScoringOpt = aiScoringRepository.findByPhotoId(photo.getId());
                if (aiScoringOpt.isPresent()) {
                    com.photoexhibition.entity.PhotoAIScoring aiScoring = aiScoringOpt.get();
                    dto.setAiOverallScore(aiScoring.getOverallScore());
                    dto.setAiTechnicalScore(aiScoring.getTechnicalScore());
                    dto.setAiCompositionScore(aiScoring.getCompositionScore());
                    dto.setAiAppealScore(aiScoring.getAppealScore());

                    // 解析优点和不足
                    if (aiScoring.getStrengths() != null) {
                        try {
                            List<String> strengths = objectMapper.readValue(aiScoring.getStrengths(),
                                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                            dto.setAiStrengths(strengths);
                        } catch (Exception e) {
                            log.warn("Failed to parse AI strengths: {}", e.getMessage());
                        }
                    }

                    if (aiScoring.getWeaknesses() != null) {
                        try {
                            List<String> weaknesses = objectMapper.readValue(aiScoring.getWeaknesses(),
                                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                            dto.setAiWeaknesses(weaknesses);
                        } catch (Exception e) {
                            log.warn("Failed to parse AI weaknesses: {}", e.getMessage());
                        }
                    }

                    if (aiScoring.getImprovementSuggestions() != null) {
                        try {
                            List<String> suggestions = objectMapper.readValue(aiScoring.getImprovementSuggestions(),
                                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                            dto.setAiSuggestions(suggestions);
                        } catch (Exception e) {
                            log.warn("Failed to parse AI suggestions: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load AI scoring for photo {}: {}", photo.getId(), e.getMessage());
            }
        }

        return dto;
    }

    /**
     * 将绝对路径转换为相对路径（相对于base-path）
     */
    private String convertToRelativePath(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return absolutePath;
        }
        
        try {
            // 获取base-path的绝对路径
            String basePath = photoBasePath;
            if (!Paths.get(basePath).isAbsolute()) {
                String projectRoot = System.getProperty("user.dir");
                if (projectRoot.endsWith("backend")) {
                    projectRoot = new File(projectRoot).getParent();
                }
                String cleanPath = basePath.startsWith("./")
                    ? basePath.substring(2)
                    : basePath;
                basePath = new File(projectRoot, cleanPath).getAbsolutePath();
            }
            
            // 标准化路径
            basePath = Paths.get(basePath).normalize().toString();
            String normalizedAbsolutePath = Paths.get(absolutePath).normalize().toString();
            
            // 检查路径是否在base-path下
            if (!normalizedAbsolutePath.startsWith(basePath)) {
                // 如果路径不在base-path下，返回原路径
                return absolutePath;
            }
            
            // 转换为相对路径
            String relativePath = normalizedAbsolutePath.substring(basePath.length());
            // 确保以/开头
            if (!relativePath.startsWith("/")) {
                relativePath = "/" + relativePath;
            }
            // 统一使用/作为路径分隔符（前端使用）
            relativePath = relativePath.replace("\\", "/");
            return relativePath;
        } catch (Exception e) {
            // 转换失败，返回原路径
            return absolutePath;
        }
    }

    private TagDTO toTagDTO(Tag tag) {
        TagDTO dto = new TagDTO();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setColor(tag.getColor());
        return dto;
    }

    private FaceDTO toFaceDTO(Face face) {
        FaceDTO dto = new FaceDTO();
        dto.setId(face.getId());
        dto.setPhotoId(face.getPhoto() != null ? face.getPhoto().getId() : null);
        dto.setPhotoFilename(face.getPhoto() != null ? face.getPhoto().getFilename() : null);
        dto.setPhotoThumbnailPath(face.getPhoto() != null ? convertToRelativePath(face.getPhoto().getThumbnailPath()) : null);
        dto.setPhotoOriginalPath(face.getPhoto() != null ? convertToRelativePath(face.getPhoto().getOriginalPath()) : null);
        dto.setX(face.getX());
        dto.setY(face.getY());
        dto.setWidth(face.getWidth());
        dto.setHeight(face.getHeight());
        dto.setConfidence(face.getConfidence());
        dto.setIsConfirmed(face.getIsConfirmed());
        if (face.getPerson() != null) {
            dto.setPersonId(face.getPerson().getId());
            dto.setPersonName(face.getPerson().getName());
            dto.setPersonDescription(face.getPerson().getDescription());
        }
        return dto;
    }

    /**
     * 根据相册或系统配置获取照片排序方式
     * 优先使用相册级别的排序设置，没有则使用系统配置
     */
    private Sort getPhotoSort() {
        return getPhotoSort(null);
    }

    /**
     * 根据相册或系统配置获取照片排序方式
     * 优先使用相册级别的排序设置，没有则使用系统配置
     */
    public Sort getPhotoSort(Long albumId) {
        String sortOrder = null;

        // 优先使用相册级别的排序设置
        if (albumId != null) {
            java.util.Optional<com.photoexhibition.entity.Album> albumOpt = albumRepository.findById(albumId);
            if (albumOpt.isPresent()) {
                sortOrder = albumOpt.get().getPhotoSortOrder();
            }
        }

        // 如果没有相册级别的设置，使用系统配置
        if (sortOrder == null || sortOrder.trim().isEmpty()) {
            sortOrder = systemConfigService.getPhotoSortOrder();
        }

        return getSortByOrderString(sortOrder);
    }

    /**
     * 根据排序字符串获取Sort对象
     */
    private Sort getSortByOrderString(String sortOrder) {
        switch (sortOrder) {
            case SystemConfigService.SORT_BY_TAKEN_AT_DESC:
                return Sort.by(Sort.Direction.DESC, "takenAt");
            case SystemConfigService.SORT_BY_TAKEN_AT_ASC:
                return Sort.by(Sort.Direction.ASC, "takenAt");
            case SystemConfigService.SORT_BY_FILENAME_DESC:
                return Sort.by(Sort.Direction.DESC, "filename");
            case SystemConfigService.SORT_BY_FILENAME_ASC:
                return Sort.by(Sort.Direction.ASC, "filename");
            case SystemConfigService.SORT_BY_CREATED_AT_DESC:
                return Sort.by(Sort.Direction.DESC, "createdAt");
            case SystemConfigService.SORT_BY_CREATED_AT_ASC:
                return Sort.by(Sort.Direction.ASC, "createdAt");
            default:
                // 默认按拍摄时间倒序
                return Sort.by(Sort.Direction.DESC, "takenAt");
        }
    }

    /**
     * 获取需要聚合的相册ID列表
     * 如果相册开启了聚合下级相册，返回该相册及其所有子相册的ID
     * 否则只返回当前相册ID
     */
    private List<Long> getAggregatedAlbumIds(Long albumId) {
        List<Long> albumIds = new java.util.ArrayList<>();
        albumIds.add(albumId);

        // 检查相册是否开启了聚合下级相册
        albumRepository.findById(albumId).ifPresent(album -> {
            if (Boolean.TRUE.equals(album.getAggregateSubAlbums())) {
                // 递归获取所有子相册ID
                addSubAlbumIds(album.getPath(), albumIds);
            }
        });

        return albumIds;
    }

    /**
     * 递归添加子相册ID到列表中
     */
    private void addSubAlbumIds(String parentPath, List<Long> albumIds) {
        List<com.photoexhibition.entity.Album> subAlbums = albumRepository.findDirectSubAlbums(parentPath);

        for (com.photoexhibition.entity.Album subAlbum : subAlbums) {
            albumIds.add(subAlbum.getId());
            // 如果子相册也开启了聚合，继续递归
            if (Boolean.TRUE.equals(subAlbum.getAggregateSubAlbums())) {
                addSubAlbumIds(subAlbum.getPath(), albumIds);
            }
        }
    }

    /**
     * 获取多个相册中的所有照片（用于聚合模式）
     */
    private Page<PhotoDTO> getPhotosByAlbumIds(List<Long> albumIds, Pageable pageable) {
        // 使用自定义查询获取多个相册的照片
        List<Photo> allPhotos = new java.util.ArrayList<>();
        int totalElements = 0;

        // 分别查询每个相册的照片，然后合并
        for (Long albumId : albumIds) {
            List<Photo> albumPhotos = photoRepository.findByAlbumId(albumId, PageRequest.of(0, 1000)).getContent();
            allPhotos.addAll(albumPhotos);
        }

        // 手动排序所有照片，使用第一个相册的排序设置
        Sort sort = getPhotoSort(albumIds.get(0));
        allPhotos.sort((p1, p2) -> {
            for (org.springframework.data.domain.Sort.Order order : sort) {
                int cmp = 0;
                String property = order.getProperty();
                switch (property) {
                    case "takenAt":
                        cmp = compareNullable(p1.getTakenAt(), p2.getTakenAt());
                        break;
                    case "filename":
                        cmp = compareNullable(p1.getFilename(), p2.getFilename());
                        break;
                    case "createdAt":
                        cmp = compareNullable(p1.getCreatedAt(), p2.getCreatedAt());
                        break;
                }
                if (cmp != 0) {
                    return order.getDirection() == org.springframework.data.domain.Sort.Direction.DESC ? -cmp : cmp;
                }
            }
            return 0;
        });

        totalElements = allPhotos.size();

        // 手动分页
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allPhotos.size());
        List<Photo> pageContent = start < allPhotos.size() ? allPhotos.subList(start, end) : new java.util.ArrayList<>();

        return new org.springframework.data.domain.PageImpl<>(
            pageContent.stream().map(this::convertToDTO).collect(Collectors.toList()),
            pageable,
            totalElements
        );
    }

    /**
     * 比较可空的Comparable对象
     */
    private <T extends Comparable<T>> int compareNullable(T a, T b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }
}

