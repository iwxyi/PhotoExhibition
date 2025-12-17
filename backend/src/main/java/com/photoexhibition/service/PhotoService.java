package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.dto.FilterRequest;
import com.photoexhibition.dto.PhotoDTO;
import com.photoexhibition.dto.FaceDTO;
import com.photoexhibition.dto.TagDTO;
import com.photoexhibition.entity.Face;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final FaceRepository faceRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${photo.scan.base-path}")
    private String photoBasePath;

    /**
     * 获取所有图片（图墙模式）
     * 注意：Page对象不缓存，因为反序列化会有问题
     */
    public Page<PhotoDTO> getAllPhotos(Pageable pageable) {
        Page<Photo> photos = photoRepository.findAll(pageable);
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
        Page<Photo> photos = photoRepository.findByAlbumId(albumId, pageable);
        return photos.map(this::convertToDTO);
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
}

