package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.CoverImagesDTO;
import com.photoexhibition.dto.PhotoDTO;
import com.photoexhibition.dto.TagDTO;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final TagRepository tagRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${photo.scan.base-path}")
    private String photoBasePath;

    /**
     * 获取所有相册并生成封面（只返回有照片的相册）
     * 注意：Page对象不缓存，因为反序列化会有问题
     */
    public Page<AlbumDTO> getAllAlbumsWithCover(Pageable pageable, String category) {
        Page<Album> albums;
        if (category != null && !category.isEmpty()) {
            String prefix = buildCategoryPrefix(category);
            albums = albumRepository.findByPathStartingWithAndPhotoCountGreaterThan(prefix, 0, pageable);
        } else {
            // 只查询有照片的相册
            albums = albumRepository.findAlbumsWithPhotos(pageable);
        }
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
        return getAllAlbumsWithCover(pageable, null);
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
     * 更新相册基础信息（名称/描述）
     */
    public AlbumDTO updateAlbum(Long id, AlbumDTO dto) {
        Album album = albumRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        if (dto.getName() != null && !dto.getName().isEmpty()) {
            album.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            album.setDescription(dto.getDescription());
        }
        Album saved = albumRepository.save(album);
        return convertToDTO(saved);
    }

    /**
     * 删除相册（会级联删除照片记录）
     */
    public void deleteAlbum(Long id) {
        if (!albumRepository.existsById(id)) {
            throw new RuntimeException("相册不存在");
        }
        // 照片表album_id外键未声明级联，这里直接删相册记录，其它清理由DB外键/应用控制
        // 若需同时删除照片，请在PhotoRepository中按albumId删除
        albumRepository.deleteById(id);
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

        Photo verticalPhoto = null;
        List<Photo> horizontalPhotos = new java.util.ArrayList<>(2);

        // 第一轮：优先找到竖图和横图
        for (Photo p : photos) {
            if (p.getHeight() != null && p.getWidth() != null && p.getHeight() > p.getWidth()) {
                if (verticalPhoto == null) {
                    verticalPhoto = p;
                    continue;
                }
            }
            if (p.getHeight() != null && p.getWidth() != null && p.getWidth() > p.getHeight()) {
                if (horizontalPhotos.size() < 2) {
                    horizontalPhotos.add(p);
                }
            }
            if (verticalPhoto != null && horizontalPhotos.size() >= 2) {
                break;
            }
        }

        // 竖图兜底：没有竖图就用第一张
        if (verticalPhoto == null) {
            verticalPhoto = photos.get(0);
        }

        // 横图兜底：不够两张，用剩余未使用的照片补齐
        if (horizontalPhotos.size() < 2) {
            for (Photo p : photos) {
                if (horizontalPhotos.size() >= 2) break;
                if (p.getId().equals(verticalPhoto.getId())) continue;
                boolean alreadyUsed = horizontalPhotos.stream().anyMatch(h -> h.getId().equals(p.getId()));
                if (!alreadyUsed) {
                    horizontalPhotos.add(p);
                }
            }
        }

        // 赋值封面
        cover.setLeftVertical(convertPhotoToDTO(verticalPhoto));
        if (horizontalPhotos.size() > 0) {
            cover.setRightTop(convertPhotoToDTO(horizontalPhotos.get(0)));
        }
        if (horizontalPhotos.size() > 1) {
            cover.setRightBottom(convertPhotoToDTO(horizontalPhotos.get(1)));
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
        dto.setDisplayTitle(buildDisplayTitle(album));
        dto.setCategory(extractCategory(album));

        // 设置相册拍摄时间：取相册最早的拍摄时间，若无则为空
        photoRepository.findTopByAlbumIdOrderByTakenAtAsc(album.getId())
            .map(Photo::getTakenAt)
            .ifPresent(dto::setTakenAt);

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
     * 获取所有一级分类（base-path 下的第一层目录）
     */
    public List<String> getCategories() {
        List<Album> albums = albumRepository.findAll();
        List<String> categories = new ArrayList<>();
        for (Album a : albums) {
            String c = extractCategory(a);
            if (c != null && !c.isEmpty() && !categories.contains(c)) {
                categories.add(c);
            }
        }
        return categories;
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

    /**
     * 生成展示用标题：去掉首段日期前缀，并将层级目录拼接
     */
    private String buildDisplayTitle(Album album) {
        try {
            String albumPath = album.getPath();
            if (albumPath == null || albumPath.isEmpty()) {
                return stripDatePrefix(album.getName());
            }

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
            Path relative;
            if (albumRealPath.startsWith(basePathResolved)) {
                relative = basePathResolved.relativize(albumRealPath);
            } else {
                relative = albumRealPath.getFileName();
            }

            List<String> parts = new ArrayList<>();
            for (int i = 0; i < relative.getNameCount(); i++) {
                String part = relative.getName(i).toString().trim();
                if (part.isEmpty()) continue;
                if (i == 0) {
                    part = stripDatePrefix(part);
                    // 忽略分类（base-path 下的第一层目录）
                    continue;
                }
                // 从第二层开始，去掉日期前缀
                part = stripDatePrefix(part);
                if (!part.isEmpty()) {
                    parts.add(part);
                }
            }

            if (parts.isEmpty()) {
                return stripDatePrefix(album.getName());
            }
            // 用“ / ”拼接层级，保留原有子目录信息
            return String.join(" / ", parts);
        } catch (Exception e) {
            return stripDatePrefix(album.getName());
        }
    }

    /**
     * 去掉形如 2025-11-12 或 2025.11.12 的日期前缀
     */
    private String stripDatePrefix(String name) {
        if (name == null) return "";
        return name.replaceFirst("^\\d{4}[\\.-]?\\d{2}[\\.-]?\\d{2}\\s*", "").trim();
    }

    /**
    * 提取一级分类（base-path 下的第一段目录名）
    */
    private String extractCategory(Album album) {
        try {
            String albumPath = album.getPath();
            if (albumPath == null || albumPath.isEmpty()) return "";

            Path basePathResolved = resolveBasePath();
            Path albumRealPath = Paths.get(albumPath).normalize();
            if (!albumRealPath.startsWith(basePathResolved)) {
                return "";
            }
            Path relative = basePathResolved.relativize(albumRealPath);
            if (relative.getNameCount() > 0) {
                return relative.getName(0).toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * 构造分类前缀绝对路径
     */
    private String buildCategoryPrefix(String category) {
        Path base = resolveBasePath();
        return base.resolve(category).toAbsolutePath().normalize().toString();
    }

    private Path resolveBasePath() {
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
        return basePathResolved.normalize();
    }

    /**
     * 为相册添加标签
     */
    @Transactional
    public AlbumDTO addTagToAlbum(Long albumId, Long tagId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        Tag tag = tagRepository.findById(tagId)
            .orElseThrow(() -> new RuntimeException("标签不存在"));
        
        if (album.getTags() == null) {
            album.setTags(new ArrayList<>());
        }
        
        // 检查是否已存在该标签
        boolean exists = album.getTags().stream()
            .anyMatch(t -> t.getId().equals(tagId));
        
        if (!exists) {
            album.getTags().add(tag);
            Album saved = albumRepository.save(album);
            // 重新获取以确保标签关系被正确加载
            saved = albumRepository.findById(saved.getId())
                .orElseThrow(() -> new RuntimeException("相册不存在"));
            return convertToDTO(saved);
        }
        
        return convertToDTO(album);
    }

    /**
     * 从相册移除标签
     */
    @Transactional
    public AlbumDTO removeTagFromAlbum(Long albumId, Long tagId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        
        if (album.getTags() != null) {
            album.getTags().removeIf(t -> t.getId().equals(tagId));
            albumRepository.save(album);
        }
        
        return convertToDTO(album);
    }
}

