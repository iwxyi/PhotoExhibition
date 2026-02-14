package com.photoexhibition.controller;

import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.CoverImagesDTO;
import com.photoexhibition.dto.FilterRequest;
import com.photoexhibition.service.AlbumService;
import com.photoexhibition.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/albums")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AlbumController {

    private static final Logger log = LoggerFactory.getLogger(AlbumController.class);

    private final AlbumService albumService;
    private final SystemConfigService systemConfigService;

    /**
     * 获取所有相册（相册模式）
     */
    @GetMapping
    public ResponseEntity<Page<AlbumDTO>> getAllAlbums(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort) {
        log.info("获取相册列表 - 页码: {}, 数量: {}, 分类: {}, 排序: {}", page, size, category, sort);
        // 参数校验，确保 page 和 size 至少为 1
        if (page < 0) page = 0;
        if (size < 1) size = 12;
        Pageable pageable = PageRequest.of(page, size);
        Page<AlbumDTO> albums = albumService.getAllAlbumsWithCover(pageable, category, sort);
        log.info("返回 {} 个相册, 总数: {}", albums.getNumberOfElements(), albums.getTotalElements());
        return ResponseEntity.ok(albums);
    }

    /**
     * 根据标签筛选相册
     */
    @PostMapping("/filter")
    public ResponseEntity<Page<AlbumDTO>> filterAlbums(@RequestBody FilterRequest request) {
        // 参数校验
        int page = request.getPage();
        int size = request.getSize();
        if (page < 0) page = 0;
        if (size < 1) size = 12;
        Pageable pageable = PageRequest.of(page, size);
        Page<AlbumDTO> albums = albumService.filterAlbums(request, pageable);
        return ResponseEntity.ok(albums);
    }

    /**
     * 获取相册详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<AlbumDTO> getAlbumById(@PathVariable Long id) {
        AlbumDTO album = albumService.getAlbumById(id);
        return ResponseEntity.ok(album);
    }

    /**
     * 获取相册的封面图片组合
     */
    @GetMapping("/{id}/cover")
    public ResponseEntity<CoverImagesDTO> getAlbumCover(@PathVariable Long id) {
        CoverImagesDTO cover = albumService.getAlbumCoverImages(id);
        return ResponseEntity.ok(cover);
    }

    /**
     * 更新相册（名称/描述）
     */
    @PutMapping("/{id}")
    public ResponseEntity<AlbumDTO> updateAlbum(@PathVariable Long id, @RequestBody AlbumDTO dto) {
        return ResponseEntity.ok(albumService.updateAlbum(id, dto));
    }

    /**
     * 设置相册聚合下级相册
     */
    @PutMapping("/{id}/aggregate-sub-albums")
    public ResponseEntity<AlbumDTO> setAggregateSubAlbums(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Boolean> request) {
        Boolean aggregate = request.get("aggregateSubAlbums");
        if (aggregate == null) {
            return ResponseEntity.badRequest().build();
        }
        AlbumDTO album = albumService.setAggregateSubAlbums(id, aggregate);
        return ResponseEntity.ok(album);
    }

    /**
     * 获取相册的直接子相册（不经过聚合过滤）
     */
    @GetMapping("/{id}/sub-albums")
    public ResponseEntity<List<AlbumDTO>> getSubAlbums(@PathVariable Long id) {
        List<AlbumDTO> subAlbums = albumService.getSubAlbums(id);
        return ResponseEntity.ok(subAlbums);
    }

    /**
     * 设置相册照片排序方式
     */
    @PutMapping("/{id}/photo-sort-order")
    public ResponseEntity<AlbumDTO> setAlbumPhotoSortOrder(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> request) {
        String sortOrder = (String) request.get("photoSortOrder");
        AlbumDTO album = albumService.setAlbumPhotoSortOrder(id, sortOrder);
        return ResponseEntity.ok(album);
    }

    /**
     * 设置相册下载权限
     */
    @PutMapping("/{id}/download-allowed")
    public ResponseEntity<AlbumDTO> setAlbumDownloadAllowed(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Boolean> request) {
        Boolean downloadAllowed = request.get("downloadAllowed");
        if (downloadAllowed == null) {
            return ResponseEntity.badRequest().build();
        }
        AlbumDTO album = albumService.setAlbumDownloadAllowed(id, downloadAllowed);
        return ResponseEntity.ok(album);
    }

    /**
     * 获取相册排序设置（公开API）
     */
    @GetMapping("/sort-order")
    public ResponseEntity<Map<String, Object>> getAlbumSortOrder() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("albumSortOrder", systemConfigService.getAlbumSortOrder());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 创建相册（如果路径存在但没有图片，也会创建）
     */
    @PostMapping
    public ResponseEntity<AlbumDTO> createAlbum(@RequestBody java.util.Map<String, String> request) {
        String path = request.get("path");
        if (path == null || path.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(null);
        }

        try {
            AlbumDTO album = albumService.createAlbumIfNotExists(path.trim());
            return ResponseEntity.ok(album);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(null);
        }
    }

    /**
     * 删除相册
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlbum(@PathVariable Long id) {
        albumService.deleteAlbum(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 获取相册总数
     */
    @GetMapping("/count")
    public ResponseEntity<Long> getAlbumsCount(@RequestParam(required = false) String category) {
        long count = albumService.getAlbumsCount(category);
        return ResponseEntity.ok(count);
    }

    /**
     * 获取所有一级分类
     */
    @GetMapping("/categories")
    public ResponseEntity<java.util.List<String>> getCategories() {
        return ResponseEntity.ok(albumService.getCategories());
    }

    /**
     * 为相册添加标签
     */
    @PostMapping("/{albumId}/tags/{tagId}")
    public ResponseEntity<AlbumDTO> addTagToAlbum(
            @PathVariable Long albumId,
            @PathVariable Long tagId) {
        AlbumDTO album = albumService.addTagToAlbum(albumId, tagId);
        return ResponseEntity.ok(album);
    }

    /**
     * 从相册移除标签
     */
    @DeleteMapping("/{albumId}/tags/{tagId}")
    public ResponseEntity<AlbumDTO> removeTagFromAlbum(
            @PathVariable Long albumId,
            @PathVariable Long tagId) {
        AlbumDTO album = albumService.removeTagFromAlbum(albumId, tagId);
        return ResponseEntity.ok(album);
    }

    /**
     * 设置相册自定义封面
     * @param albumId 相册ID
     * @param request 包含coverImageIds列表的请求体
     */
    @PutMapping("/{albumId}/cover")
    public ResponseEntity<AlbumDTO> setAlbumCover(
            @PathVariable Long albumId,
            @RequestBody java.util.Map<String, java.util.List<Long>> request) {
        java.util.List<Long> coverImageIds = request.get("coverImageIds");
        if (coverImageIds == null) {
            coverImageIds = new java.util.ArrayList<>();
        }
        
        // 限制最多4个封面
        if (coverImageIds.size() > 4) {
            coverImageIds = coverImageIds.subList(0, 4);
        }
        
        AlbumDTO album = albumService.setAlbumCover(albumId, coverImageIds);
        return ResponseEntity.ok(album);
    }
}

