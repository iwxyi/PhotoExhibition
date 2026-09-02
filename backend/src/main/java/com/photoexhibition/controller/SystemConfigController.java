package com.photoexhibition.controller;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.SystemConfigService;
import com.photoexhibition.service.UserPathService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/admin/config")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SystemConfigController {

    private static final Pattern EMBEDDED_PATH_PATTERN =
        Pattern.compile("(storage://[^\\s,;]+|[A-Za-z]:\\\\[^\\s,;]+|/(?:[^\\s,;])+)");

    private String normalizeAdminColorMode(String value) {
        if (value == null) return "dark";
        String normalized = value.trim().toLowerCase();
        return "light".equals(normalized) || "dark".equals(normalized) || "system".equals(normalized)
            ? normalized
            : "dark";
    }

    private final AuthService authService;
    private final SystemConfigService systemConfigService;
    private final UserPathService userPathService;
    private final ObjectMapper objectMapper;

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
            resp.put("aiSearchEnabled", systemConfigService.isAiSearchEnabled());
            resp.put("aiSearchApiUrl", systemConfigService.getAiSearchApiUrl());
            String apiKey = systemConfigService.getAiSearchApiKey();
            if (apiKey != null && apiKey.length() > 8) {
                resp.put("aiSearchApiKey", apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4));
            } else {
                resp.put("aiSearchApiKey", apiKey != null && !apiKey.isEmpty() ? "****" : "");
            }
            resp.put("aiSearchModel", systemConfigService.getAiSearchModel());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/admin-theme")
    public ResponseEntity<Map<String, Object>> getAdminColorMode(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            resp.put("colorMode", normalizeAdminColorMode(user.getAdminColorMode()));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取后台颜色模式失败"));
            if (isUnauthorized(e)) {
                resp.put("message", "登录状态已失效，请重新登录");
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(resp);
            }
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PutMapping("/admin-theme")
    public ResponseEntity<Map<String, Object>> updateAdminColorMode(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                      @RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            String colorMode = normalizeAdminColorMode(
                request.get("colorMode") == null ? null : String.valueOf(request.get("colorMode"))
            );
            user.setAdminColorMode(colorMode);
            authService.saveUserAccount(user);
            resp.put("colorMode", colorMode);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "保存后台颜色模式失败"));
            if (isUnauthorized(e)) {
                resp.put("message", "登录状态已失效，请重新登录");
                return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(resp);
            }
            return ResponseEntity.status(500).body(resp);
        }
    }

    private boolean isUnauthorized(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("未授权") || message.contains("Token无效")
            || message.contains("Token已过期"));
    }

    @GetMapping("/file-browser-preferences")
    public ResponseEntity<Map<String, Object>> getFileBrowserPreferences(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Map<String, Object> preferences = loadFileBrowserPreferences(user.getId());
            resp.put("viewMode", normalizeAllowed(
                preferences.get("viewMode") == null ? null : String.valueOf(preferences.get("viewMode")),
                "grid",
                new String[]{"grid", "list"}
            ));
            resp.put("gridPreset", normalizeAllowed(
                preferences.get("gridPreset") == null ? null : String.valueOf(preferences.get("gridPreset")),
                "auto-md",
                new String[]{"auto-sm", "auto-md", "auto-lg", "cols-2", "cols-3", "cols-4", "cols-5"}
            ));
            resp.put("sortMode", normalizeAllowed(
                preferences.get("sortMode") == null ? null : String.valueOf(preferences.get("sortMode")),
                "name-asc",
                new String[]{"name-asc", "name-desc", "lastModified-desc", "lastModified-asc", "size-desc", "size-asc", "photoCount-desc", "photoCount-asc", "type-asc", "type-desc"}
            ));
            Integer pageSize = parseInteger(preferences.get("pageSize"), 48);
            resp.put("pageSize", (pageSize != null && (pageSize == 24 || pageSize == 48 || pageSize == 96 || pageSize == 200)) ? pageSize : 48);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取文件浏览器偏好失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PutMapping("/file-browser-preferences")
    public ResponseEntity<Map<String, Object>> updateFileBrowserPreferences(@RequestHeader("Authorization") String authorization,
                                                                            @RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            String viewMode = normalizeAllowed(
                request.get("viewMode") == null ? null : String.valueOf(request.get("viewMode")).trim(),
                "grid",
                new String[]{"grid", "list"}
            );
            String gridPreset = normalizeAllowed(
                request.get("gridPreset") == null ? null : String.valueOf(request.get("gridPreset")).trim(),
                "auto-md",
                new String[]{"auto-sm", "auto-md", "auto-lg", "cols-2", "cols-3", "cols-4", "cols-5"}
            );
            String sortMode = normalizeAllowed(
                request.get("sortMode") == null ? null : String.valueOf(request.get("sortMode")).trim(),
                "name-asc",
                new String[]{"name-asc", "name-desc", "lastModified-desc", "lastModified-asc", "size-desc", "size-asc", "photoCount-desc", "photoCount-asc", "type-asc", "type-desc"}
            );
            Integer pageSize = 48;
            Object pageSizeValue = request.get("pageSize");
            if (pageSizeValue instanceof Number) {
                pageSize = ((Number) pageSizeValue).intValue();
            } else if (pageSizeValue != null) {
                pageSize = Integer.parseInt(String.valueOf(pageSizeValue));
            }
            if (!(pageSize == 24 || pageSize == 48 || pageSize == 96 || pageSize == 200)) {
                pageSize = 48;
            }
            Map<String, Object> preferences = new HashMap<>();
            preferences.put("viewMode", viewMode);
            preferences.put("gridPreset", gridPreset);
            preferences.put("sortMode", sortMode);
            preferences.put("pageSize", pageSize);
            systemConfigService.setConfigValue(
                buildFileBrowserPreferenceKey(user.getId()),
                objectMapper.writeValueAsString(preferences),
                "文件浏览器偏好配置(JSON)"
            );
            resp.put("viewMode", viewMode);
            resp.put("gridPreset", gridPreset);
            resp.put("sortMode", sortMode);
            resp.put("pageSize", pageSize);
            resp.put("message", "文件浏览器偏好已保存");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "保存文件浏览器偏好失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "参数错误"));
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "参数错误"));
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "参数错误"));
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            log.error("相册排序设置失败: {}", e.getMessage(), e);
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "参数错误"));
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "参数错误"));
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "参数错误"));
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    // ===== AI 搜索配置 =====

    @GetMapping("/ai-search-enabled")
    public ResponseEntity<Map<String, Object>> getAiSearchEnabled() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("aiSearchEnabled", systemConfigService.isAiSearchEnabled());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PutMapping("/ai-search-enabled")
    public ResponseEntity<Map<String, Object>> setAiSearchEnabled(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Boolean enabled = (Boolean) request.get("aiSearchEnabled");
            if (enabled == null) {
                resp.put("error", "aiSearchEnabled 参数不能为空");
                return ResponseEntity.badRequest().body(resp);
            }
            systemConfigService.setAiSearchEnabled(enabled);
            resp.put("message", "AI搜索开关设置成功");
            resp.put("aiSearchEnabled", enabled);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/ai-search-api-url")
    public ResponseEntity<Map<String, Object>> getAiSearchApiUrl() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("aiSearchApiUrl", systemConfigService.getAiSearchApiUrl());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PutMapping("/ai-search-api-url")
    public ResponseEntity<Map<String, Object>> setAiSearchApiUrl(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String url = (String) request.get("aiSearchApiUrl");
            systemConfigService.setAiSearchApiUrl(url);
            resp.put("message", "AI搜索API地址设置成功");
            resp.put("aiSearchApiUrl", url != null ? url.trim() : "");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/ai-search-api-key")
    public ResponseEntity<Map<String, Object>> getAiSearchApiKey() {
        Map<String, Object> resp = new HashMap<>();
        try {
            String apiKey = systemConfigService.getAiSearchApiKey();
            if (apiKey != null && apiKey.length() > 8) {
                resp.put("aiSearchApiKey", apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4));
            } else {
                resp.put("aiSearchApiKey", apiKey != null && !apiKey.isEmpty() ? "****" : "");
            }
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PutMapping("/ai-search-api-key")
    public ResponseEntity<Map<String, Object>> setAiSearchApiKey(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String key = (String) request.get("aiSearchApiKey");
            systemConfigService.setAiSearchApiKey(key);
            resp.put("message", "AI搜索API密钥设置成功");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/ai-search-model")
    public ResponseEntity<Map<String, Object>> getAiSearchModel() {
        Map<String, Object> resp = new HashMap<>();
        try {
            resp.put("aiSearchModel", systemConfigService.getAiSearchModel());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取配置失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PutMapping("/ai-search-model")
    public ResponseEntity<Map<String, Object>> setAiSearchModel(@RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String model = (String) request.get("aiSearchModel");
            systemConfigService.setAiSearchModel(model);
            resp.put("message", "AI搜索模型设置成功");
            resp.put("aiSearchModel", model != null ? model.trim() : "gpt-4o");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置配置失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    private String sanitizeErrorMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        Matcher matcher = EMBEDDED_PATH_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        boolean replaced = false;
        while (matcher.find()) {
            String candidate = matcher.group(1);
            String sanitizedCandidate = userPathService.toDisplayPath(candidate, true);
            if (!candidate.equals(sanitizedCandidate)) {
                replaced = true;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(sanitizedCandidate));
        }
        matcher.appendTail(buffer);
        return replaced ? buffer.toString() : message;
    }

    private UserAccount requireCurrentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("未授权，请先登录");
        }
        return authService.getCurrentUserEntity(authorization.substring(7));
    }

    private String normalizeAllowed(String value, String fallback, String[] allowedValues) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        for (String allowedValue : allowedValues) {
            if (allowedValue.equals(value)) {
                return value;
            }
        }
        return fallback;
    }

    private Map<String, Object> loadFileBrowserPreferences(Long userId) {
        try {
            String raw = systemConfigService.getConfigValue(buildFileBrowserPreferenceKey(userId), "{}");
            Map<String, Object> data = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            return data == null ? new HashMap<>() : data;
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    private String buildFileBrowserPreferenceKey(Long userId) {
        return "user_file_browser_preferences_" + (userId == null ? 0 : userId);
    }

    private Integer parseInteger(Object value, Integer fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
