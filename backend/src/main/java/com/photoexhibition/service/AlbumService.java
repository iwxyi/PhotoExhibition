package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.CoverImagesDTO;
import com.photoexhibition.dto.PhotoDTO;
import com.photoexhibition.dto.TagDTO;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
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
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${photo.scan.base-path}")
    private String photoBasePath;

    /**
     * 获取所有相册并生成封面（只返回有照片的相册）
     * 注意：Page对象不缓存，因为反序列化会有问题
     */
    public Page<AlbumDTO> getAllAlbumsWithCover(Pageable pageable) {
        // 只查询有照片的相册
        Page<Album> albums = albumRepository.findAlbumsWithPhotos(pageable);
        return albums.map(this::convertToDTO);
    }

    /**
     * 筛选相册（只返回有照片的相册）
     */
    public Page<AlbumDTO> filterAlbums(com.photoexhibition.dto.FilterRequest request, Pageable pageable) {
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            // 只查询有照片的相册
            Page<Album> albums = albumRepository.findByTagIdsWithPhotos(request.getTagIds(), pageable);
            return albums.map(this::convertToDTO);
        }
        return getAllAlbumsWithCover(pageable);
    }

    /**
     * 获取相册详情
     * 注意：暂时不缓存，避免反序列化问题
     */
    public AlbumDTO getAlbumById(Long id) {
        Album album = albumRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        return convertToDTO(album);
    }

    /**
     * 获取相册封面图片组合（左侧竖图+右侧上下两张横图）
     */
    public CoverImagesDTO getAlbumCoverImages(Long albumId) {
        List<Photo> photos = photoRepository.findByAlbumId(albumId, 
            org.springframework.data.domain.PageRequest.of(0, 20)) // 增加查询数量，确保能找到合适的图片
            .getContent();

        CoverImagesDTO cover = new CoverImagesDTO();
        
        if (photos.isEmpty()) {
            return cover;
        }

        // 查找竖图（高度>宽度）
        Photo verticalPhoto = photos.stream()
            .filter(p -> p.getHeight() != null && p.getWidth() != null && p.getHeight() > p.getWidth())
            .findFirst()
            .orElse(null);

        // 查找横图（宽度>高度）
        List<Photo> horizontalPhotos = photos.stream()
            .filter(p -> p.getHeight() != null && p.getWidth() != null && p.getWidth() > p.getHeight())
            .limit(2)
            .collect(Collectors.toList());

        // 如果找不到竖图，使用第一张图片（不区分方向）
        if (verticalPhoto == null && !photos.isEmpty()) {
            verticalPhoto = photos.get(0);
        }

        // 如果找不到横图，使用前两张图片（不区分方向）
        List<Photo> finalHorizontalPhotos;
        if (horizontalPhotos.isEmpty() && photos.size() > 1) {
            finalHorizontalPhotos = photos.stream()
                .limit(2)
                .collect(Collectors.toList());
        } else if (horizontalPhotos.isEmpty() && photos.size() == 1) {
            // 如果只有一张图片且是竖图，右侧也用这张图
            finalHorizontalPhotos = List.of(photos.get(0));
        } else {
            finalHorizontalPhotos = horizontalPhotos;
        }

        if (verticalPhoto != null) {
            cover.setLeftVertical(convertPhotoToDTO(verticalPhoto));
        }

        if (finalHorizontalPhotos.size() > 0) {
            cover.setRightTop(convertPhotoToDTO(finalHorizontalPhotos.get(0)));
        }

        if (finalHorizontalPhotos.size() > 1) {
            cover.setRightBottom(convertPhotoToDTO(finalHorizontalPhotos.get(1)));
        } else if (finalHorizontalPhotos.size() == 1 && photos.size() > 1) {
            // 如果只有一张横图，第二张使用其他图片
            Photo secondPhoto = photos.stream()
                .filter(p -> !p.getId().equals(finalHorizontalPhotos.get(0).getId()))
                .findFirst()
                .orElse(null);
            if (secondPhoto != null) {
                cover.setRightBottom(convertPhotoToDTO(secondPhoto));
            }
        }

        return cover;
    }

    /**
     * 转换为DTO
     */
    private AlbumDTO convertToDTO(Album album) {
        AlbumDTO dto = new AlbumDTO();
        dto.setId(album.getId());
        dto.setName(album.getName());
        dto.setPath(album.getPath());
        dto.setCoverImageId(album.getCoverImageId());
        dto.setDescription(album.getDescription());
        dto.setPhotoCount(album.getPhotoCount());
        dto.setCreatedAt(album.getCreatedAt());
        dto.setUpdatedAt(album.getUpdatedAt());

        if (album.getTags() != null) {
            dto.setTags(album.getTags().stream()
                .map(tag -> {
                    TagDTO tagDTO = new TagDTO();
                    tagDTO.setId(tag.getId());
                    tagDTO.setName(tag.getName());
                    tagDTO.setColor(tag.getColor());
                    return tagDTO;
                })
                .collect(Collectors.toList()));
        }

        // 生成封面图片（只有有照片的相册才生成封面）
        if (album.getPhotoCount() != null && album.getPhotoCount() > 0) {
            dto.setCoverImages(getAlbumCoverImages(album.getId()));
        } else {
            // 没有照片的相册，设置空的封面
            dto.setCoverImages(new CoverImagesDTO());
        }

        return dto;
    }

    /**
     * 转换Photo为DTO
     */
    private PhotoDTO convertPhotoToDTO(Photo photo) {
        PhotoDTO dto = new PhotoDTO();
        dto.setId(photo.getId());
        dto.setAlbumId(photo.getAlbumId());
        dto.setFilename(photo.getFilename());
        // 将绝对路径转换为相对路径（相对于base-path）
        dto.setOriginalPath(convertToRelativePath(photo.getOriginalPath()));
        dto.setThumbnailPath(convertToRelativePath(photo.getThumbnailPath()));
        dto.setWebpPath(convertToRelativePath(photo.getWebpPath()));
        dto.setFileSize(photo.getFileSize());
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
        dto.setViewCount(photo.getViewCount());
        dto.setIsFeatured(photo.getIsFeatured());
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
}

