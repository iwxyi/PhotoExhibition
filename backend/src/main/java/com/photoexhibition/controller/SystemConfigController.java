package com.photoexhibition.controller;

import com.photoexhibition.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    /**
     * 获取所有系统配置
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllConfigs() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("configs", systemConfigService.getAllConfigs());
            resp.put("maxAlbumDepth", systemConfigService.getMaxAlbumDepth());
            resp.put("photoSortOrder", systemConfigService.getPhotoSortOrder());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 获取最大相册层级配置
     */
    @GetMapping("/max-album-depth")
    public ResponseEntity<Map<String, Object>> getMaxAlbumDepth() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("maxAlbumDepth", systemConfigService.getMaxAlbumDepth());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 设置最大相册层级
     */
    @PutMapping("/max-album-depth")
    public ResponseEntity<Map<String, Object>> setMaxAlbumDepth(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Integer depth = (Integer) request.get("maxAlbumDepth");
            if (depth == null) {
                resp.put("error", "maxAlbumDepth 参数不能为空");
                return ResponseEntity.badRequest().body(resp);
            }

            systemConfigService.setMaxAlbumDepth(depth);
            resp.put("message", "最大相册层级设置成功");
            resp.put("maxAlbumDepth", depth);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "设置配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 获取照片排序方式配置
     */
    @GetMapping("/photo-sort-order")
    public ResponseEntity<Map<String, Object>> getPhotoSortOrder() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("photoSortOrder", systemConfigService.getPhotoSortOrder());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 设置照片排序方式
     */
    @PutMapping("/photo-sort-order")
    public ResponseEntity<Map<String, Object>> setPhotoSortOrder(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String sortOrder = (String) request.get("photoSortOrder");
            if (sortOrder == null || sortOrder.trim().isEmpty()) {
                resp.put("error", "photoSortOrder 参数不能为空");
                return ResponseEntity.badRequest().body(resp);
            }

            systemConfigService.setPhotoSortOrder(sortOrder.trim());
            resp.put("message", "照片排序方式设置成功");
            resp.put("photoSortOrder", sortOrder.trim());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "设置配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }
}
