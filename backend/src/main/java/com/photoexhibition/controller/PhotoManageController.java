package com.photoexhibition.controller;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.PhotoManageService;
import com.photoexhibition.service.UserPathService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/photos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PhotoManageController {

    private final AuthService authService;
    private final PhotoManageService photoManageService;
    private final UserPathService userPathService;

    /**
     * Get move targets for photos in an album (parent dir, sibling dirs, child dirs)
     */
    @GetMapping("/move-targets/{albumId}")
    public ResponseEntity<Map<String, Object>> getMoveTargets(@RequestHeader("Authorization") String authorization,
                                                              @PathVariable Long albumId) {
        return ResponseEntity.ok(photoManageService.getMoveTargets(requireCurrentUser(authorization), albumId));
    }

    /**
     * Batch move photos to a target directory.
     * Pass conflictResolution: null (detect), "overwrite", "rename", "skip".
     */
    @PostMapping("/batch-move")
    public ResponseEntity<Map<String, Object>> batchMovePhotos(@RequestHeader("Authorization") String authorization,
                                                               @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) request.get("photoIds");
        String targetPath = (String) request.get("targetPath");
        String conflictResolution = (String) request.get("conflictResolution");

        if (rawIds == null || targetPath == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少参数"));
        }

        List<Long> photoIds = rawIds.stream().map(Integer::longValue).collect(java.util.stream.Collectors.toList());
        log.info("批量移动照片: {} 张 -> {}, 冲突处理: {}",
                photoIds.size(), userPathService.toDisplayPath(targetPath, true), conflictResolution);
        Map<String, Object> result = photoManageService.movePhotos(requireCurrentUser(authorization), photoIds, targetPath, conflictResolution);
        return ResponseEntity.ok(result);
    }

    /**
     * Batch delete photos (files + DB records)
     */
    @PostMapping("/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDeletePhotos(@RequestHeader("Authorization") String authorization,
                                                                 @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) request.get("photoIds");

        if (rawIds == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少参数"));
        }

        List<Long> photoIds = rawIds.stream().map(Integer::longValue).collect(java.util.stream.Collectors.toList());
        log.info("批量删除照片: {} 张", photoIds.size());
        Map<String, Object> result = photoManageService.deletePhotos(requireCurrentUser(authorization), photoIds);
        return ResponseEntity.ok(result);
    }

    private UserAccount requireCurrentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("未授权，请先登录");
        }
        return authService.getCurrentUserEntity(authorization.substring(7));
    }
}
