package com.photoexhibition.controller;

import com.photoexhibition.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/config")
@RequiredArgsConstructor
@Slf4j
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
            resp.put("albumSortOrder", systemConfigService.getAlbumSortOrder());
            resp.put("wallSortOrder", systemConfigService.getWallSortOrder());
            resp.put("minClusterFaceCount", systemConfigService.getMinClusterFaceCount());
            resp.put("globalDownloadAllowed", systemConfigService.isGlobalDownloadAllowed());
            resp.put("albumCategorySortOrder", systemConfigService.getAlbumCategorySortOrder());
            resp.put("tagIgnoreList", systemConfigService.getTagIgnoreList());
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

    /**
     * 获取相册排序方式配置
     */
    @GetMapping("/album-sort-order")
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
     * 设置相册排序方式
     */
    @PutMapping("/album-sort-order")
    public ResponseEntity<Map<String, Object>> setAlbumSortOrder(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String sortOrder = (String) request.get("albumSortOrder");
            log.info("收到相册排序设置请求: albumSortOrder = {}", sortOrder);
            if (sortOrder == null || sortOrder.trim().isEmpty()) {
                resp.put("error", "albumSortOrder 参数不能为空");
                return ResponseEntity.badRequest().body(resp);
            }

            systemConfigService.setAlbumSortOrder(sortOrder.trim());
            log.info("相册排序方式已更新: {}", sortOrder.trim());
            resp.put("message", "相册排序方式设置成功");
            resp.put("albumSortOrder", sortOrder.trim());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            log.warn("相册排序设置参数错误: {}", e.getMessage());
            resp.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            log.error("相册排序设置失败: {}", e.getMessage(), e);
            resp.put("error", e.getMessage() != null ? e.getMessage() : "设置配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 获取图墙排序方式配置
     */
    @GetMapping("/wall-sort-order")
    public ResponseEntity<Map<String, Object>> getWallSortOrder() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("wallSortOrder", systemConfigService.getWallSortOrder());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 设置图墙排序方式
     */
    @PutMapping("/wall-sort-order")
    public ResponseEntity<Map<String, Object>> setWallSortOrder(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String sortOrder = (String) request.get("wallSortOrder");
            if (sortOrder == null || sortOrder.trim().isEmpty()) {
                resp.put("error", "wallSortOrder 参数不能为空");
                return ResponseEntity.badRequest().body(resp);
            }

            systemConfigService.setWallSortOrder(sortOrder.trim());
            resp.put("message", "图墙排序方式设置成功");
            resp.put("wallSortOrder", sortOrder.trim());
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
     * 获取聚类显示最小人脸数量配置
     */
    @GetMapping("/min-cluster-face-count")
    public ResponseEntity<Map<String, Object>> getMinClusterFaceCount() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("minClusterFaceCount", systemConfigService.getMinClusterFaceCount());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 设置聚类显示最小人脸数量
     */
    @PutMapping("/min-cluster-face-count")
    public ResponseEntity<Map<String, Object>> setMinClusterFaceCount(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Integer minCount = (Integer) request.get("minClusterFaceCount");
            if (minCount == null) {
                resp.put("error", "minClusterFaceCount 参数不能为空");
                return ResponseEntity.badRequest().body(resp);
            }

            systemConfigService.setMinClusterFaceCount(minCount);
            resp.put("message", "聚类最小人脸数量设置成功");
            resp.put("minClusterFaceCount", minCount);
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
     * 获取全局下载权限配置
     */
    @GetMapping("/global-download-allowed")
    public ResponseEntity<Map<String, Object>> getGlobalDownloadAllowed() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("globalDownloadAllowed", systemConfigService.isGlobalDownloadAllowed());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 设置全局下载权限
     */
    @PutMapping("/global-download-allowed")
    public ResponseEntity<Map<String, Object>> setGlobalDownloadAllowed(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Boolean allowed = (Boolean) request.get("globalDownloadAllowed");
            if (allowed == null) {
                resp.put("error", "globalDownloadAllowed 参数不能为空");
                return ResponseEntity.badRequest().body(resp);
            }

            systemConfigService.setGlobalDownloadAllowed(allowed);
            resp.put("message", "全局下载权限设置成功");
            resp.put("globalDownloadAllowed", allowed);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "设置配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 获取相册分类排序配置
     */
    @GetMapping("/album-category-sort-order")
    public ResponseEntity<Map<String, Object>> getAlbumCategorySortOrder() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("albumCategorySortOrder", systemConfigService.getAlbumCategorySortOrder());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 设置相册分类排序
     */
    @PutMapping("/album-category-sort-order")
    public ResponseEntity<Map<String, Object>> setAlbumCategorySortOrder(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String sortOrder = (String) request.get("albumCategorySortOrder");
            systemConfigService.setAlbumCategorySortOrder(sortOrder);
            resp.put("message", "相册分类排序设置成功");
            resp.put("albumCategorySortOrder", sortOrder != null ? sortOrder.trim() : "");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "设置配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 获取标签忽略列表配置
     */
    @GetMapping("/tag-ignore-list")
    public ResponseEntity<Map<String, Object>> getTagIgnoreList() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("tagIgnoreList", systemConfigService.getTagIgnoreList());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 设置标签忽略列表
     */
    @PutMapping("/tag-ignore-list")
    public ResponseEntity<Map<String, Object>> setTagIgnoreList(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String tagIgnoreList = (String) request.get("tagIgnoreList");
            systemConfigService.setTagIgnoreList(tagIgnoreList);
            resp.put("message", "标签忽略列表设置成功");
            resp.put("tagIgnoreList", tagIgnoreList != null ? tagIgnoreList.trim() : "");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "设置配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 获取全局氛围特效开关
     */
    @GetMapping("/atmosphere-enabled")
    public ResponseEntity<Map<String, Object>> getAtmosphereEnabled() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("atmosphereEnabled", systemConfigService.isAtmosphereEnabled());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 设置全局氛围特效开关
     */
    @PutMapping("/atmosphere-enabled")
    public ResponseEntity<Map<String, Object>> setAtmosphereEnabled(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Boolean enabled = (Boolean) request.get("atmosphereEnabled");
            if (enabled == null) {
                resp.put("error", "atmosphereEnabled 参数不能为空");
                return ResponseEntity.badRequest().body(resp);
            }

            systemConfigService.setAtmosphereEnabled(enabled);
            resp.put("message", "全局氛围特效开关设置成功");
            resp.put("atmosphereEnabled", enabled);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "设置配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 获取人脸聚类阈值配置
     */
    @GetMapping("/face-cluster-threshold")
    public ResponseEntity<Map<String, Object>> getFaceClusterThreshold() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("faceClusterThreshold", systemConfigService.getFaceClusterThreshold());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取配置失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 设置人脸聚类阈值
     */
    @PutMapping("/face-cluster-threshold")
    public ResponseEntity<Map<String, Object>> setFaceClusterThreshold(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Object thresholdObj = request.get("faceClusterThreshold");
            double threshold;
            if (thresholdObj instanceof Number) {
                threshold = ((Number) thresholdObj).doubleValue();
            } else if (thresholdObj instanceof String) {
                threshold = Double.parseDouble((String) thresholdObj);
            } else {
                resp.put("error", "faceClusterThreshold 参数不能为空");
                return ResponseEntity.badRequest().body(resp);
            }

            systemConfigService.setFaceClusterThreshold(threshold);
            resp.put("message", "人脸聚类阈值设置成功");
            resp.put("faceClusterThreshold", threshold);
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
