package com.photoexhibition.controller;

import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.AlbumMoveRequest;
import com.photoexhibition.dto.AlbumMoveResult;
import com.photoexhibition.dto.CoverImagesDTO;
import com.photoexhibition.dto.FilterRequest;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.service.AlbumMoveService;
import com.photoexhibition.service.AlbumService;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.PublicUserScopeService;
import com.photoexhibition.service.SystemConfigService;
import com.photoexhibition.service.UserPathService;
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
    private final AlbumMoveService albumMoveService;
    private final SystemConfigService systemConfigService;
    private final PublicUserScopeService publicUserScopeService;
    private final AuthService authService;
    private final UserPathService userPathService;

    /**
     * 获取所有相册（相册模式）
     */
    @GetMapping
    public ResponseEntity<Page<AlbumDTO>> getAllAlbums(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "false") boolean includeHidden,
            @RequestParam(required = false) String userSlug) {
        log.info("获取相册列表 - 页码: {}, 数量: {}, 分类: {}, 排序: {}, includeHidden: {}", page, size, category, sort, includeHidden);
        // 参数校验，确保 page 和 size 至少为 1
        if (page < 0) page = 0;
        if (size < 1) size = 12;
        Pageable pageable = PageRequest.of(page, size);
        Long userId = publicUserScopeService.resolveUserId(userSlug);
        Page<AlbumDTO> albums = albumService.getAllAlbumsWithCover(pageable, category, sort, includeHidden, userId);
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
    public ResponseEntity<AlbumDTO> getAlbumById(@PathVariable Long id,
                                                 @RequestParam(required = false) String userSlug) {
        AlbumDTO album = albumService.getAlbumById(id, publicUserScopeService.resolveUserId(userSlug));
        return ResponseEntity.ok(album);
    }

    /**
     * 根据名称搜索相册（用于短链接）
     */
    @GetMapping("/search")
    public ResponseEntity<AlbumDTO> searchAlbumByName(@RequestParam String name,
                                                      @RequestParam(required = false) String userSlug) {
        AlbumDTO album = albumService.searchAlbumByName(name, publicUserScopeService.resolveUserId(userSlug));
        if (album == null) {
            return ResponseEntity.notFound().build();
        }
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
    public ResponseEntity<AlbumDTO> updateAlbum(@RequestHeader("Authorization") String authorization,
                                                @PathVariable Long id,
                                                @RequestBody AlbumDTO dto) {
        UserAccount currentUser = requireCurrentUser(authorization);
        return ResponseEntity.ok(albumService.updateAlbum(id, dto, albumService.resolveScopedUserId(currentUser)));
    }

    /**
     * 重命名相册（同时重命名文件夹和数据库记录）
     */
    @PostMapping("/{id}/rename")
    public ResponseEntity<Map<String, Object>> renameAlbum(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        UserAccount currentUser = requireCurrentUser(authorization);
        String newName = request.get("newName");
        if (newName == null || newName.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "新名称不能为空");
            return ResponseEntity.badRequest().body(error);
        }
        Map<String, Object> result = albumService.renameAlbum(id, newName.trim(), albumService.resolveScopedUserId(currentUser));
        return ResponseEntity.ok(result);
    }

    /**
     * 设置相册聚合下级相册
     */
    @PutMapping("/{id}/aggregate-sub-albums")
    public ResponseEntity<AlbumDTO> setAggregateSubAlbums(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Boolean> request) {
        UserAccount currentUser = requireCurrentUser(authorization);
        Boolean aggregate = request.get("aggregateSubAlbums");
        if (aggregate == null) {
            return ResponseEntity.badRequest().build();
        }
        AlbumDTO album = albumService.setAggregateSubAlbums(id, aggregate, albumService.resolveScopedUserId(currentUser));
        return ResponseEntity.ok(album);
    }

    /**
     * 获取相册的直接子相册（不经过聚合过滤）
     */
    @GetMapping("/{id}/sub-albums")
    public ResponseEntity<List<AlbumDTO>> getSubAlbums(@RequestHeader("Authorization") String authorization,
                                                       @PathVariable Long id) {
        UserAccount currentUser = requireCurrentUser(authorization);
        List<AlbumDTO> subAlbums = albumService.getSubAlbums(id, albumService.resolveScopedUserId(currentUser));
        return ResponseEntity.ok(subAlbums);
    }

    /**
     * 设置相册照片排序方式
     */
    @PutMapping("/{id}/photo-sort-order")
    public ResponseEntity<AlbumDTO> setAlbumPhotoSortOrder(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Object> request) {
        UserAccount currentUser = requireCurrentUser(authorization);
        String sortOrder = (String) request.get("photoSortOrder");
        AlbumDTO album = albumService.setAlbumPhotoSortOrder(id, sortOrder, albumService.resolveScopedUserId(currentUser));
        return ResponseEntity.ok(album);
    }

    /**
     * 设置相册下载权限
     */
    @PutMapping("/{id}/download-allowed")
    public ResponseEntity<AlbumDTO> setAlbumDownloadAllowed(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Boolean> request) {
        UserAccount currentUser = requireCurrentUser(authorization);
        Boolean downloadAllowed = request.get("downloadAllowed");
        if (downloadAllowed == null) {
            return ResponseEntity.badRequest().build();
        }
        AlbumDTO album = albumService.setAlbumDownloadAllowed(id, downloadAllowed, albumService.resolveScopedUserId(currentUser));
        return ResponseEntity.ok(album);
    }

    /**
     * 设置相册隐藏状态
     */
    @PutMapping("/{id}/hidden")
    public ResponseEntity<AlbumDTO> setAlbumHidden(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestBody java.util.Map<String, Boolean> request) {
        UserAccount currentUser = requireCurrentUser(authorization);
        Boolean isHidden = request.get("isHidden");
        if (isHidden == null) {
            return ResponseEntity.badRequest().build();
        }
        AlbumDTO album = albumService.setAlbumHidden(id, isHidden, albumService.resolveScopedUserId(currentUser));
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
            resp.put("error", userPathService.sanitizeVisibleText(e.getMessage() == null ? "获取配置失败" : e.getMessage()));
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
    public ResponseEntity<Void> deleteAlbum(@RequestHeader("Authorization") String authorization,
                                            @PathVariable Long id) {
        UserAccount currentUser = requireCurrentUser(authorization);
        albumService.deleteAlbum(id, albumService.resolveScopedUserId(currentUser));
        return ResponseEntity.ok().build();
    }

    /**
     * 移动相册到新路径
     */
    @PostMapping("/{id}/move")
    public ResponseEntity<AlbumMoveResult> moveAlbum(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestBody AlbumMoveRequest request) {
        UserAccount currentUser = requireCurrentUser(authorization);
        log.info("移动相册 {} 到 {}, 冲突处理: {}",
                id, userPathService.toDisplayPath(request.getTargetPath(), true), request.getConflictResolution());
        AlbumMoveResult result = albumMoveService.moveAlbum(currentUser, id, request.getTargetPath(), request.getConflictResolution());
        return ResponseEntity.ok(result);
    }

    /**
     * 检查移动相册是否有冲突（预检）
     */
    @GetMapping("/{id}/move/check")
    public ResponseEntity<AlbumMoveResult> checkMoveAlbum(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestParam String targetPath) {
        UserAccount currentUser = requireCurrentUser(authorization);
        AlbumMoveResult result = albumMoveService.checkMove(currentUser, id, targetPath);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取分类列表（用于移动至分类）
     */
    @GetMapping("/move/categories")
    public ResponseEntity<List<Map<String, String>>> getMoveCategories(@RequestHeader("Authorization") String authorization) {
        UserAccount currentUser = requireCurrentUser(authorization);
        return ResponseEntity.ok(albumMoveService.getCategories(currentUser));
    }

    /**
     * 获取相册的下一级子目录（用于移动至下一级）
     */
    @GetMapping("/{id}/move/children")
    public ResponseEntity<List<Map<String, String>>> getMoveChildren(@RequestHeader("Authorization") String authorization,
                                                                     @PathVariable Long id) {
        UserAccount currentUser = requireCurrentUser(authorization);
        return ResponseEntity.ok(albumMoveService.getChildDirectories(currentUser, id));
    }

    /**
     * 获取相册的同级目录（用于合并至同级）
     */
    @GetMapping("/{id}/move/siblings")
    public ResponseEntity<List<Map<String, String>>> getMoveSiblings(@RequestHeader("Authorization") String authorization,
                                                                     @PathVariable Long id) {
        UserAccount currentUser = requireCurrentUser(authorization);
        return ResponseEntity.ok(albumMoveService.getSiblingDirectories(currentUser, id));
    }

    /**
     * 合并相册到同级目录
     */
    @PostMapping("/{id}/merge")
    public ResponseEntity<Map<String, Object>> mergeAlbum(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        UserAccount currentUser = requireCurrentUser(authorization);
        String targetPath = request.get("targetPath");
        if (targetPath == null || targetPath.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "目标路径不能为空");
            return ResponseEntity.badRequest().body(error);
        }
        Map<String, Object> result = albumMoveService.mergeAlbum(currentUser, id, targetPath);
        return ResponseEntity.ok(result);
    }

    /**
     * 列出指定路径的子目录（用于路径选择器）
     */
    @GetMapping("/move/directories")
    public ResponseEntity<Map<String, Object>> listMoveDirectories(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) String path) {
        UserAccount currentUser = requireCurrentUser(authorization);
        return ResponseEntity.ok(albumMoveService.listDirectories(currentUser, path));
    }

    /**
     * 获取相册总数
     */
    @GetMapping("/count")
    public ResponseEntity<Long> getAlbumsCount(@RequestParam(required = false) String category,
                                               @RequestParam(defaultValue = "false") boolean includeHidden,
                                               @RequestParam(required = false) String userSlug) {
        long count = albumService.getAlbumsCount(category, includeHidden, publicUserScopeService.resolveUserId(userSlug));
        return ResponseEntity.ok(count);
    }

    /**
     * 获取所有一级分类
     */
    @GetMapping("/categories")
    public ResponseEntity<java.util.List<String>> getCategories(@RequestParam(required = false) String userSlug) {
        return ResponseEntity.ok(albumService.getCategories(publicUserScopeService.resolveUserId(userSlug)));
    }

    /**
     * 为相册添加标签
     */
    @PostMapping("/{albumId}/tags/{tagId}")
    public ResponseEntity<AlbumDTO> addTagToAlbum(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long albumId,
            @PathVariable Long tagId) {
        UserAccount currentUser = requireCurrentUser(authorization);
        AlbumDTO album = albumService.addTagToAlbum(albumId, tagId, currentUser.getId());
        return ResponseEntity.ok(album);
    }

    /**
     * 从相册移除标签
     */
    @DeleteMapping("/{albumId}/tags/{tagId}")
    public ResponseEntity<AlbumDTO> removeTagFromAlbum(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long albumId,
            @PathVariable Long tagId) {
        UserAccount currentUser = requireCurrentUser(authorization);
        AlbumDTO album = albumService.removeTagFromAlbum(albumId, tagId, currentUser.getId());
        return ResponseEntity.ok(album);
    }

    private UserAccount requireCurrentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("未授权，请先登录");
        }
        return authService.getCurrentUserEntity(authorization.substring(7));
    }

    /**
     * 设置相册自定义封面
     * @param albumId 相册ID
     * @param request 包含coverImageIds列表的请求体
     */
    @PutMapping("/{albumId}/cover")
    public ResponseEntity<AlbumDTO> setAlbumCover(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long albumId,
            @RequestBody java.util.Map<String, java.util.List<Long>> request) {
        UserAccount currentUser = requireCurrentUser(authorization);
        java.util.List<Long> coverImageIds = request.get("coverImageIds");
        if (coverImageIds == null) {
            coverImageIds = new java.util.ArrayList<>();
        }
        
        // 限制最多4个封面
        if (coverImageIds.size() > 4) {
            coverImageIds = coverImageIds.subList(0, 4);
        }
        
        AlbumDTO album = albumService.setAlbumCover(albumId, coverImageIds, albumService.resolveScopedUserId(currentUser));
        return ResponseEntity.ok(album);
    }
}
