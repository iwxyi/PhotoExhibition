package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.dto.FilterRequest;
import com.photoexhibition.dto.PhotoDTO;
import com.photoexhibition.dto.FaceDTO;
import com.photoexhibition.dto.TagDTO;
import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PhotoRepository;
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

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final FaceRepository faceRepository;
    private final AlbumRepository albumRepository;
    private final ObjectMapper objectMapper;
    private final SystemConfigService systemConfigService;

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
        Integer minIso = request.getMinIso();
        Integer maxIso = request.getMaxIso();

        // 按人物筛选（优先级最高）
        if (request.getPersonId() != null) {
            photos = photoRepository.findByPersonId(request.getPersonId(), pageable);
        }
        // 标签筛选
        else if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            photos = photoRepository.findByTagIds(request.getTagIds(), pageable);
        }
        // EXIF筛选
        else if (cameraModel != null || lensModel != null ||
                 minAperture != null || maxAperture != null ||
                 minIso != null || maxIso != null) {
            photos = photoRepository.findByExifFilters(
                cameraModel,
                lensModel,
                minAperture,
                maxAperture,
                minIso,
                maxIso,
                pageable
            );
        }
        // 默认获取所有
        else {
            photos = photoRepository.findAll(pageable);
        }

        // 进一步筛选（色彩、质量评分等）
        List<PhotoDTO> filtered = photos.getContent().stream()
            .map(this::convertToDTO)
            .filter(dto -> {
                if (request.getDominantColor() != null && 
                    !request.getDominantColor().equals(dto.getDominantColor())) {
                    return false;
                }
                if (request.getMinQualityScore() != null && 
                    (dto.getQualityScore() == null || dto.getQualityScore() < request.getMinQualityScore())) {
                    return false;
                }
                return true;
            })
            .collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(filtered, pageable, filtered.size());
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
        photoRepository.deleteById(id);
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
        dto.setAperture(photo.getAperture());
        dto.setShutterSpeed(photo.getShutterSpeed());
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

