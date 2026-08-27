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
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
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
    @Lazy
    private final PhotoService photoService;
    private final UserPathService userPathService;

    /**
     * 获取相册总数（有照片的相册或开启了聚合的相册，已过滤被聚合的相册）
     * @param includeHidden 是否包含隐藏相册（true=后台管理，显示所有；false=前台，过滤隐藏）
     */
    @Transactional(readOnly = true)
    public long getAlbumsCount(String category, boolean includeHidden) {
        return getAlbumsCount(category, includeHidden, null);
    }

    @Transactional(readOnly = true)
    public long getAlbumsCount(String category, boolean includeHidden, Long userId) {
        List<Album> allAlbums = loadAlbumsForListing(category, userId);

        allAlbums = filterAlbumsByUser(allAlbums, userId);

        // 过滤掉被聚合的相册
        List<Album> filteredAlbums = filterAggregatedAlbums(allAlbums, includeHidden);
        return filteredAlbums.size();
    }

    /**
     * 获取相册总数（有照片的相册或开启了聚合的相册，已过滤被聚合的相册）
     * 默认不包含隐藏相册
     */
    @Transactional(readOnly = true)
    public long getAlbumsCount(String category) {
        return getAlbumsCount(category, false);
    }

    /**
     * 获取所有相册并生成封面（只返回有照片的相册或开启了聚合的相册）
     * 注意：Page对象不缓存，因为反序列化会有问题
     */
    @Transactional(readOnly = true)
    public Page<AlbumDTO> getAllAlbumsWithCover(Pageable pageable, String category, String sort) {
        return getAllAlbumsWithCover(pageable, category, sort, false);
    }

    /**
     * 获取所有相册并生成封面（只返回有照片的相册或开启了聚合的相册）
     * @param includeHidden 是否包含隐藏相册（true=后台管理，显示所有；false=前台，过滤隐藏）
     */
    @Transactional(readOnly = true)
    public Page<AlbumDTO> getAllAlbumsWithCover(Pageable pageable, String category, String sort, boolean includeHidden) {
        return getAllAlbumsWithCover(pageable, category, sort, includeHidden, null);
    }

    @Transactional(readOnly = true)
    public Page<AlbumDTO> getAllAlbumsWithCover(Pageable pageable, String category, String sort, boolean includeHidden, Long userId) {
        log.debug("获取相册列表 - 排序: {}, 分类: {}, includeHidden: {}", sort, category, includeHidden);

        // 聚合相册需要按路径排除子相册，因此先完整读取候选集，再执行筛选、排序和分页。
        // 不能只读取固定的前 500 条，否则总数大于 500 时后续页面会永久为空。
        List<Album> filteredAlbums = filterAggregatedAlbums(
            filterAlbumsByUser(loadAlbumsForListing(category, userId), userId), includeHidden);
        filteredAlbums = sortAlbums(filteredAlbums, sort);

        // 获取总数
        long totalElements = getAlbumsCount(category, includeHidden, userId);

        // 计算实际的分页范围
        int page = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int start = page * pageSize;
        int end = Math.min(start + pageSize, filteredAlbums.size());

        // 如果开始位置超出范围，返回空页
        if (start >= filteredAlbums.size()) {
            return new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.emptyList(),
                pageable,
                totalElements
            );
        }

        // 提取当前页的数据
        List<Album> pageContent = filteredAlbums.subList(start, end);

        return new org.springframework.data.domain.PageImpl<>(
            pageContent.stream().map(this::convertToDTO).collect(java.util.stream.Collectors.toList()),
            pageable,
            totalElements
        );
    }

    /**
     * 筛选相册（只返回有照片的相册或开启了聚合的相册）
     */
    @Transactional(readOnly = true)
    public Page<AlbumDTO> filterAlbums(com.photoexhibition.dto.FilterRequest request, Pageable pageable) {
        // 先查询足够多的数据
        int fetchSize = 500;
        Pageable fetchPageable = PageRequest.of(0, fetchSize, pageable.getSort());

        Page<Album> albums;
        if (request.getTagIds() != null && !request.getTagIds().isEmpty()) {
            // 查询有照片且带标签的相册，或开启了聚合功能的相册
            albums = albumRepository.findByTagIdsWithPhotosOrAggregation(request.getTagIds(), fetchPageable);
        } else {
            albums = albumRepository.findAlbumsWithPhotosOrAggregation(fetchPageable);
        }

        // 过滤掉被聚合的相册
        List<Album> filteredAlbums = filterAggregatedAlbums(albums.getContent());

        // 获取总数
        long totalElements = filteredAlbums.size();

        // 计算实际的分页范围
        int page = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int start = page * pageSize;
        int end = Math.min(start + pageSize, filteredAlbums.size());

        // 如果开始位置超出范围，返回空页
        if (start >= filteredAlbums.size()) {
            return new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.emptyList(),
                pageable,
                totalElements
            );
        }

        // 提取当前页的数据
        List<Album> pageContent = filteredAlbums.subList(start, end);

        return new org.springframework.data.domain.PageImpl<>(
            pageContent.stream().map(this::convertToDTO).collect(java.util.stream.Collectors.toList()),
            pageable,
            totalElements
        );
    }

    /**
     * 获取相册详情
     * 注意：暂时不缓存，避免反序列化问题
     */
    public AlbumDTO getAlbumById(Long id) {
        return getAlbumById(id, null);
    }

    public AlbumDTO getAlbumById(Long id, Long userId) {
        Album album = albumRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        validateAlbumOwnership(album, userId);
        return convertToDTO(album);
    }

    /**
     * 根据名称模糊搜索相册（用于短链接）
     * 返回匹配的第一个有照片的相册
     */
    public AlbumDTO searchAlbumByName(String name) {
        return searchAlbumByName(name, null);
    }

    public AlbumDTO searchAlbumByName(String name, Long userId) {
        List<Album> albums = filterAlbumsByUser(albumRepository.searchByName(name), userId);
        // 优先返回有照片的相册
        for (Album album : albums) {
            if (album.getPhotoCount() != null && album.getPhotoCount() > 0) {
                return convertToDTO(album);
            }
        }
        // 如果没有有照片的，返回第一个匹配的
        if (!albums.isEmpty()) {
            return convertToDTO(albums.get(0));
        }
        return null;
    }

    /**
     * 根据名称模糊搜索相册列表（用于全局搜索）
     * 返回匹配的相册列表：
     * 1. 名称匹配的相册（优先有照片的）
     * 2. 路径匹配但名称不匹配的相册的子相册（如果有照片）
     */
    public List<Album> searchAlbumsByName(String name) {
        return searchAlbumsByName(name, null);
    }

    public List<Album> searchAlbumsByName(String name, Long userId) {
        List<Album> nameMatches = filterAlbumsByUser(albumRepository.searchByName(name), userId);

        // 过滤掉隐藏的相册
        nameMatches = nameMatches.stream()
            .filter(album -> !Boolean.TRUE.equals(album.getIsHidden()))
            .collect(Collectors.toList());

        // 1. 先过滤有照片的名称匹配相册
        List<Album> withPhotos = nameMatches.stream()
            .filter(album -> album.getPhotoCount() != null && album.getPhotoCount() > 0)
            .collect(Collectors.toList());
        if (!withPhotos.isEmpty()) {
            return withPhotos;
        }
        
        // 2. 如果没有有照片的名称匹配相册，查找路径匹配但名称不匹配的相册
        List<Album> pathMatches = filterAlbumsByUser(albumRepository.searchByPath(name), userId);
        if (pathMatches.isEmpty()) {
            return nameMatches;
        }
        
        // 3. 对每个路径匹配，获取其有照片的子相册
        Set<Long> resultIds = new java.util.HashSet<>();
        List<Album> result = new java.util.ArrayList<>();
        
        for (Album pathMatch : pathMatches) {
            // 跳过已有结果的相册
            if (resultIds.contains(pathMatch.getId())) continue;
            
            // 如果路径匹配的相册本身有照片，加入结果
            if (pathMatch.getPhotoCount() != null && pathMatch.getPhotoCount() > 0) {
                result.add(pathMatch);
                resultIds.add(pathMatch.getId());
                continue;
            }
            
            // 否则查找该相册路径下的所有有照片的子相册
            String pathPrefix = pathMatch.getPath();
            if (pathPrefix != null) {
                // 标准化路径
                pathPrefix = pathPrefix.replace("\\", "/");
                if (!pathPrefix.endsWith("/")) {
                    pathPrefix = pathPrefix + "/";
                }
                
                // 查找以该路径为前缀的有照片相册
                List<Album> subAlbums = albumRepository.findByPathPrefixWithPhotos(
                    pathPrefix, 0);
                for (Album sub : subAlbums) {
                    if (!resultIds.contains(sub.getId()) && 
                        sub.getPhotoCount() != null && sub.getPhotoCount() > 0) {
                        result.add(sub);
                        resultIds.add(sub.getId());
                    }
                }
            }
        }
        
        // 4. 如果还是没有，返回名称匹配的空相册
        if (result.isEmpty()) {
            return nameMatches;
        }
        
        return result;
    }

    /**
     * 更新相册基础信息（名称/描述）
     */
    public AlbumDTO updateAlbum(Long id, AlbumDTO dto) {
        return updateAlbum(id, dto, null);
    }

    public AlbumDTO updateAlbum(Long id, AlbumDTO dto, Long userId) {
        Album album = albumRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        validateAlbumOwnership(album, userId);
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
     * 重命名相册（同时重命名文件夹和更新数据库记录）
     */
    @Transactional
    public Map<String, Object> renameAlbum(Long id, String newName) {
        return renameAlbum(id, newName, null);
    }

    @Transactional
    public Map<String, Object> renameAlbum(Long id, String newName, Long userId) {
        Map<String, Object> result = new java.util.HashMap<>();

        Album album = albumRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        validateAlbumOwnership(album, userId);

        String oldPath = album.getPath();
        Path sourcePath = resolveStoredDirectoryPath(oldPath);
        Path parentPath = sourcePath.getParent();
        Path targetPath = parentPath.resolve(newName);

        // 验证
        if (!Files.isDirectory(sourcePath)) {
            result.put("success", false);
            result.put("message", "相册文件夹不存在: " + convertAlbumPath(oldPath));
            return result;
        }
        if (Files.exists(targetPath)) {
            result.put("success", false);
            result.put("message", "目标名称已存在: " + newName);
            return result;
        }

        try {
            String oldPrefix = oldPath.replace("\\", "/");
            String newPrefix = targetPath.toString().replace("\\", "/");
            Path oldRoot = sourcePath.toAbsolutePath().normalize();
            Path newRoot = targetPath.toAbsolutePath().normalize();

            // 1. 重命名文件系统中的文件夹
            Files.move(sourcePath, targetPath);

            // 2. 更新数据库中所有相关路径
            // 2.1 更新当前相册的路径
            album.setPath(toStoredDirectoryPath(targetPath, album.getUserId()));
            album.setPathHash(computeSha256(album.getPath()));
            album.setName(newName);

            // 2.2 更新所有子相册的路径（路径以旧路径开头的）
            List<Album> allAlbums = listScopedAlbums(userId);
            List<Album> subAlbumsToUpdate = new java.util.ArrayList<>();
            for (Album a : allAlbums) {
                if (a.getPath() != null) {
                    // 使用标准化后的路径进行比较
                    String normalizedDbPath = a.getPath().replace("\\", "/");
                    if (normalizedDbPath.startsWith(oldPrefix)) {
                        a.setPath(rewriteStoredPathForDirectoryMove(a.getPath(), oldRoot, newRoot, a.getUserId()));
                        a.setPathHash(computeSha256(a.getPath()));
                        subAlbumsToUpdate.add(a);
                    }
                }
            }
            if (!subAlbumsToUpdate.isEmpty()) {
                albumRepository.saveAll(subAlbumsToUpdate);
                log.info("重命名相册: 更新了 {} 个子相册路径", subAlbumsToUpdate.size());
            }

            // 2.3 更新当前相册下所有照片的路径
            // 使用 albumId 查询更可靠
            List<Photo> photos = loadAllPhotosByAlbumId(id);
            for (Photo p : photos) {
                p.setOriginalPath(rewriteStoredPathForDirectoryMove(p.getOriginalPath(), oldRoot, newRoot, p.getUserId()));
                p.setPathHash(computeSha256(p.getOriginalPath()));
                if (p.getThumbnailPath() != null) {
                    p.setThumbnailPath(rewriteStoredPathForDirectoryMove(p.getThumbnailPath(), oldRoot, newRoot, p.getUserId()));
                }
                if (p.getWebpPath() != null) {
                    p.setWebpPath(rewriteStoredPathForDirectoryMove(p.getWebpPath(), oldRoot, newRoot, p.getUserId()));
                }
                if (p.getSmallThumbPath() != null) {
                    p.setSmallThumbPath(rewriteStoredPathForDirectoryMove(p.getSmallThumbPath(), oldRoot, newRoot, p.getUserId()));
                }
                if (p.getMediumThumbPath() != null) {
                    p.setMediumThumbPath(rewriteStoredPathForDirectoryMove(p.getMediumThumbPath(), oldRoot, newRoot, p.getUserId()));
                }
                if (p.getLargeThumbPath() != null) {
                    p.setLargeThumbPath(rewriteStoredPathForDirectoryMove(p.getLargeThumbPath(), oldRoot, newRoot, p.getUserId()));
                }
                if (p.getBackgroundRemovedPath() != null) {
                    p.setBackgroundRemovedPath(rewriteStoredPathForDirectoryMove(p.getBackgroundRemovedPath(), oldRoot, newRoot, p.getUserId()));
                }
            }
            if (!photos.isEmpty()) {
                photoRepository.saveAll(photos);
                log.info("重命名相册: 更新了 {} 张照片路径", photos.size());
            }

            // 2.4 保存当前相册
            Album savedAlbum = albumRepository.save(album);

            // 重新获取完整的DTO（包括重新计算的displayTitle）
            AlbumDTO updatedDto = convertToDTO(savedAlbum);
            result.put("success", true);
            result.put("message", "相册重命名成功");
            result.put("album", updatedDto);

        } catch (Exception e) {
            log.error("重命名相册失败", e);
            result.put("success", false);
            result.put("message", "重命名失败: " + userPathService.sanitizeVisibleText(e.getMessage() == null ? "系统异常" : e.getMessage()));
        }

        return result;
    }

    /**
     * 替换路径前缀（兼容不同路径分隔符）
     */
    private String replacePathPrefix(String path, String oldPrefix, String newPrefix) {
        if (path == null) return null;

        // 标准化路径分隔符（统一用 / 比较）
        String normalizedPath = path.replace("\\", "/");
        String normalizedOld = oldPrefix.replace("\\", "/");
        String normalizedNew = newPrefix.replace("\\", "/");

        if (normalizedPath.startsWith(normalizedOld)) {
            // 保持原始分隔符风格
            String suffix = path.substring(oldPrefix.length());
            return newPrefix + suffix;
        }
        return path;
    }

    private String rewriteStoredPathForDirectoryMove(String currentPath, Path oldRoot, Path newRoot, Long userId) {
        if (currentPath == null || currentPath.isBlank() || oldRoot == null || newRoot == null) {
            return currentPath;
        }
        try {
            Path currentAbsolute = userPathService.resolveStoredPhotoPath(currentPath);
            if (!currentAbsolute.startsWith(oldRoot)) {
                return currentPath;
            }
            Path relative = oldRoot.relativize(currentAbsolute);
            Path targetAbsolute = newRoot.resolve(relative).normalize();
            return userPathService.tryBuildStoragePathReference(targetAbsolute.toString(), userId).orElse(targetAbsolute.toString());
        } catch (Exception e) {
            return currentPath;
        }
    }

    private Path resolveStoredDirectoryPath(String path) {
        if (path == null || path.isBlank()) {
            throw new RuntimeException("相册路径不存在");
        }
        try {
            return userPathService.resolveStoredPhotoPath(path);
        } catch (Exception e) {
            return Paths.get(path).toAbsolutePath().normalize();
        }
    }

    private String toStoredDirectoryPath(Path path, Long userId) {
        if (path == null) {
            return null;
        }
        String absolute = path.toAbsolutePath().normalize().toString();
        return userPathService.tryBuildStoragePathReference(absolute, userId).orElse(absolute);
    }

    /**
     * 计算SHA256哈希
     */
    private String computeSha256(String input) {
        if (input == null) return null;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 删除相册（会级联删除照片记录）
     */
    public void deleteAlbum(Long id) {
        deleteAlbum(id, null);
    }

    public void deleteAlbum(Long id, Long userId) {
        Album album = albumRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        validateAlbumOwnership(album, userId);
        if (!albumRepository.existsById(id)) {
            throw new RuntimeException("相册不存在");
        }
        // 照片表album_id外键未声明级联，这里直接删相册记录，其它清理由DB外键/应用控制
        // 若需同时删除照片，请在PhotoRepository中按albumId删除
        albumRepository.deleteById(id);
    }

    /**
     * 获取相册封面图片组合（左侧竖图+右侧上下两张横图）
     * 优先使用自定义封面，没有自定义封面时自动生成
     */
    public CoverImagesDTO getAlbumCoverImages(Long albumId) {
        CoverImagesDTO cover = new CoverImagesDTO();
        
        // 尝试获取自定义封面
        java.util.Optional<Album> albumOpt = albumRepository.findById(albumId);
        if (albumOpt.isPresent()) {
            Album album = albumOpt.get();
            String coverImageIdsStr = album.getCoverImageIds();
            if (coverImageIdsStr != null && !coverImageIdsStr.isEmpty()) {
                try {
                    java.util.List<Long> coverImageIds = objectMapper.readValue(
                        coverImageIdsStr,
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, Long.class)
                    );
                    
                    // 过滤掉null和无效的ID
                    coverImageIds = coverImageIds.stream()
                        .filter(id -> id != null && id > 0)
                        .collect(java.util.stream.Collectors.toList());
                    
                    if (!coverImageIds.isEmpty()) {
                        // 加载自定义封面图片
                        for (int i = 0; i < Math.min(coverImageIds.size(), 4); i++) {
                            Long photoId = coverImageIds.get(i);
                            java.util.Optional<Photo> photoOpt = photoRepository.findById(photoId);
                            if (photoOpt.isPresent()) {
                                PhotoDTO photoDTO = convertPhotoToDTO(photoOpt.get());
                                switch (i) {
                                    case 0:
                                        cover.setCover1(photoDTO);
                                        break;
                                    case 1:
                                        cover.setCover2(photoDTO);
                                        break;
                                    case 2:
                                        cover.setCover3(photoDTO);
                                        break;
                                    case 3:
                                        cover.setCover4(photoDTO);
                                        break;
                                }
                            }
                        }
                        
                        // 如果至少有一个自定义封面，返回自定义封面
                        if (cover.getCover1() != null || cover.getCover2() != null || 
                            cover.getCover3() != null || cover.getCover4() != null) {
                            return cover;
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析自定义封面ID列表失败: {}", e.getMessage());
                }
            }
        }
        
        // 没有自定义封面，使用自动生成逻辑
        return generateAutoCoverImages(albumId);
    }
    
    /**
     * 自动生成相册封面图片组合（排除隐藏的照片）
     */
    private CoverImagesDTO generateAutoCoverImages(Long albumId) {
        log.debug("generateAutoCoverImages - albumId: {}", albumId);

        // 检查相册是否开启了聚合
        List<Long> albumIds = getAggregatedAlbumIds(albumId);
        log.debug("generateAutoCoverImages - 聚合相册ID列表: {}, 大小: {}", albumIds, albumIds.size());

        List<Photo> photos;
        if (albumIds.size() == 1) {
            // 非聚合相册：从单个相册取前 10 张未隐藏的照片（按相册排序）
            org.springframework.data.domain.Sort sort = photoService.getPhotoSort(albumId);
            photos = photoRepository.findByAlbumIdAndIsHiddenFalse(albumId,
                org.springframework.data.domain.PageRequest.of(0, 10, sort))
                .getContent();
        } else {
            // 聚合相册：获取所有照片并按瀑布流排序，然后取前几张（排除隐藏）
            photos = new java.util.ArrayList<>();
            for (Long id : albumIds) {
                List<Photo> albumPhotos = photoRepository.findByAlbumIdAndIsHiddenFalse(id,
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

        // 赋值封面（使用新的字段名）
        if (verticalPhoto != null) {
            cover.setCover1(convertPhotoToDTO(verticalPhoto));
        }
        if (horizontalPhotos.size() > 0) {
            cover.setCover2(convertPhotoToDTO(horizontalPhotos.get(0)));
        }
        if (horizontalPhotos.size() > 1) {
            cover.setCover3(convertPhotoToDTO(horizontalPhotos.get(1)));
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
        dto.setPath(convertAlbumPath(album.getPath()));
        dto.setCoverImageId(album.getCoverImageId());

        // 计算相对路径（去掉 base-path）
        dto.setRelativePath(calculateRelativePath(album.getPath(), album.getUserId()));

        // 解析自定义封面图片ID列表
        if (album.getCoverImageIds() != null && !album.getCoverImageIds().isEmpty()) {
            try {
                java.util.List<Long> coverImageIds = objectMapper.readValue(
                    album.getCoverImageIds(),
                    objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, Long.class)
                );
                dto.setCoverImageIds(coverImageIds);
            } catch (Exception e) {
                log.warn("解析封面图片ID列表失败: {}", e.getMessage());
            }
        }
        
        dto.setDescription(album.getDescription());
        dto.setPhotoCount(album.getPhotoCount());
        dto.setAggregateSubAlbums(album.getAggregateSubAlbums());
        dto.setDownloadAllowed(album.getDownloadAllowed());
        dto.setIsHidden(album.getIsHidden());
        dto.setPhotoSortOrder(album.getPhotoSortOrder());

        boolean hasSubs = false;
        try {
            hasSubs = !findDirectChildAlbums(album.getPath(), album.getUserId()).isEmpty();
        } catch (Exception e) {
            log.debug("检查相册子文件夹失败: {} - {}", album.getName(), e.getMessage());
        }
        dto.setHasSubAlbums(hasSubs);

        // 检查是否是顶级相册（不能聚合到上一级）
        // 顶级相册是指在base-path/分类/下的相册
        int depth = 0;

        try {
            Path relativePath = resolveAlbumLogicalRelativePath(album.getPath(), album.getUserId());
            depth = relativePath.getNameCount();
        } catch (Exception e) {
            log.debug("路径计算失败: {}", e.getMessage());
        }

        boolean isTop = (depth == 2);

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
            takenAt = findEarliestTakenAtFromSubAlbums(album.getPath(), album.getUserId());
        }

        takenAt.ifPresent(dto::setTakenAt);

        // 设置氛围颜色
        dto.setDarkBgColor(album.getDarkBgColor());
        dto.setLightBgColor(album.getLightBgColor());
        dto.setDarkAccentColor(album.getDarkAccentColor());
        dto.setLightAccentColor(album.getLightAccentColor());

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
        return getCategories(null);
    }

    public List<String> getCategories(Long userId) {
        List<Album> albums = listScopedAlbums(userId);
        List<String> categories = new ArrayList<>();
        for (Album a : albums) {
            String c = extractCategory(a);
            if (c != null && !c.isEmpty() && !categories.contains(c)) {
                categories.add(c);
            }
        }
        return categories;
    }

    private List<Album> filterAlbumsByUser(List<Album> albums, Long userId) {
        if (userId == null) {
            return albums;
        }
        return albums.stream()
            .filter(album -> Objects.equals(album.getUserId(), userId))
            .collect(Collectors.toList());
    }

    private void validateAlbumOwnership(Album album, Long userId) {
        if (userId != null && !Objects.equals(album.getUserId(), userId)) {
            throw new RuntimeException("相册不存在");
        }
    }

    public Long resolveScopedUserId(UserAccount user) {
        if (user == null || user.getRole() == UserRole.SUPER_ADMIN) {
            return null;
        }
        return user.getId();
    }

    /**
     * 转换Photo为DTO
     */
    /**
     * 将快门秒数转换为分数形式显示
     */
    private String formatShutterSpeedFromSeconds(Double seconds) {
        if (seconds == null || seconds == 0) return "0";
        if (seconds >= 1) return String.valueOf(Math.round(seconds));  // 超出1秒显示整数
        if (seconds >= 0.1) return String.format("%.1f", seconds);  // 0.1秒到1秒之间显示小数
        // 小于一秒显示倒数，分母取整
        int denominator = (int) Math.round(1.0 / seconds);
        return "1/" + denominator;
    }

    private PhotoDTO convertPhotoToDTO(Photo photo) {
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

        // 相册封面不需要完整EXIF数据，跳过exifData字段以减少响应体积
        // exifData 仅在照片详情接口中返回

        dto.setCameraMake(photo.getCameraMake());
        dto.setCameraModel(photo.getCameraModel());
        dto.setLensModel(photo.getLensModel());
        dto.setFocalLength(photo.getFocalLength());
        dto.setAperture(photo.getAperture());

        // 优先使用原始快门字符串，如果没有则从秒数转换为分数形式
        String shutterSpeedDisplay = photo.getShutterSpeed();
        if (shutterSpeedDisplay == null && photo.getShutterSpeedSeconds() != null) {
            shutterSpeedDisplay = formatShutterSpeedFromSeconds(photo.getShutterSpeedSeconds());
        }
        dto.setShutterSpeed(shutterSpeedDisplay);

        dto.setIso(photo.getIso());
        dto.setTakenAt(photo.getTakenAt());
        dto.setQualityScore(photo.getQualityScore());
        dto.setViewCount(photo.getViewCount());
        dto.setIsFeatured(photo.getIsFeatured());
        dto.setIsHidden(photo.getIsHidden());
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
        String displayPath = userPathService.toDisplayPath(absolutePath, true);
        if (!absolutePath.equals(displayPath)) {
            return displayPath;
        }
        return sanitizeLeafPath(absolutePath);
    }

    private String convertAlbumPath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        String displayPath = userPathService.toDisplayPath(path, true);
        if (!path.equals(displayPath)) {
            return displayPath;
        }
        return sanitizeLeafPath(path);
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

    /**
     * 生成展示用标题：去掉首段日期前缀，并将层级目录拼接
     */
    private String buildDisplayTitle(Album album) {
        try {
            String albumPath = album.getPath();
            if (albumPath == null || albumPath.isEmpty()) {
                return stripDatePrefix(album.getName());
            }
            Path relative = resolveAlbumLogicalRelativePath(albumPath, album.getUserId());

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
            Path relative = resolveAlbumLogicalRelativePath(albumPath, album.getUserId());
            if (relative.getNameCount() > 0) {
                return relative.getName(0).toString();
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * 从子相册中找到最早的拍摄时间（用于聚合相册）
     */
    private java.util.Optional<java.time.LocalDateTime> findEarliestTakenAtFromSubAlbums(String parentPath) {
        return findEarliestTakenAtFromSubAlbums(parentPath, null);
    }

    private java.util.Optional<java.time.LocalDateTime> findEarliestTakenAtFromSubAlbums(String parentPath, Long userId) {
        try {
            java.util.List<Album> subAlbums = findDirectChildAlbums(parentPath, userId);
            log.debug("findEarliestTakenAtFromSubAlbums - parentPath原始: [{}], 找到 {} 个直接子相册", parentPath, subAlbums.size());
            if (subAlbums.isEmpty()) {
                return java.util.Optional.empty();
            }

            java.time.LocalDateTime earliest = null;
            for (Album subAlbum : subAlbums) {
                log.debug("findEarliestTakenAtFromSubAlbums - 子相册: {}, path: {}", subAlbum.getName(), convertAlbumPath(subAlbum.getPath()));
                // 递归查找子相册的最早时间
                java.util.Optional<java.time.LocalDateTime> subTakenAt = findEarliestTakenAtFromAlbum(subAlbum);
                if (subTakenAt.isPresent()) {
                    log.debug("findEarliestTakenAtFromSubAlbums - 子相册 {} 的最早时间: {}", subAlbum.getName(), subTakenAt.get());
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
            return findEarliestTakenAtFromSubAlbums(album.getPath(), album.getUserId());
        }

        return java.util.Optional.empty();
    }

    private List<Album> findDirectChildAlbums(String parentPath, Long userId) {
        if (parentPath == null || parentPath.isBlank()) {
            return List.of();
        }
        Path parentRelative = resolveAlbumLogicalRelativePath(parentPath, userId);
        int expectedDepth = parentRelative.getNameCount() + 1;

        List<Album> candidates = findCandidateChildAlbums(parentPath, userId);
        return candidates.stream()
            .filter(album -> album != null && album.getPath() != null && !Objects.equals(album.getPath(), parentPath))
            .filter(album -> {
                try {
                    Path candidateRelative = resolveAlbumLogicalRelativePath(album.getPath(), album.getUserId());
                    return candidateRelative.getNameCount() == expectedDepth && candidateRelative.startsWith(parentRelative);
                } catch (Exception e) {
                    log.debug("过滤直接子相册失败: path={}, err={}", album.getPath(), e.getMessage());
                    return false;
                }
            })
            .collect(Collectors.toList());
    }

    private List<Album> findCandidateChildAlbums(String parentPath, Long userId) {
        String normalizedPath = parentPath.replace("\\", "/");
        if (!normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath + "/";
        }
        if (userId != null) {
            return albumRepository.findByUserIdAndPathStartingWithNormalized(userId, normalizedPath);
        }
        String parentPathLike = normalizedPath + "%";
        String parentPathLikeWithSlash = normalizedPath.startsWith("/")
            ? normalizedPath + "%"
            : "/" + normalizedPath + "%";
        return albumRepository.findDirectSubAlbumsNormalized(normalizedPath, parentPathLike, parentPathLikeWithSlash);
    }

    /**
     * 规范化路径，处理前导斜杠的Windows路径（如 /D:/xxx -> D:/xxx）
     */
    private Path normalizePath(String path) {
        if (path == null) {
            return null;
        }
        // 处理前导斜杠的Windows路径，如 /D:/xxx -> D:/xxx
        String normalized = path;
        if (path.matches("^/[A-Za-z]:/.*")) {
            normalized = path.substring(1);
        }
        return Paths.get(normalized).normalize();
    }

    private Path resolveBasePath() {
        return userPathService.resolvePhotoBasePath();
    }

    /**
     * 计算相册的相对路径（去掉 base-path）
     */
    private String calculateRelativePath(String albumPath, Long userId) {
        if (albumPath == null || albumPath.isEmpty()) {
            return "";
        }

        try {
            Path relative = resolveAlbumLogicalRelativePath(albumPath, userId);
            return relative.toString().replace("\\", "/");
        } catch (Exception e) {
            log.debug("计算相对路径失败: {} - {}", albumPath, e.getMessage());
            return albumPath;
        }
    }

    private Path resolveAlbumLogicalRelativePath(String albumPath, Long userId) {
        String tenantRelative = userPathService.extractTenantRelativePhotoPath(albumPath);
        if (tenantRelative != null && !tenantRelative.isBlank()) {
            return Paths.get(tenantRelative).normalize();
        }

        Path basePathResolved = resolveBasePath();

        String normalizedAlbumPath = albumPath;
        if (albumPath.matches("^/[A-Za-z]:/.*")) {
            normalizedAlbumPath = albumPath.substring(1);
        }

        Path albumRealPath = Paths.get(normalizedAlbumPath).normalize();
        if (albumRealPath.startsWith(basePathResolved)) {
            return stripUserRootSegment(basePathResolved.relativize(albumRealPath), userId);
        }

        Path fallback = extractRemoteRelativePath(albumRealPath, userId);
        if (fallback != null) {
            return fallback;
        }
        return albumRealPath.getFileName() != null ? albumRealPath.getFileName() : Paths.get(stripUserLeadingSegment(albumPath, userId));
    }

    private Path extractRemoteRelativePath(Path albumPath, Long userId) {
        if (albumPath == null || albumPath.getNameCount() == 0) {
            return null;
        }
        String userSegment = userId != null ? String.valueOf(userId) : null;
        for (int i = 0; i < albumPath.getNameCount(); i++) {
            String segment = albumPath.getName(i).toString();
            if (!segment.matches("\\d+")) {
                continue;
            }
            if (userSegment != null && !userSegment.equals(segment)) {
                continue;
            }
            if (i + 1 >= albumPath.getNameCount()) {
                return Paths.get("");
            }
            return albumPath.subpath(i + 1, albumPath.getNameCount());
        }
        return null;
    }

    private Path stripUserRootSegment(Path relativePath, Long userId) {
        if (relativePath == null || relativePath.getNameCount() == 0) {
            return relativePath;
        }
        String firstSegment = relativePath.getName(0).toString();
        if (firstSegment.matches("\\d+") && (userId == null || firstSegment.equals(String.valueOf(userId)))) {
            if (relativePath.getNameCount() == 1) {
                return Paths.get("");
            }
            return relativePath.subpath(1, relativePath.getNameCount());
        }
        return relativePath;
    }

    private String stripUserLeadingSegment(String relativePath, Long userId) {
        if (relativePath == null || relativePath.isEmpty()) {
            return "";
        }
        String normalized = relativePath.replace("\\", "/");
        String[] parts = normalized.split("/");
        if (parts.length == 0) {
            return normalized;
        }
        if (parts[0].matches("\\d+") && (userId == null || parts[0].equals(String.valueOf(userId)))) {
            if (parts.length == 1) {
                return "";
            }
            return String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length));
        }
        return normalized;
    }

    /**
     * 为相册添加标签
     */
    @Transactional
    public AlbumDTO addTagToAlbum(Long albumId, Long tagId) {
        return addTagToAlbum(albumId, tagId, null);
    }

    @Transactional
    public AlbumDTO addTagToAlbum(Long albumId, Long tagId, Long userId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        validateAlbumOwnership(album, userId);
        Tag tag = (userId == null
            ? tagRepository.findById(tagId)
            : tagRepository.findByIdAndUserId(tagId, userId))
            .orElseThrow(() -> new RuntimeException("标签不存在"));
        if (userId != null && !Objects.equals(tag.getUserId(), album.getUserId())) {
            throw new RuntimeException("标签不存在");
        }
        
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
        return removeTagFromAlbum(albumId, tagId, null);
    }

    @Transactional
    public AlbumDTO removeTagFromAlbum(Long albumId, Long tagId, Long userId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        validateAlbumOwnership(album, userId);

        if (album.getTags() != null) {
            album.getTags().removeIf(t -> t.getId().equals(tagId) && (userId == null || Objects.equals(t.getUserId(), userId)));
            albumRepository.save(album);
        }

        return convertToDTO(album);
    }

    /**
     * 设置相册聚合下级相册
     */
    @Transactional
    public AlbumDTO setAggregateSubAlbums(Long albumId, Boolean aggregate) {
        return setAggregateSubAlbums(albumId, aggregate, null);
    }

    @Transactional
    public AlbumDTO setAggregateSubAlbums(Long albumId, Boolean aggregate, Long userId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        validateAlbumOwnership(album, userId);

        album.setAggregateSubAlbums(aggregate != null ? aggregate : false);

        // 如果开启聚合，且当前相册没有albumNameDate，从子相册获取最早的albumNameDate
        if (Boolean.TRUE.equals(aggregate) && album.getAlbumNameDate() == null) {
            LocalDateTime earliestDateFromSubAlbums = findEarliestAlbumNameDateFromSubAlbums(album.getPath());
            if (earliestDateFromSubAlbums != null) {
                album.setAlbumNameDate(earliestDateFromSubAlbums);
                log.debug("setAggregateSubAlbums - 从子相册设置albumNameDate: {}", earliestDateFromSubAlbums);
            }
        }

        Album saved = albumRepository.save(album);

        return convertToDTO(saved);
    }

    /**
     * 从子相册中找到最早的albumNameDate
     */
    private LocalDateTime findEarliestAlbumNameDateFromSubAlbums(String parentPath) {
        // 标准化路径
        String normalizedPath = parentPath.replace("\\", "/");
        // 处理前导斜杠的Windows路径
        if (normalizedPath.matches("^/[A-Za-z]:/.*")) {
            normalizedPath = normalizedPath.substring(1);
        }
        if (!normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath + "/";
        }

        // 查询模式
        String parentPathLike = normalizedPath + "%";
        String parentPathLikeWithSlash = normalizedPath.startsWith("/") 
            ? normalizedPath + "%" 
            : "/" + normalizedPath + "%";

        List<Album> subAlbums = albumRepository.findDirectSubAlbumsNormalized(normalizedPath, parentPathLike, parentPathLikeWithSlash);

        // 计算当前层级
        String[] currentParts = normalizedPath.split("/");
        int currentLevel = currentParts.length;

        // 过滤直接子相册并找到最早的albumNameDate
        LocalDateTime earliestDate = null;
        for (Album sub : subAlbums) {
            String subPathNormalized = sub.getPath().replace("\\", "/");
            if (subPathNormalized.matches("^/[A-Za-z]:/.*")) {
                subPathNormalized = subPathNormalized.substring(1);
            }
            String[] subParts = subPathNormalized.split("/");

            // 只检查直接子相册
            if (subParts.length == currentLevel + 1) {
                LocalDateTime subDate = sub.getAlbumNameDate();
                if (subDate != null) {
                    if (earliestDate == null || subDate.isBefore(earliestDate)) {
                        earliestDate = subDate;
                    }
                }
            }
        }

        return earliestDate;
    }

    /**
     * 获取相册的直接子相册（不经过聚合过滤）
     */
    @Transactional(readOnly = true)
    public List<AlbumDTO> getSubAlbums(Long albumId) {
        return getSubAlbums(albumId, null);
    }

    @Transactional(readOnly = true)
    public List<AlbumDTO> getSubAlbums(Long albumId, Long userId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        validateAlbumOwnership(album, userId);

        // 标准化路径：统一使用 / 分隔符
        String pathPrefix = album.getPath().replace("\\", "/");
        // 处理前导斜杠的Windows路径，如 /D:/xxx -> D:/xxx
        if (pathPrefix.matches("^/[A-Za-z]:/.*")) {
            pathPrefix = pathPrefix.substring(1);
        }
        // 确保路径以 / 结尾
        if (!pathPrefix.endsWith("/")) {
            pathPrefix = pathPrefix + "/";
        }

        log.debug("getSubAlbums - albumPath原始: [{}], pathPrefix: [{}]", convertAlbumPath(album.getPath()), convertAlbumPath(pathPrefix));

        // 计算 LIKE 模式 (pathPrefix 已经以 / 结尾，直接加 % 即可)
        // 模式1: 匹配不带前导斜杠的路径 (如 D:/xxx/yyy)
        // 模式2: 匹配带前导斜杠的路径 (如 /D:/xxx/yyy 或 /Users/xxx/yyy)
        String parentPathLike = pathPrefix + "%";
        String parentPathLikeWithSlash = pathPrefix.startsWith("/") 
            ? pathPrefix + "%" 
            : "/" + pathPrefix + "%";

        log.debug("getSubAlbums - 查询模式1: [{}], 查询模式2: [{}]", parentPathLike, parentPathLikeWithSlash);

        // 使用标准化路径查询（Repository 会自动处理前导斜杠的Windows路径）
        List<Album> subAlbums = albumRepository.findDirectSubAlbumsNormalized(pathPrefix, parentPathLike, parentPathLikeWithSlash);

        log.debug("getSubAlbums - 找到 {} 个候选子相册", subAlbums.size());
        for (Album sa : subAlbums) {
            log.debug("getSubAlbums - 候选子相册: [{}], path: [{}]", sa.getName(), convertAlbumPath(sa.getPath()));
        }

        // 过滤出直接子相册（层级 = 当前相册层级 + 1）
        String[] currentParts = pathPrefix.split("/");
        int currentLevel = currentParts.length;

        log.debug("getSubAlbums - 当前层级: {}, pathPrefix分割后: {}", currentLevel, java.util.Arrays.toString(currentParts));

        List<Album> directSubAlbums = subAlbums.stream()
            .filter(sub -> {
                String subPathNormalized = sub.getPath().replace("\\", "/");
                // 如果子路径以 / 开头（Windows风格），去掉前导斜杠后再计算层级
                if (subPathNormalized.matches("^/[A-Za-z]:/.*")) {
                    subPathNormalized = subPathNormalized.substring(1);
                }
                String[] subParts = subPathNormalized.split("/");
                log.debug("getSubAlbums - 子相册 [{}] 层级: {}, 分割: {}", sub.getName(), subParts.length, java.util.Arrays.toString(subParts));
                // 直接子相册的层级 = 当前相册层级 + 1
                return subParts.length == currentLevel + 1;
            })
            .collect(Collectors.toList());

        return filterAlbumsByUser(directSubAlbums, userId).stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * 设置相册照片排序方式
     */
    @Transactional
    public AlbumDTO setAlbumPhotoSortOrder(Long albumId, String sortOrder) {
        return setAlbumPhotoSortOrder(albumId, sortOrder, null);
    }

    @Transactional
    public AlbumDTO setAlbumPhotoSortOrder(Long albumId, String sortOrder, Long userId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在"));
        validateAlbumOwnership(album, userId);

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
     * 设置相册下载权限
     */
    @Transactional
    public AlbumDTO setAlbumDownloadAllowed(Long albumId, Boolean downloadAllowed) {
        return setAlbumDownloadAllowed(albumId, downloadAllowed, null);
    }

    @Transactional
    public AlbumDTO setAlbumDownloadAllowed(Long albumId, Boolean downloadAllowed, Long userId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在: " + albumId));
        validateAlbumOwnership(album, userId);

        album.setDownloadAllowed(downloadAllowed);
        Album saved = albumRepository.save(album);

        return convertToDTO(saved);
    }

    /**
     * 设置相册隐藏状态
     */
    @Transactional
    public AlbumDTO setAlbumHidden(Long albumId, Boolean isHidden) {
        return setAlbumHidden(albumId, isHidden, null);
    }

    @Transactional
    public AlbumDTO setAlbumHidden(Long albumId, Boolean isHidden, Long userId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在: " + albumId));
        validateAlbumOwnership(album, userId);

        album.setIsHidden(isHidden);
        Album saved = albumRepository.save(album);

        return convertToDTO(saved);
    }

    /**
     * 设置相册自定义封面
     * 注意：不重新生成封面，直接返回更新后的相册信息
     */
    @Transactional
    public AlbumDTO setAlbumCover(Long albumId, java.util.List<Long> coverImageIds) {
        return setAlbumCover(albumId, coverImageIds, null);
    }

    @Transactional
    public AlbumDTO setAlbumCover(Long albumId, java.util.List<Long> coverImageIds, Long userId) {
        Album album = albumRepository.findById(albumId)
            .orElseThrow(() -> new RuntimeException("相册不存在: " + albumId));
        validateAlbumOwnership(album, userId);

        log.info("设置相册封面: albumId={}, coverImageIds={}", albumId, coverImageIds);

        // 将封面ID列表序列化为JSON
        if (coverImageIds == null || coverImageIds.isEmpty()) {
            album.setCoverImageIds(null);
        } else {
            try {
                String json = objectMapper.writeValueAsString(coverImageIds);
                album.setCoverImageIds(json);
                log.info("封面ID序列化成功: {}", json);
            } catch (Exception e) {
                throw new RuntimeException("序列化封面ID失败", e);
            }
        }

        Album saved = albumRepository.save(album);
        log.info("相册封面保存成功: albumId={}, coverImageIds={}", albumId, saved.getCoverImageIds());
        
        // 直接返回，不调用 convertToDTO（避免重新生成封面导致超时）
        AlbumDTO dto = new AlbumDTO();
        dto.setId(saved.getId());
        dto.setName(saved.getName());
        dto.setPath(convertAlbumPath(saved.getPath()));
        dto.setCoverImageId(saved.getCoverImageId());
        
        // 解析封面ID列表
        if (saved.getCoverImageIds() != null && !saved.getCoverImageIds().isEmpty()) {
            try {
                java.util.List<Long> ids = objectMapper.readValue(
                    saved.getCoverImageIds(),
                    objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, Long.class)
                );
                dto.setCoverImageIds(ids);
            } catch (Exception e) {
                log.warn("解析封面图片ID列表失败: {}", e.getMessage());
            }
        }
        
        dto.setDescription(saved.getDescription());
        dto.setPhotoCount(saved.getPhotoCount());
        dto.setAggregateSubAlbums(saved.getAggregateSubAlbums());
        dto.setDownloadAllowed(saved.getDownloadAllowed());
        dto.setPhotoSortOrder(saved.getPhotoSortOrder());
        dto.setCreatedAt(saved.getCreatedAt());
        dto.setUpdatedAt(saved.getUpdatedAt());
        
        return dto;
    }

    /**
     * 创建相册（如果不存在的话）
     */
    @Transactional
    public AlbumDTO createAlbumIfNotExists(String path) {
        // 规范化路径：统一使用 / 分隔符，并去掉前导斜杠的Windows路径
        String normalizedPath = path.replace("\\", "/");
        // 处理前导斜杠的Windows路径，如 /D:/xxx -> D:/xxx
        if (normalizedPath.matches("^/[A-Za-z]:/.*")) {
            normalizedPath = normalizedPath.substring(1);
        }
        log.debug("createAlbumIfNotExists - 原始路径: {}, 规范化后: {}", path, normalizedPath);
        path = normalizedPath;

        Path directoryPath = resolveStoredDirectoryPath(path);
        Long userId = userPathService.extractUserIdFromPath(directoryPath.toString());
        String storedPath = toStoredDirectoryPath(directoryPath, userId);

        // 检查路径是否已经存在相册
        Optional<Album> existingAlbum = findExistingAlbumByCandidates(storedPath, directoryPath.toString(), path);
        if (existingAlbum.isPresent()) {
            return convertToDTO(existingAlbum.get());
        }

        // 检查路径是否存在且是目录
        java.io.File dir = directoryPath.toFile();
        if (!dir.exists() || !dir.isDirectory()) {
            throw new IllegalArgumentException("路径不存在或不是目录: " + path);
        }

        // 创建新相册
        Album newAlbum = new Album();
        newAlbum.setName(dir.getName());
        newAlbum.setPath(storedPath);
        newAlbum.setPathHash(calculateSha256(storedPath));
        newAlbum.setUserId(userId);

        // 从路径中解析相册名日期（用于排序）
        LocalDateTime albumNameDate = parseDateFromAlbumPath(storedPath);
        newAlbum.setAlbumNameDate(albumNameDate);

        Album saved = albumRepository.save(newAlbum);
        return convertToDTO(saved);
    }

    private Optional<Album> findExistingAlbumByCandidates(String... candidates) {
        if (candidates == null) {
            return Optional.empty();
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            Optional<Album> existing = albumRepository.findByPath(candidate);
            if (existing.isPresent()) {
                return existing;
            }
            String pathHash = calculateSha256(candidate);
            existing = albumRepository.findByPathHash(pathHash);
            if (existing.isPresent()) {
                return existing;
            }
        }
        return Optional.empty();
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
    @Transactional(readOnly = true)
    public List<Album> filterAggregatedAlbums(List<Album> albums) {
        return filterAggregatedAlbums(albums, true);
    }

    /**
     * 过滤被聚合的相册
     * @param filterHidden 是否保留隐藏相册（true=保留显示所有相册，false=过滤掉隐藏相册）
     */
    @Transactional(readOnly = true)
    public List<Album> filterAggregatedAlbums(List<Album> albums, boolean filterHidden) {
        // 获取所有开启了聚合功能的相册
        List<Album> aggregatingAlbums = albumRepository.findAlbumsWithAggregationEnabled();

        log.debug("filterAggregatedAlbums - 开启聚合的相册数量: {}", aggregatingAlbums.size());
        for (Album aa : aggregatingAlbums) {
            log.debug("filterAggregatedAlbums - 聚合相册: [{}], path: [{}], aggregateSubAlbums: {}", aa.getName(), convertAlbumPath(aa.getPath()), aa.getAggregateSubAlbums());
        }

        // 收集所有被聚合的相册路径（使用标准化路径）
        java.util.Set<String> aggregatedPaths = new java.util.HashSet<>();
        for (Album aggregatingAlbum : aggregatingAlbums) {
            // 递归收集所有子相册路径
            collectSubAlbumPaths(aggregatingAlbum.getPath(), aggregatedPaths);
        }

        log.debug("filterAggregatedAlbums - 被聚合的相册路径数量: {}", aggregatedPaths.size());
        for (String p : aggregatedPaths) {
            log.debug("filterAggregatedAlbums - 被聚合的路径: [{}]", p);
        }

        // 过滤掉被聚合的相册（使用标准化路径进行比较）
        List<Album> filtered = albums.stream()
            .filter(album -> {
                // 过滤隐藏相册（前台可见时过滤隐藏相册，后台管理时显示所有）
                if (!filterHidden && Boolean.TRUE.equals(album.getIsHidden())) {
                    return false;
                }

                // 标准化路径进行比较
                String normalizedPath = album.getPath().replace("\\", "/");
                // 如果是前导斜杠的Windows路径，去掉前导斜杠
                if (normalizedPath.matches("^/[A-Za-z]:/.*")) {
                    normalizedPath = normalizedPath.substring(1);
                }

                boolean shouldFilter = aggregatedPaths.contains(album.getPath()) || aggregatedPaths.contains(normalizedPath);
                log.debug("filterAggregatedAlbums - 检查相册 [{}], path: [{}], normalized: [{}], 过滤: {}",
                    album.getName(), convertAlbumPath(album.getPath()), convertAlbumPath(normalizedPath), shouldFilter);
                return !shouldFilter;
            })
            .collect(java.util.stream.Collectors.toList());

        log.debug("filterAggregatedAlbums - 过滤前: {}, 过滤后: {}", albums.size(), filtered.size());
        return filtered;
    }

    /**
     * 递归收集指定相册的所有子相册路径
     */
    private void collectSubAlbumPaths(String parentPath, java.util.Set<String> paths) {
        // 标准化路径：统一使用 / 分隔符，并去掉前导斜杠的Windows路径
        String normalizedPath = parentPath.replace("\\", "/");
        // 处理前导斜杠的Windows路径，如 /D:/xxx -> D:/xxx
        if (normalizedPath.matches("^/[A-Za-z]:/.*")) {
            normalizedPath = normalizedPath.substring(1);
        }
        // 确保路径以 / 结尾
        if (!normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath + "/";
        }

        log.debug("collectSubAlbumPaths - 父路径原始: [{}], 标准化: [{}]", parentPath, normalizedPath);

        // 计算 LIKE 模式
        String parentPathLike = normalizedPath + "%";

        // 使用标准化路径查询（Repository 会自动处理前导斜杠的Windows路径）
        List<Album> subAlbums = albumRepository.findDirectSubAlbumsNormalized(normalizedPath, parentPathLike, parentPathLike);

        // 计算当前层级
        String[] currentParts = normalizedPath.split("/");
        int currentLevel = currentParts.length;

        for (Album subAlbum : subAlbums) {
            // 标准化子相册路径
            String subPathNormalized = subAlbum.getPath().replace("\\", "/");
            if (subPathNormalized.matches("^/[A-Za-z]:/.*")) {
                subPathNormalized = subPathNormalized.substring(1);
            }
            String[] subParts = subPathNormalized.split("/");

            // 只添加直接子相册（层级 = 当前层级 + 1）
            if (subParts.length == currentLevel + 1) {
                // 同时添加标准化路径和原始路径，确保匹配成功
                paths.add(subPathNormalized);
                paths.add(subAlbum.getPath());
                log.debug("collectSubAlbumPaths - 添加子相册: [{}], path: [{}]", subAlbum.getName(), convertAlbumPath(subPathNormalized));

                // 如果子相册也开启了聚合，继续递归收集
                if (Boolean.TRUE.equals(subAlbum.getAggregateSubAlbums())) {
                    collectSubAlbumPaths(subAlbum.getPath(), paths);
                }
            }
        }
    }

    /**
     * 获取相册聚合的所有相册ID列表
     */
    public List<Long> getAggregatedAlbumIds(Long albumId) {
        List<Long> albumIds = new java.util.ArrayList<>();
        albumIds.add(albumId);

        // 检查相册是否开启了聚合下级相册
        albumRepository.findById(albumId).ifPresent(album -> {
            log.debug("getAggregatedAlbumIds - albumId: {}, name: {}, path: {}, aggregateSubAlbums: {}",
                albumId, album.getName(), convertAlbumPath(album.getPath()), album.getAggregateSubAlbums());
            if (Boolean.TRUE.equals(album.getAggregateSubAlbums())) {
                // 递归获取所有子相册ID，初始深度为0，最大深度10层
                log.debug("getAggregatedAlbumIds - 开始获取子相册，父路径: {}", convertAlbumPath(album.getPath()));
                addSubAlbumIds(album.getPath(), albumIds, 0, 10);
                log.debug("getAggregatedAlbumIds - 获取子相册结束，albumIds: {}", albumIds);
            }
        });

        log.debug("getAggregatedAlbumIds - 最终相册ID列表: {}", albumIds);
        return albumIds;
    }

    /**
     * 递归添加子相册ID到列表中
     * @param parentPath 父相册路径
     * @param albumIds 相册ID列表
     * @param currentDepth 当前深度，防止无限递归
     * @param maxDepth 最大递归深度，默认10层
     */
    private void addSubAlbumIds(String parentPath, List<Long> albumIds, int currentDepth, int maxDepth) {
        // 防止无限递归
        if (currentDepth > maxDepth) {
            log.warn("addSubAlbumIds - 达到最大递归深度 {}，停止查询子相册", maxDepth);
            return;
        }

        // 标准化路径：统一使用 / 分隔符
        String normalizedPath = parentPath.replace("\\", "/");
        // 处理前导斜杠的Windows路径，如 /D:/xxx -> D:/xxx
        if (normalizedPath.matches("^/[A-Za-z]:/.*")) {
            normalizedPath = normalizedPath.substring(1);
        }
        // 确保路径以 / 结尾
        if (!normalizedPath.endsWith("/")) {
            normalizedPath = normalizedPath + "/";
        }

        log.debug("addSubAlbumIds - parentPath原始: [{}], normalizedPath: [{}], depth: {}", parentPath, normalizedPath, currentDepth);

        // 打印数据库中所有路径的前缀匹配情况（仅在第一次调用时）
        if (currentDepth == 0) {
            log.debug("addSubAlbumIds - 准备查询数据库，父路径前缀: [{}]", normalizedPath.substring(0, Math.min(50, normalizedPath.length())));
        }

        // 简化查询：直接用前缀匹配，忽略前导斜杠的差异
        String pattern1 = normalizedPath + "%";
        String pattern2 = (normalizedPath.startsWith("/") ? normalizedPath : "/" + normalizedPath) + "%";
        
        // 查询所有以父路径开头的相册
        List<Album> subAlbums = albumRepository.findByPathPrefixes(pattern1, pattern2);
        
        // 计算父相册的层级深度
        int parentDepth = normalizedPath.split("/").length - 1;
        
        log.debug("addSubAlbumIds - 父层级: {}, 候选子相册数: {}", parentDepth, subAlbums.size());

        // 过滤出直接子相册（层级 = 父相册层级 + 1）
        List<Album> directSubAlbums = subAlbums.stream()
            .filter(sub -> {
                // 统一使用 / 分隔符
                String subPath = sub.getPath().replace("\\", "/");
                // 如果子路径以 / 开头（Windows风格），去掉前导斜杠后再计算层级
                if (subPath.matches("^/[A-Za-z]:/.*")) {
                    subPath = subPath.substring(1);
                }
                String[] subParts = subPath.split("/");
                int subDepth = subParts.length - 1;
                return subDepth == parentDepth + 1;
            })
            .collect(Collectors.toList());

        log.debug("addSubAlbumIds - 找到 {} 个直接子相册", directSubAlbums.size());
        for (Album subAlbum : directSubAlbums) {
            albumIds.add(subAlbum.getId());
            // 如果子相册也开启了聚合，继续递归
            if (Boolean.TRUE.equals(subAlbum.getAggregateSubAlbums())) {
                addSubAlbumIds(subAlbum.getPath(), albumIds, currentDepth + 1, maxDepth);
            }
        }
    }

    /**
     * 根据排序参数创建带有排序的Pageable
     */
    private Pageable createSortedPageable(Pageable pageable, String sort) {
        if (sort == null || sort.isEmpty()) {
            // 默认按名称升序排序
            Sort defaultSort = Sort.by(Sort.Direction.ASC, "name");
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }

        Sort sortObj;
        switch (sort) {
            case SystemConfigService.SORT_BY_NAME_ASC:
                sortObj = Sort.by(Sort.Direction.ASC, "name");
                break;
            case SystemConfigService.SORT_BY_NAME_DESC:
                sortObj = Sort.by(Sort.Direction.DESC, "name");
                break;
            case SystemConfigService.SORT_BY_CREATED_AT_ASC:
                sortObj = Sort.by(Sort.Direction.ASC, "createdAt");
                break;
            case SystemConfigService.SORT_BY_CREATED_AT_DESC:
                sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
                break;
            case SystemConfigService.SORT_BY_LATEST_PHOTO_TAKEN_ASC:
                sortObj = Sort.by(Sort.Direction.ASC, "latestPhotoTakenAt");
                break;
            case SystemConfigService.SORT_BY_LATEST_PHOTO_TAKEN_DESC:
                sortObj = Sort.by(Sort.Direction.DESC, "latestPhotoTakenAt");
                break;
            case SystemConfigService.SORT_BY_ALBUM_NAME_DATE_ASC:
                sortObj = Sort.by(Sort.Direction.ASC, "albumNameDate");
                break;
            case SystemConfigService.SORT_BY_ALBUM_NAME_DATE_DESC:
                sortObj = Sort.by(Sort.Direction.DESC, "albumNameDate");
                break;
            default:
                // 默认按名称升序排序
                sortObj = Sort.by(Sort.Direction.ASC, "name");
                break;
        }

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sortObj);
    }

    /**
     * 根据排序参数对相册列表进行排序（备用方法）
     */
    private List<Album> sortAlbums(List<Album> albums, String sort) {
        if (albums == null || albums.isEmpty()) {
            return albums;
        }

        if (sort == null || sort.isEmpty()) {
            return albums.stream()
                .sorted(Comparator.comparing(Album::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        }

        switch (sort) {
            case SystemConfigService.SORT_BY_NAME_ASC:
                return albums.stream()
                    .sorted(Comparator.comparing(Album::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());

            case SystemConfigService.SORT_BY_NAME_DESC:
                return albums.stream()
                    .sorted(Comparator.comparing(Album::getName, String.CASE_INSENSITIVE_ORDER).reversed())
                    .collect(Collectors.toList());

            case SystemConfigService.SORT_BY_CREATED_AT_ASC:
                return albums.stream()
                    .sorted(Comparator.comparing(Album::getCreatedAt))
                    .collect(Collectors.toList());

            case SystemConfigService.SORT_BY_CREATED_AT_DESC:
                return albums.stream()
                    .sorted(Comparator.comparing(Album::getCreatedAt).reversed())
                    .collect(Collectors.toList());

            case SystemConfigService.SORT_BY_LATEST_PHOTO_TAKEN_ASC:
                return albums.stream()
                    .sorted(Comparator.comparing((Album a) -> a.getLatestPhotoTakenAt(), Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());

            case SystemConfigService.SORT_BY_LATEST_PHOTO_TAKEN_DESC:
                return albums.stream()
                    .sorted(Comparator.comparing((Album a) -> a.getLatestPhotoTakenAt(), Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .collect(Collectors.toList());

            case SystemConfigService.SORT_BY_ALBUM_NAME_DATE_ASC:
                return albums.stream()
                    .sorted(Comparator.comparing((Album a) -> a.getAlbumNameDate(), Comparator.nullsLast(Comparator.naturalOrder())))
                    .collect(Collectors.toList());

            case SystemConfigService.SORT_BY_ALBUM_NAME_DATE_DESC:
                return albums.stream()
                    .sorted(Comparator.comparing((Album a) -> a.getAlbumNameDate(), Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .collect(Collectors.toList());

            default:
                // 默认按名称升序排序
                return albums.stream()
                    .sorted(Comparator.comparing(Album::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
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

    /**
     * 更新相册的时间字段
     */
    @Transactional
    public void updateAlbumTimeFields() {
        List<Album> allAlbums = listScopedAlbums(null);
        log.info("开始更新相册时间字段，总相册数: {}", allAlbums.size());

        int changedCount = 0;
        for (Album album : allAlbums) {
            boolean changed = updateSingleAlbumTimeFields(album);
            if (changed) {
                changedCount++;
            }
        }

        log.info("相册时间字段更新完成，共处理 {} 个相册，其中 {} 个相册的时间发生变化", allAlbums.size(), changedCount);
    }

    private List<Album> listScopedAlbums(Long userId) {
        List<Album> albums = new ArrayList<>();
        int pageNumber = 0;
        Page<Album> page;
        do {
            Pageable pageable = PageRequest.of(pageNumber, 200);
            page = userId == null ? albumRepository.findAll(pageable) : albumRepository.findByUserId(userId, pageable);
            albums.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return albums;
    }

    /**
     * 更新单个相册的时间字段
     */
    @Transactional
    public boolean updateSingleAlbumTimeFields(Album album) {
        LocalDateTime oldLatestTakenAt = album.getLatestPhotoTakenAt();
        LocalDateTime oldAlbumNameDate = album.getAlbumNameDate();

        // 获取相册的所有照片（如果是聚合相册，需要递归获取所有子相册的照片）
        List<Photo> photos = getAllPhotosForAlbum(album);

        // 计算相册拍摄时间（最晚的照片拍摄时间）
        LocalDateTime latestPhotoTakenAt = calculateLatestPhotoTakenAt(photos, album);

        // 计算相册名时间（从相册路径解析时间，支持向上查找）
        LocalDateTime albumNameDate = parseDateFromAlbumPath(album.getPath());

        // 如果路径和父路径都不包含时间名，则按规则回退：
        // 1) 使用相册（含子相册）中最晚的 EXIF 拍摄时间
        // 2) 如果也没有 EXIF 时间，则使用最晚的文件创建时间（createdAt）
        if (albumNameDate == null) {
            if (latestPhotoTakenAt != null) {
                albumNameDate = latestPhotoTakenAt;
            } else if (!photos.isEmpty()) {
                albumNameDate = photos.stream()
                    .map(Photo::getCreatedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            }
        }

        boolean changed =
            !Objects.equals(oldLatestTakenAt, latestPhotoTakenAt) ||
            !Objects.equals(oldAlbumNameDate, albumNameDate);

        // 更新相册并打印变化信息
        if (changed) {
            album.setLatestPhotoTakenAt(latestPhotoTakenAt);
            album.setAlbumNameDate(albumNameDate);
            albumRepository.save(album);

            log.info("相册时间更新: id={}, name='{}', latestPhotoTakenAt: {} -> {}, albumNameDate: {} -> {}",
                album.getId(),
                album.getName(),
                oldLatestTakenAt,
                latestPhotoTakenAt,
                oldAlbumNameDate,
                albumNameDate
            );
        }

        return changed;
    }

    /**
     * 获取相册的所有照片（如果是聚合相册，包含所有子相册的照片）
     */
    private List<Photo> getAllPhotosForAlbum(Album album) {
        List<Long> albumIds = new ArrayList<>();
        albumIds.add(album.getId());

        // 如果相册开启了聚合，递归收集所有子相册ID，初始深度为0，最大深度10层
        if (Boolean.TRUE.equals(album.getAggregateSubAlbums())) {
            addSubAlbumIds(album.getPath(), albumIds, 0, 10);
        }

        // 获取所有相关相册的照片（排除隐藏）
        List<Photo> allPhotos = new ArrayList<>();
        for (Long albumId : albumIds) {
            allPhotos.addAll(loadVisiblePhotosByAlbumId(albumId));
        }

        return allPhotos;
    }

    private List<Album> loadAlbumsForListing(String category, Long userId) {
        List<Album> albums = new ArrayList<>();
        if (category != null && !category.isEmpty()) {
            albums.addAll(loadCategoryAlbums(category, userId).stream()
                .filter(this::isAlbumVisibleInListing)
                .collect(Collectors.toList()));
            return albums;
        }

        int pageNumber = 0;
        Page<Album> page;
        do {
            Pageable pageable = PageRequest.of(pageNumber, 200);
            page = albumRepository.findAlbumsWithPhotosOrAggregation(pageable);
            albums.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return albums;
    }

    private List<Album> loadCategoryAlbums(String category, Long userId) {
        String normalizedCategory = category == null ? "" : category.trim();
        if (normalizedCategory.isEmpty()) {
            return List.of();
        }
        return userId != null
            ? albumRepository.findByUserIdAndTopLevelCategory(userId, normalizedCategory)
            : albumRepository.findByTopLevelCategory(normalizedCategory);
    }

    private boolean isAlbumVisibleInListing(Album album) {
        return album != null && (album.getPhotoCount() != null && album.getPhotoCount() > 0 || Boolean.TRUE.equals(album.getAggregateSubAlbums()));
    }

    private List<Photo> loadAllPhotosByAlbumId(Long albumId) {
        List<Photo> photos = new ArrayList<>();
        int pageNumber = 0;
        Page<Photo> page;
        do {
            page = photoRepository.findByAlbumId(albumId, PageRequest.of(pageNumber, 200));
            photos.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return photos;
    }

    private List<Photo> loadVisiblePhotosByAlbumId(Long albumId) {
        List<Photo> photos = new ArrayList<>();
        int pageNumber = 0;
        Page<Photo> page;
        do {
            page = photoRepository.findByAlbumIdAndIsHiddenFalse(albumId, PageRequest.of(pageNumber, 200));
            photos.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());
        return photos;
    }

    /**
     * 计算相册中最晚的照片拍摄时间
     */
    private LocalDateTime calculateLatestPhotoTakenAt(List<Photo> photos, Album album) {
        if (photos.isEmpty()) {
            return null;
        }

        // 优先使用拍摄时间（最晚的）
        Optional<LocalDateTime> latestTakenAt = photos.stream()
            .filter(photo -> photo.getTakenAt() != null)
            .map(Photo::getTakenAt)
            .max(Comparator.naturalOrder());

        if (latestTakenAt.isPresent()) {
            return latestTakenAt.get();
        }

        // 如果相册内所有图片都没有拍摄时间，优先使用相册路径上的日期时间
        LocalDateTime albumNameDate = parseDateFromAlbumPath(album.getPath());
        if (albumNameDate != null) {
            return albumNameDate;
        }

        // 如果相册开启了聚合，从子相册中找到最早的拍摄时间
        if (Boolean.TRUE.equals(album.getAggregateSubAlbums())) {
            Optional<LocalDateTime> subAlbumTakenAt = findEarliestTakenAtFromSubAlbums(album.getPath());
            if (subAlbumTakenAt.isPresent()) {
                return subAlbumTakenAt.get();
            }
        }

        // 最后使用最早的文件创建时间
        return photos.stream()
            .filter(photo -> photo.getCreatedAt() != null)
            .map(Photo::getCreatedAt)
            .min(Comparator.naturalOrder())  // 使用 min 而不是 max，获取最早的时间
            .orElse(null);
    }

    /**
     * 从相册路径中解析时间（支持向上查找父路径）
     * 优先级：当前相册名称 > 父路径中的时间
     * 支持格式：2025.01.01, 2025-01-01, 2025.01.01 9:10 等
     */
    private LocalDateTime parseDateFromAlbumPath(String albumPath) {
        if (albumPath == null || albumPath.trim().isEmpty()) {
            return null;
        }

        // 获取相对于base-path的路径部分
        String relativePath = getRelativePath(albumPath);
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }

        // 分割路径为各级目录
        String[] pathParts = relativePath.split("[/\\\\]");
        if (pathParts.length == 0) {
            return null;
        }

        // 从最深层开始向上查找（优先使用当前相册名称的时间）
        for (int i = pathParts.length - 1; i >= 0; i--) {
            String folderName = pathParts[i].trim();
            if (!folderName.isEmpty()) {
                LocalDateTime parsedTime = parseDateFromFolderName(folderName);
                if (parsedTime != null) {
                    return parsedTime;
                }
            }
        }


        return null;  // 没有找到匹配的时间格式
    }

    /**
     * 获取相对于base-path的路径
     */
    private String getRelativePath(String fullPath) {
        if (fullPath == null) {
            return null;
        }

        String tenantRelativePath = userPathService.extractTenantRelativePhotoPath(fullPath);
        if (tenantRelativePath != null) {
            return tenantRelativePath;
        }
        return null;  // 如果都匹配失败，返回null
    }


    /**
     * 从单个文件夹名称中解析时间
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
                        LocalDate date = LocalDate.parse(dateStr.toString(), formatter);
                        return date.atStartOfDay();
                    }
                } catch (DateTimeParseException | NumberFormatException e) {
                    // 解析失败，继续尝试下一个模式
                    continue;
                }
            }
        }


        return null;  // 没有找到匹配的时间格式
    }

}
