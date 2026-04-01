package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.dto.FilterRequest;
import com.photoexhibition.dto.PhotoDTO;
import com.photoexhibition.dto.FaceDTO;
import com.photoexhibition.dto.TagDTO;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.PersonProfile;
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
import com.photoexhibition.service.AlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.time.LocalDateTime;
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
    private final UserPathService userPathService;
    
    @Lazy
    @Autowired
    private AlbumService albumService;

    /**
     * 获取所有图片（图墙模式）
     * 注意：Page对象不缓存，因为反序列化会有问题
     */
    public Page<PhotoDTO> getAllPhotos(Pageable pageable) {
        return getAllPhotos(pageable, null);
    }

    public Page<PhotoDTO> getAllPhotos(Pageable pageable, Long userId) {
        // 如果pageable已经有排序，使用传入的排序；否则使用系统配置的排序
        Pageable sortedPageable = pageable;
        if (pageable.getSort().isEmpty()) {
            Sort sort = getPhotoSort();
            sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        }

        if (userId != null) {
            return photoRepository.findByUserId(userId, sortedPageable).map(this::convertToDTO);
        }

        Page<Photo> photos = photoRepository.findAll(sortedPageable);
        return photos.map(this::convertToDTO);
    }

    /**
     * 获取随机高质量图片（排除隐藏的照片）
     */
    public Page<PhotoDTO> getRandomHighQualityPhotos(double minQualityScore, Pageable pageable) {
        return getRandomHighQualityPhotos(minQualityScore, pageable, null);
    }

    public Page<PhotoDTO> getRandomHighQualityPhotos(double minQualityScore, Pageable pageable, Long userId) {
        if (userId != null) {
            List<Photo> photos = photoRepository.findRandomHighQualityPhotosNotHiddenByUserId(userId, minQualityScore, pageable);
            Long total = photoRepository.countByQualityScoreGreaterThanEqualAndUserId(userId, minQualityScore);
            return new org.springframework.data.domain.PageImpl<>(
                photos.stream().map(this::convertToDTO).collect(Collectors.toList()),
                pageable,
                total == null ? 0L : total
            );
        }

        List<Photo> photos = photoRepository.findRandomHighQualityPhotosNotHidden(minQualityScore, pageable);
        Long total = photoRepository.countByQualityScoreGreaterThanEqual(minQualityScore);
        return new org.springframework.data.domain.PageImpl<>(
            photos.stream().map(this::convertToDTO).collect(Collectors.toList()),
            pageable,
            total
        );
    }

    /**
     * 获取相册中的图片（排除隐藏的照片）
     * 注意：Page对象不缓存，因为反序列化会有问题
     */
    public Page<PhotoDTO> getPhotosByAlbum(Long albumId, Pageable pageable) {
        return getPhotosByAlbum(albumId, pageable, null);
    }

    public Page<PhotoDTO> getPhotosByAlbum(Long albumId, Pageable pageable, Long userId) {
        validateAlbumOwnership(albumId, userId);
        // 应用相册或系统配置的排序方式
        Sort sort = getPhotoSort(albumId);
        Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        // 检查相册是否开启了聚合下级相册
        List<Long> albumIds = getAggregatedAlbumIds(albumId);

        if (albumIds.size() == 1) {
            // 没有聚合，直接查询单个相册，排除隐藏的照片
            Page<Photo> photos = photoRepository.findByAlbumIdAndIsHiddenFalse(albumId, sortedPageable);
            return photos.map(this::convertToDTO);
        } else {
            // 聚合模式，查询多个相册的照片（排除隐藏）
            return getPhotosByAlbumIds(albumIds, sortedPageable);
        }
    }

    /**
     * 获取相册的所有照片（不分页）
     */
    public Page<PhotoDTO> getAllPhotosByAlbum(Long albumId) {
        return getAllPhotosByAlbum(albumId, null);
    }

    public Page<PhotoDTO> getAllPhotosByAlbum(Long albumId, Long userId) {
        validateAlbumOwnership(albumId, userId);
        // 检查相册是否开启了聚合下级相册
        boolean isAggregated = albumRepository.findById(albumId)
            .map(album -> {
                Boolean agg = album.getAggregateSubAlbums();
                log.debug("getAllPhotosByAlbum - albumId: {}, aggregateSubAlbums: {}", albumId, agg);
                return agg != null && agg;
            })
            .orElse(false);

        log.debug("getAllPhotosByAlbum - albumId: {}, isAggregated: {}", albumId, isAggregated);

        List<Photo> allPhotos;
        if (!isAggregated) {
            // 没有聚合，直接查询单个相册的所有照片
            allPhotos = new java.util.ArrayList<>(photoRepository.findByAlbumIdAndIsHiddenFalse(albumId, PageRequest.of(0, 1000)).getContent());
            log.debug("getAllPhotosByAlbum - 非聚合模式，直接查询相册 {} 的照片，数量: {}", albumId, allPhotos.size());
        } else {
            // 聚合模式：查找所有相关的相册ID，然后查询所有照片
            List<Long> albumIds = getAggregatedAlbumIds(albumId);
            log.debug("getAllPhotosByAlbum - 聚合模式，相册ID列表: {}, 大小: {}", albumIds, albumIds.size());

            allPhotos = new java.util.ArrayList<>();
            for (Long id : albumIds) {
                List<Photo> albumPhotos = photoRepository.findByAlbumIdAndIsHiddenFalse(id, PageRequest.of(0, 1000)).getContent();
                log.debug("getAllPhotosByAlbum - 子相册 {} 的照片数量: {}", id, albumPhotos.size());
                allPhotos.addAll(albumPhotos);
            }
            log.debug("getAllPhotosByAlbum - 聚合相册总照片数: {}", allPhotos.size());
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
        // 确保 page size 至少为 1
        int pageSize = Math.max(1, allPhotos.size());
        return new org.springframework.data.domain.PageImpl<>(dtos, PageRequest.of(0, pageSize), allPhotos.size());
    }

    /**
     * 获取筛选选项
     */
    public Map<String, Object> getFilterOptions() {
        return filterOptionService.getFilterOptions();
    }

    public Map<String, Object> getFilterOptions(Long userId) {
        return filterOptionService.getFilterOptions(userId);
    }

    /**
     * 高级筛选
     */
    public Page<PhotoDTO> filterPhotos(FilterRequest request, Pageable pageable) {
        return filterPhotos(request, pageable, null);
    }

    public Page<PhotoDTO> filterPhotos(FilterRequest request, Pageable pageable, Long userId) {
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
        List<Long> scopedExcludePhotoIds = excludePhotoIds == null || excludePhotoIds.isEmpty() ? null : excludePhotoIds;

        // 处理日期范围
        java.time.LocalDateTime startDate = null;
        java.time.LocalDateTime endDate = null;
        if (request.getStartDate() != null && !request.getStartDate().isBlank()) {
            startDate = java.time.LocalDate.parse(request.getStartDate()).atStartOfDay();
        }
        if (request.getEndDate() != null && !request.getEndDate().isBlank()) {
            endDate = java.time.LocalDate.parse(request.getEndDate()).atTime(23, 59, 59);
        }
        final LocalDateTime finalStartDate = startDate;
        final LocalDateTime finalEndDate = endDate;

        // 按人物筛选（优先级最高）
        if (request.getPersonId() != null) {
            validatePersonOwnership(request.getPersonId(), userId);
            photos = photoRepository.findByPersonId(request.getPersonId(), createNativePageable(pageable));
        }
        // 标签筛选
        else if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            if (userId == null) {
                photos = photoRepository.findByTagIds(request.getTagIds(), createNativePageable(pageable));
            } else {
                photos = photoRepository.findByTagIdsAndUserId(request.getTagIds(), userId, createNativePageable(pageable));
            }
        }
        // 颜色筛选（独立筛选条件）
        else if (colorCategory != null && !colorCategory.trim().isEmpty()) {
            if (userId == null) {
                photos = photoRepository.findByColorCategory(colorCategory, minQualityScore, excludePhotoIds, createNativePageable(pageable));
            } else {
                photos = photoRepository.findByColorCategoryAndUserId(
                    colorCategory,
                    userId,
                    minQualityScore,
                    scopedExcludePhotoIds,
                    createNativePageable(pageable)
                );
            }
        }
        // 分类筛选（独立筛选条件）
        else if (category != null && !category.trim().isEmpty()) {
            String normalizedCategory = category.trim();
            List<Album> albums = userId == null
                ? albumRepository.findByTopLevelCategory(normalizedCategory)
                : albumRepository.findByUserIdAndTopLevelCategory(userId, normalizedCategory);
            List<Long> matchingAlbumIds = albums.stream()
                .map(Album::getId)
                .collect(java.util.stream.Collectors.toList());

            if (!matchingAlbumIds.isEmpty()) {
                photos = photoRepository.findByAlbumIds(matchingAlbumIds, pageable);
            } else {
                photos = Page.empty(pageable);
            }
        }
        // EXIF筛选（包括日期范围）
        else if (cameraModel != null || lensModel != null ||
                 minAperture != null || maxAperture != null ||
                 minFocalLength != null || maxFocalLength != null ||
                 minShutterSpeed != null || maxShutterSpeed != null ||
                 minIso != null || maxIso != null ||
                 startDate != null || endDate != null) {
            if (userId == null) {
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
                    startDate,
                    endDate,
                    excludePhotoIds,
                    pageable
                );
            } else {
                photos = photoRepository.findByExifFiltersAndUserId(
                    userId,
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
                    startDate,
                    endDate,
                    scopedExcludePhotoIds,
                    pageable
                );
            }
        }
        // 默认获取所有
        else {
            // 检查是否为随机排序
            boolean isRandomOrder = pageable.getSort().stream()
                .anyMatch(order -> "RAND()".equals(order.getProperty()));

            if (isRandomOrder) {
                // 使用自定义的随机查询方法（排除隐藏的照片）
                if (userId == null) {
                    photos = photoRepository.findAllRandomNotHidden(createNativePageable(pageable));
                } else {
                    photos = photoRepository.findVisibleRandomByUserId(userId, createNativePageable(pageable));
                }
            } else {
                if (userId == null) {
                    photos = photoRepository.findAll(pageable);
                } else {
                    return photoRepository.findVisibleByUserId(userId, pageable).map(this::convertToDTO);
                }
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
        return getPhotoById(id, null);
    }

    public PhotoDTO getPhotoById(Long id, Long userId) {
        Photo photo = photoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("图片不存在"));
        validatePhotoOwnership(photo, userId);
        return convertToDTO(photo);
    }

    public void deletePhoto(Long id) {
        deletePhoto(id, null);
    }

    public void deletePhoto(Long id, Long userId) {
        Photo photo = photoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("图片不存在"));
        validatePhotoOwnership(photo, userId);
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
        return assignPhotoToPerson(photoId, personId, null);
    }

    @Transactional
    public PhotoDTO assignPhotoToPerson(Long photoId, Long personId, Long userId) {
        Photo photo = photoRepository.findById(photoId).orElseThrow(() -> new RuntimeException("图片不存在"));
        validatePhotoOwnership(photo, userId);
        com.photoexhibition.entity.PersonProfile person = personProfileRepository.findById(personId)
            .orElseThrow(() -> new RuntimeException("人物不存在"));
        if (userId != null && !Objects.equals(person.getUserId(), userId)) {
            throw new RuntimeException("人物不存在");
        }

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
        unassignPhoto(photoId, null);
    }

    @Transactional
    public void unassignPhoto(Long photoId, Long userId) {
        Photo photo = photoRepository.findById(photoId)
            .orElseThrow(() -> new RuntimeException("图片不存在"));
        validatePhotoOwnership(photo, userId);
        // 仅删除图片级别的指派记录，不对图片中的人脸做任何解绑处理。
        photoAssignmentRepository.deleteByPhotoId(photoId);
    }

    public Page<PhotoDTO> listPhotosAssignedToPerson(Long personId, Pageable pageable) {
        return listPhotosAssignedToPerson(personId, pageable, null);
    }

    public Page<PhotoDTO> listPhotosAssignedToPerson(Long personId, Pageable pageable, Long userId) {
        validatePersonOwnership(personId, userId);
        Page<com.photoexhibition.entity.PhotoAssignment> page = photoAssignmentRepository.findByPersonId(personId, pageable);
        List<PhotoDTO> dtos = page.getContent().stream()
            .map(pa -> {
                Photo photo = photoRepository.findById(pa.getPhotoId()).orElse(null);
                if (photo == null) {
                    return null;
                }
                if (userId != null && !Objects.equals(photo.getUserId(), userId)) {
                    return null;
                }
                return convertToDTO(photo);
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
        incrementViewCount(id, null);
    }

    @Transactional
    public void incrementViewCount(Long id, Long userId) {
        Photo photo = photoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("图片不存在"));
        validatePhotoOwnership(photo, userId);
        photo.setViewCount(photo.getViewCount() + 1);
        photoRepository.save(photo);
    }

    /**
     * 点赞（增加 likeCount）
     */
    @Transactional
    public int incrementLike(Long id) {
        return incrementLike(id, null);
    }

    @Transactional
    public int incrementLike(Long id, Long userId) {
        Photo photo = photoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("图片不存在"));
        validatePhotoOwnership(photo, userId);
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
        return decrementLike(id, null);
    }

    @Transactional
    public int decrementLike(Long id, Long userId) {
        Photo photo = photoRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("图片不存在"));
        validatePhotoOwnership(photo, userId);
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
        return getLikeCount(id, null);
    }

    public int getLikeCount(Long id, Long userId) {
        return photoRepository.findById(id)
            .map(photo -> {
                validatePhotoOwnership(photo, userId);
                return photo.getLikeCount() == null ? 0 : photo.getLikeCount();
            })
            .orElse(0);
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

    public PhotoDTO convertToDTO(Photo photo) {
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
        dto.setCanonicalPhotoId(photo.getCanonicalPhotoId());
        dto.setCanonicalSource(photo.getCanonicalPhotoId() == null);
        dto.setDuplicateContent(photo.getCanonicalPhotoId() != null);
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
        dto.setIsHidden(photo.getIsHidden());
        if (photo.getTags() != null) {
            // 过滤掉忽略列表中的标签
            Set<String> ignoredTags = systemConfigService.getTagIgnoreListSet();
            dto.setTags(photo.getTags().stream()
                    .filter(tag -> !ignoredTags.contains(tag.getName()))
                    .map(this::toTagDTO)
                    .collect(Collectors.toList()));
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
        String relativePath = userPathService.toRelativePhotoPath(absolutePath, true);
        if (!absolutePath.equals(relativePath)) {
            return relativePath;
        }
        String displayPath = userPathService.toDisplayPath(absolutePath, true);
        if (!absolutePath.equals(displayPath)) {
            return displayPath;
        }
        return sanitizeLeafPath(absolutePath);
    }

    private String sanitizeLeafPath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        if (!path.startsWith("/") && !path.matches("^[A-Za-z]:\\\\.*")) {
            return path;
        }
        String normalized = path.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
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
     * 注意：这里使用实体属性名，Spring Data JPA 会自动映射到数据库列名
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
     * 根据关键词搜索照片（文件名）
     */
    public List<Photo> searchPhotosByFilename(String keyword) {
        return photoRepository.searchByFilename(keyword);
    }

    /**
     * 根据关键词搜索照片并按系统设置排序
     * @param keyword 搜索关键词
     * @return 按设置排序的照片列表
     */
    public List<Photo> searchPhotosByKeyword(String keyword) {
        List<Photo> photos = photoRepository.searchByFilename(keyword);
        return sortPhotosByAlbumRules(photos);
    }

    public List<Photo> searchPhotosByKeyword(String keyword, Long userId) {
        List<Photo> photos = userId == null
            ? photoRepository.searchByFilename(keyword)
            : photoRepository.searchByFilenameAndUserId(keyword, userId);
        return sortPhotosByAlbumRules(photos);
    }

    private List<Photo> sortPhotosByAlbumRules(List<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return photos;
        }

        // 获取相册ID列表
        Set<Long> albumIds = photos.stream()
            .map(Photo::getAlbumId)
            .collect(Collectors.toSet());

        // 获取所有涉及的相册的排序设置
        Map<Long, String> albumSortOrders = new HashMap<>();
        for (Album album : albumRepository.findAllById(albumIds)) {
            if (album.getPhotoSortOrder() != null && !album.getPhotoSortOrder().trim().isEmpty()) {
                albumSortOrders.put(album.getId(), album.getPhotoSortOrder());
            }
        }

        // 获取系统默认排序
        String systemSortOrder = systemConfigService.getPhotoSortOrder();

        // 按各相册的设置排序
        // 由于不同相册可能有不同排序，我们按相册分组后分别排序
        Map<Long, List<Photo>> photosByAlbum = photos.stream()
            .collect(Collectors.groupingBy(Photo::getAlbumId));

        // 扁平化并按各相册的排序规则排序
        List<Photo> sortedPhotos = new java.util.ArrayList<>();
        for (Map.Entry<Long, List<Photo>> entry : photosByAlbum.entrySet()) {
            List<Photo> albumPhotos = entry.getValue();
            String sortOrder = albumSortOrders.getOrDefault(entry.getKey(), systemSortOrder);
            Sort sort = getSortByOrderString(sortOrder);

            // 使用 Stream API 进行排序
            List<Photo> sorted = albumPhotos.stream()
                .sorted((p1, p2) -> {
                    String order = sortOrder != null ? sortOrder : SystemConfigService.SORT_BY_TAKEN_AT_DESC;
                    int cmp = 0;
                    switch (order) {
                        case SystemConfigService.SORT_BY_TAKEN_AT_ASC:
                            cmp = compareNullLast(p1.getTakenAt(), p2.getTakenAt(), true);
                            break;
                        case SystemConfigService.SORT_BY_FILENAME_ASC:
                            cmp = compareNullLast(p1.getFilename(), p2.getFilename(), true);
                            break;
                        case SystemConfigService.SORT_BY_FILENAME_DESC:
                            cmp = compareNullLast(p2.getFilename(), p1.getFilename(), true);
                            break;
                        case SystemConfigService.SORT_BY_CREATED_AT_ASC:
                            cmp = compareNullLast(p1.getCreatedAt(), p2.getCreatedAt(), true);
                            break;
                        case SystemConfigService.SORT_BY_CREATED_AT_DESC:
                            cmp = compareNullLast(p2.getCreatedAt(), p1.getCreatedAt(), true);
                            break;
                        case SystemConfigService.SORT_BY_TAKEN_AT_DESC:
                        default:
                            cmp = compareNullLast(p2.getTakenAt(), p1.getTakenAt(), true);
                            break;
                    }
                    return cmp;
                })
                .collect(Collectors.toList());
            sortedPhotos.addAll(sorted);
        }

        return sortedPhotos;
    }

    private boolean matchesExifFilters(Photo photo,
                                       String cameraModel,
                                       String lensModel,
                                       Double minAperture,
                                       Double maxAperture,
                                       Double minFocalLength,
                                       Double maxFocalLength,
                                       Double minShutterSpeed,
                                       Double maxShutterSpeed,
                                       Integer minIso,
                                       Integer maxIso,
                                       String colorCategory,
                                       Double minQualityScore,
                                       LocalDateTime startDate,
                                       LocalDateTime endDate,
                                       List<Long> excludePhotoIds) {
        if (cameraModel != null && !cameraModel.equals(photo.getCameraModel())) return false;
        if (lensModel != null && !lensModel.equals(photo.getLensModel())) return false;
        if (minAperture != null && (photo.getApertureValue() == null || photo.getApertureValue() < minAperture)) return false;
        if (maxAperture != null && (photo.getApertureValue() == null || photo.getApertureValue() > maxAperture)) return false;
        if (minFocalLength != null && (photo.getFocalLengthMm() == null || photo.getFocalLengthMm() < minFocalLength)) return false;
        if (maxFocalLength != null && (photo.getFocalLengthMm() == null || photo.getFocalLengthMm() > maxFocalLength)) return false;
        if (minShutterSpeed != null && (photo.getShutterSpeedSeconds() == null || photo.getShutterSpeedSeconds() < minShutterSpeed)) return false;
        if (maxShutterSpeed != null && (photo.getShutterSpeedSeconds() == null || photo.getShutterSpeedSeconds() > maxShutterSpeed)) return false;
        if (minIso != null && (photo.getIso() == null || photo.getIso() < minIso)) return false;
        if (maxIso != null && (photo.getIso() == null || photo.getIso() > maxIso)) return false;
        if (colorCategory != null && !colorCategory.equals(photo.getColorCategory())) return false;
        if (minQualityScore != null && (photo.getQualityScore() == null || photo.getQualityScore() < minQualityScore)) return false;
        if (startDate != null && (photo.getTakenAt() == null || photo.getTakenAt().isBefore(startDate))) return false;
        if (endDate != null && (photo.getTakenAt() == null || photo.getTakenAt().isAfter(endDate))) return false;
        return excludePhotoIds == null || !excludePhotoIds.contains(photo.getId());
    }

    private Comparator<Photo> toPhotoComparator(Sort sort) {
        List<Sort.Order> orders = sort.stream().collect(Collectors.toList());
        if (orders.isEmpty()) {
            orders = Collections.singletonList(new Sort.Order(Sort.Direction.DESC, "takenAt"));
        }
        List<Sort.Order> finalOrders = orders;
        return (p1, p2) -> {
            for (Sort.Order order : finalOrders) {
                int cmp;
                switch (order.getProperty()) {
                    case "filename":
                        cmp = compareNullable(p1.getFilename(), p2.getFilename());
                        break;
                    case "createdAt":
                        cmp = compareNullable(p1.getCreatedAt(), p2.getCreatedAt());
                        break;
                    case "qualityScore":
                        cmp = compareNullable(p1.getQualityScore(), p2.getQualityScore());
                        break;
                    case "takenAt":
                    default:
                        cmp = compareNullable(p1.getTakenAt(), p2.getTakenAt());
                        break;
                }
                if (cmp != 0) {
                    return order.isAscending() ? cmp : -cmp;
                }
            }
            return 0;
        };
    }

    private void validatePersonOwnership(Long personId, Long userId) {
        if (userId == null) {
            return;
        }
        PersonProfile person = personProfileRepository.findById(personId)
            .orElseThrow(() -> new RuntimeException("人物不存在"));
        if (!Objects.equals(person.getUserId(), userId)) {
            throw new RuntimeException("人物不存在");
        }
    }

    private void validateAlbumOwnership(Long albumId, Long userId) {
        if (userId == null) {
            return;
        }
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        if (!Objects.equals(album.getUserId(), userId)) {
            throw new RuntimeException("相册不存在");
        }
    }

    private void validatePhotoOwnership(Photo photo, Long userId) {
        if (userId != null && !Objects.equals(photo.getUserId(), userId)) {
            throw new RuntimeException("图片不存在");
        }
    }

    private <T> Page<T> paginateList(List<T> items, Pageable pageable) {
        int start = (int) pageable.getOffset();
        if (start >= items.size()) {
            return new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList(), pageable, items.size());
        }
        int end = Math.min(start + pageable.getPageSize(), items.size());
        return new org.springframework.data.domain.PageImpl<>(items.subList(start, end), pageable, items.size());
    }

    /**
     * 比较两个可比较对象，处理null值
     */
    private <T extends Comparable<T>> int compareNullLast(T a, T b, boolean nullsLast) {
        if (a == null && b == null) return 0;
        if (a == null) return nullsLast ? 1 : -1;
        if (b == null) return nullsLast ? -1 : 1;
        return a.compareTo(b);
    }

    /**
     * 将实体属性名转换为数据库列名（用于原生查询的排序）
     */
    private String propertyToColumn(String property) {
        switch (property) {
            case "takenAt":
                return "taken_at";
            case "createdAt":
                return "created_at";
            case "filename":
                return "filename";
            default:
                return property;
        }
    }

    /**
     * 创建用于原生查询的Pageable（将实体属性名转换为数据库列名）
     */
    private Pageable createNativePageable(Pageable pageable) {
        if (pageable.getSort().isEmpty()) {
            return pageable;
        }
        List<org.springframework.data.domain.Sort.Order> nativeOrders = pageable.getSort().stream()
                .map(order -> new org.springframework.data.domain.Sort.Order(
                        order.getDirection(),
                        propertyToColumn(order.getProperty())
                ))
                .collect(java.util.stream.Collectors.toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(nativeOrders));
    }

    /**
     * 获取需要聚合的相册ID列表
     * 如果相册开启了聚合下级相册，返回该相册及其所有子相册的ID
     * 否则只返回当前相册ID
     */
    /**
     * 获取聚合相册的所有子相册ID列表（调用AlbumService的方法）
     */
    private List<Long> getAggregatedAlbumIds(Long albumId) {
        // 调用 AlbumService 的方法，确保使用一致的逻辑
        return albumService.getAggregatedAlbumIds(albumId);
    }

    /**
     * 获取多个相册中的所有照片（用于聚合模式，排除隐藏的照片）
     */
    private Page<PhotoDTO> getPhotosByAlbumIds(List<Long> albumIds, Pageable pageable) {
        // 使用自定义查询获取多个相册的照片
        List<Photo> allPhotos = new java.util.ArrayList<>();
        int totalElements = 0;

        // 分别查询每个相册的照片（排除隐藏），然后合并
        for (Long albumId : albumIds) {
            List<Photo> albumPhotos = photoRepository.findByAlbumIdAndIsHiddenFalse(albumId, PageRequest.of(0, 1000)).getContent();
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
