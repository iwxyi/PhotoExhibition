package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.AtmosphereEffectDTO;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlbumService {

    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final TagRepository tagRepository;
    private final ObjectMapper objectMapper;
    private final PhotoService photoService;
    
    @Value("${photo.scan.base-path}")
    private String photoBasePath;

    /**
     * 获取所有相册并生成封面（只返回有照片的相册或开启了聚合的相册）
     * 注意：Page对象不缓存，因为反序列化会有问题
     */
    public Page<AlbumDTO> getAllAlbumsWithCover(Pageable pageable, String category) {
        Page<Album> albums;
        if (category != null && !category.isEmpty()) {
            String prefix = buildCategoryPrefix(category);
            albums = albumRepository.findByPathStartingWithAndPhotoCountGreaterThan(prefix, 0, pageable);
        } else {
            // 查询有照片的相册或开启了聚合功能的相册
            albums = albumRepository.findAlbumsWithPhotosOrAggregation(pageable);
        }

        // 过滤掉被聚合的相册
        List<Album> filteredAlbums = filterAggregatedAlbums(albums.getContent());

        // 重新创建Page对象
        return new org.springframework.data.domain.PageImpl<>(
            filteredAlbums.stream().map(this::convertToDTO).collect(java.util.stream.Collectors.toList()),
            pageable,
            filteredAlbums.size()
        );
    }

    /**
     * 筛选相册（只返回有照片的相册或开启了聚合的相册）
     */
    public Page<AlbumDTO> filterAlbums(com.photoexhibition.dto.FilterRequest request, Pageable pageable) {
        Page<Album> albums;
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            // 查询有照片且带标签的相册，或开启了聚合功能的相册
            albums = albumRepository.findByTagIdsWithPhotosOrAggregation(request.getTagIds(), pageable);
        } else {
            albums = albumRepository.findAlbumsWithPhotosOrAggregation(pageable);
        }

        // 过滤掉被聚合的相册
        List<Album> filteredAlbums = filterAggregatedAlbums(albums.getContent());

        // 重新创建Page对象
        return new org.springframework.data.domain.PageImpl<>(
            filteredAlbums.stream().map(this::convertToDTO).collect(java.util.stream.Collectors.toList()),
            pageable,
            filteredAlbums.size()
        );
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
        // 检查相册是否开启了聚合
        List<Long> albumIds = getAggregatedAlbumIds(albumId);

        List<Photo> photos;
        if (albumIds.size() == 1) {
            // 非聚合相册：从单个相册取前 10 张照片（按相册排序）
            org.springframework.data.domain.Sort sort = photoService.getPhotoSort(albumId);
            photos = photoRepository.findByAlbumId(albumId,
                org.springframework.data.domain.PageRequest.of(0, 10, sort))
                .getContent();
        } else {
            // 聚合相册：获取所有照片并按瀑布流排序，然后取前几张
            photos = new java.util.ArrayList<>();
            for (Long id : albumIds) {
                List<Photo> albumPhotos = photoRepository.findByAlbumId(id,
                    org.springframework.data.domain.PageRequest.of(0, 1000)) // 每个相册取1000张
                    .getContent();
                photos.addAll(albumPhotos);
            }

            // 按相册排序规则对所有照片排序（与瀑布流排序一致）
            org.springframework.data.domain.Sort sort = photoService.getPhotoSort(albumId);
            photos.sort((p1, p2) -> {
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

            // 只取前10张用于生成封面（优先保证封面与瀑布流前端排序一致）
            if (photos.size() > 10) {
                photos = photos.subList(0, 10);
            }
        }

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

        // 确保横图不与竖图重复，并优先使用真正的横图
        // 当整组照片都是横图时，避免左竖图和右上图使用同一张
        List<Photo> cleanHorizontal = new java.util.ArrayList<>(2);
        for (Photo p : photos) {
            if (p.getId().equals(verticalPhoto.getId())) {
                continue;
            }
            if (p.getHeight() != null && p.getWidth() != null && p.getWidth() > p.getHeight()) {
                cleanHorizontal.add(p);
            }
            if (cleanHorizontal.size() >= 2) {
                break;
            }
        }
        // 横图兜底：不够两张，用剩余未使用的照片补齐（仍然避免与竖图和已选横图重复）
        if (cleanHorizontal.size() < 2) {
            for (Photo p : photos) {
                if (cleanHorizontal.size() >= 2) break;
                if (p.getId().equals(verticalPhoto.getId())) continue;
                boolean alreadyUsed = cleanHorizontal.stream().anyMatch(h -> h.getId().equals(p.getId()));
                if (!alreadyUsed) {
                    cleanHorizontal.add(p);
                }
            }
        }
        horizontalPhotos = cleanHorizontal;

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
     * 比较可空的可比较对象
     */
    private <T extends Comparable<T>> int compareNullable(T a, T b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
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
        dto.setAggregateSubAlbums(album.getAggregateSubAlbums());
        dto.setPhotoSortOrder(album.getPhotoSortOrder());

        // 检查是否有子文件夹（用于聚合功能）
        boolean hasSubs = false;
        try {
            Path albumPath = Paths.get(album.getPath());
            if (Files.exists(albumPath) && Files.isDirectory(albumPath)) {
                try (Stream<Path> stream = Files.list(albumPath)) {
                    hasSubs = stream.anyMatch(path -> Files.isDirectory(path) &&
                        !path.getFileName().toString().equals(".thumbnails"));
                }
            }
        } catch (Exception e) {
            // 忽略错误，默认为没有子文件夹
            log.debug("检查相册子文件夹失败: {} - {}", album.getName(), e.getMessage());
        }
        dto.setHasSubAlbums(hasSubs);

        // 检查是否是顶级相册（不能聚合到上一级）
        // 顶级相册是指在base-path/分类/下的相册
        Path basePathResolved = resolveBasePath();
        Path albumPath = Paths.get(album.getPath());
        int depth = 0;
        boolean pathMatches = false;

        try {
            pathMatches = albumPath.startsWith(basePathResolved);
            if (pathMatches) {
                Path relativePath = basePathResolved.relativize(albumPath);
                depth = relativePath.getNameCount();
            }
        } catch (Exception e) {
            log.debug("路径计算失败: {}", e.getMessage());
        }

        boolean isTop = (depth == 2);

        // 如果路径匹配失败，使用备用逻辑：检查路径是否以basePath开头且相对深度为2
        if (!pathMatches || depth == 0) {
            String albumPathStr = album.getPath();
            String basePathStr = basePathResolved.toString();
            if (albumPathStr.startsWith(basePathStr)) {
                String relative = albumPathStr.substring(basePathStr.length());
                if (relative.startsWith("/")) {
                    relative = relative.substring(1);
                }
                String[] parts = relative.split("/");
                depth = parts.length;
                isTop = (depth == 2);
            }
        }

        dto.setIsTopLevel(isTop);

        // 如果开启了聚合，计算所有子相册的照片总数
        if (Boolean.TRUE.equals(album.getAggregateSubAlbums())) {
            List<Long> aggregatedAlbumIds = getAggregatedAlbumIds(album.getId());
            int totalPhotoCount = 0;
            for (Long id : aggregatedAlbumIds) {
                Long count = photoRepository.countByAlbumId(id);
                totalPhotoCount += count.intValue();
            }
            dto.setPhotoCount(totalPhotoCount);
        }

        dto.setCreatedAt(album.getCreatedAt());
        dto.setUpdatedAt(album.getUpdatedAt());
        dto.setDisplayTitle(buildDisplayTitle(album));
        dto.setCategory(extractCategory(album));

        // 设置相册拍摄时间：取相册最早的拍摄时间，若无则为空
        // 对于聚合相册，如果没有直接图片，从子相册中取最早时间
        java.util.Optional<java.time.LocalDateTime> takenAt = photoRepository.findTopByAlbumIdOrderByTakenAtAsc(album.getId())
            .map(Photo::getTakenAt);

        if (takenAt.isEmpty() && Boolean.TRUE.equals(album.getAggregateSubAlbums())) {
            // 从子相册中找到最早的拍摄时间
            takenAt = findEarliestTakenAtFromSubAlbums(album.getPath());
        }

        takenAt.ifPresent(dto::setTakenAt);

        // 设置氛围信息
        dto.setBackgroundColor(album.getBackgroundColor());
        dto.setForegroundColor(album.getForegroundColor());
        dto.setNavbarColor(album.getNavbarColor());

        // 解析氛围特效
        if (album.getAtmosphereEffects() != null) {
            try {
                java.util.List<AtmosphereEffectDTO> effects = objectMapper.readValue(
                    album.getAtmosphereEffects(),
                    objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, AtmosphereEffectDTO.class)
                );
                dto.setAtmosphereEffects(effects);
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

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

        // 生成封面图片（有照片的相册或开启了聚合的相册才生成封面）
        boolean shouldGenerateCover = (album.getPhotoCount() != null && album.getPhotoCount() > 0) ||
                                      Boolean.TRUE.equals(album.getAggregateSubAlbums());

        if (shouldGenerateCover) {
            dto.setCoverImages(getAlbumCoverImages(album.getId()));
        } else {
            // 没有照片且未开启聚合的相册，设置空的封面
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

    /**
     * 从子相册中找到最早的拍摄时间（用于聚合相册）
     */
    private java.util.Optional<java.time.LocalDateTime> findEarliestTakenAtFromSubAlbums(String parentPath) {
        try {
            // 查找所有直接子相册
            java.util.List<Album> subAlbums = albumRepository.findDirectSubAlbums(parentPath);
            if (subAlbums.isEmpty()) {
                return java.util.Optional.empty();
            }

            java.time.LocalDateTime earliest = null;
            for (Album subAlbum : subAlbums) {
                // 递归查找子相册的最早时间
                java.util.Optional<java.time.LocalDateTime> subTakenAt = findEarliestTakenAtFromAlbum(subAlbum);
                if (subTakenAt.isPresent()) {
                    if (earliest == null || subTakenAt.get().isBefore(earliest)) {
                        earliest = subTakenAt.get();
                    }
                }
            }
            return java.util.Optional.ofNullable(earliest);
        } catch (Exception e) {
            log.warn("查找子相册最早拍摄时间失败: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    /**
     * 从相册及其子相册中找到最早的拍摄时间
     */
    private java.util.Optional<java.time.LocalDateTime> findEarliestTakenAtFromAlbum(Album album) {
        // 首先检查相册是否有直接图片
        java.util.Optional<java.time.LocalDateTime> directTakenAt = photoRepository.findTopByAlbumIdOrderByTakenAtAsc(album.getId())
            .map(Photo::getTakenAt);

        if (directTakenAt.isPresent()) {
            return directTakenAt;
        }

        // 如果没有直接图片且开启了聚合，递归查找子相册
        if (Boolean.TRUE.equals(album.getAggregateSubAlbums())) {
            return findEarliestTakenAtFromSubAlbums(album.getPath());
        }

        return java.util.Optional.empty();
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

    /**
     * 设置相册聚合下级相册
     */
    @Transactional
    public AlbumDTO setAggregateSubAlbums(Long albumId, Boolean aggregate) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));

        album.setAggregateSubAlbums(aggregate != null ? aggregate : false);
        Album saved = albumRepository.save(album);

        return convertToDTO(saved);
    }

    /**
     * 设置相册照片排序方式
     */
    @Transactional
    public AlbumDTO setAlbumPhotoSortOrder(Long albumId, String sortOrder) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));

        // 如果传入null或空字符串，则清除相册级别的排序设置，使用全局设置
        if (sortOrder == null || sortOrder.trim().isEmpty()) {
            album.setPhotoSortOrder(null);
        } else {
            // 验证排序方式
            if (!isValidSortOrder(sortOrder)) {
                throw new IllegalArgumentException("无效的排序方式: " + sortOrder);
            }
            album.setPhotoSortOrder(sortOrder.trim());
        }

        Album saved = albumRepository.save(album);
        return convertToDTO(saved);
    }

    /**
     * 创建相册（如果不存在的话）
     */
    @Transactional
    public AlbumDTO createAlbumIfNotExists(String path) {
        // 检查路径是否已经存在相册
        Optional<Album> existingAlbum = albumRepository.findByPath(path);
        if (existingAlbum.isPresent()) {
            return convertToDTO(existingAlbum.get());
        }

        // 检查路径是否存在且是目录
        java.io.File dir = new java.io.File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("路径不存在或不是目录: " + path);
        }

        // 创建新相册
        Album newAlbum = new Album();
        newAlbum.setName(dir.getName());
        newAlbum.setPath(path);
        newAlbum.setPathHash(calculateSha256(path));

        Album saved = albumRepository.save(newAlbum);
        return convertToDTO(saved);
    }

    /**
     * 计算字符串的SHA-256哈希
     */
    private String calculateSha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算哈希失败", e);
        }
    }

    /**
     * 过滤掉被聚合的相册
     * 如果一个相册被其父相册聚合了，就不在列表中显示
     */
    private List<Album> filterAggregatedAlbums(List<Album> albums) {
        // 获取所有开启了聚合功能的相册
        List<Album> aggregatingAlbums = albumRepository.findAlbumsWithAggregationEnabled();

        // 收集所有被聚合的相册路径
        java.util.Set<String> aggregatedPaths = new java.util.HashSet<>();
        for (Album aggregatingAlbum : aggregatingAlbums) {
            // 递归收集所有子相册路径
            collectSubAlbumPaths(aggregatingAlbum.getPath(), aggregatedPaths);
        }

        // 过滤掉被聚合的相册
        return albums.stream()
            .filter(album -> !aggregatedPaths.contains(album.getPath()))
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 递归收集指定相册的所有子相册路径
     */
    private void collectSubAlbumPaths(String parentPath, java.util.Set<String> paths) {
        List<Album> subAlbums = albumRepository.findDirectSubAlbums(parentPath);
        for (Album subAlbum : subAlbums) {
            paths.add(subAlbum.getPath());
            // 如果子相册也开启了聚合，继续递归收集
            if (Boolean.TRUE.equals(subAlbum.getAggregateSubAlbums())) {
                collectSubAlbumPaths(subAlbum.getPath(), paths);
            }
        }
    }

    /**
     * 获取相册聚合的所有相册ID列表
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
        List<Album> subAlbums = albumRepository.findDirectSubAlbums(parentPath);
        for (Album subAlbum : subAlbums) {
            albumIds.add(subAlbum.getId());
            // 如果子相册也开启了聚合，继续递归
            if (Boolean.TRUE.equals(subAlbum.getAggregateSubAlbums())) {
                addSubAlbumIds(subAlbum.getPath(), albumIds);
            }
        }
    }

    /**
     * 验证排序方式是否有效
     */
    private boolean isValidSortOrder(String sortOrder) {
        return SystemConfigService.SORT_BY_TAKEN_AT_DESC.equals(sortOrder) ||
               SystemConfigService.SORT_BY_TAKEN_AT_ASC.equals(sortOrder) ||
               SystemConfigService.SORT_BY_FILENAME_DESC.equals(sortOrder) ||
               SystemConfigService.SORT_BY_FILENAME_ASC.equals(sortOrder) ||
               SystemConfigService.SORT_BY_CREATED_AT_DESC.equals(sortOrder) ||
               SystemConfigService.SORT_BY_CREATED_AT_ASC.equals(sortOrder);
    }

}

