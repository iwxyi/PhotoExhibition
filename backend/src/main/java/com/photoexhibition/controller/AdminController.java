package com.photoexhibition.controller;

import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.LoginResponse;
import com.photoexhibition.entity.AdminUser;
import com.photoexhibition.repository.AdminUserRepository;
import com.photoexhibition.service.AlbumService;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.DataCleanupService;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.service.FilterOptionService;
import com.photoexhibition.service.PhotoScanService;
import com.photoexhibition.util.ONNXDiagnosticUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class AdminController {

    private final PhotoScanService photoScanService;
    private final AdminUserRepository adminUserRepository;
    private final DataCleanupService dataCleanupService;
    private final AlbumService albumService;
    private final FilterOptionService filterOptionService;
    private final PhotoRepository photoRepository;
    private final AuthService authService;
    private final ONNXDiagnosticUtil onnxDiagnosticUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 单独触发更新所有照片的 EXIF 字段（用于已存在图片的后处理）
     * 包括数值字段（快门秒数、焦距mm、光圈值）和字符串字段（ISO、镜头型号）
     */
    @PostMapping("/recalculate-photo-colors")
    public ResponseEntity<Map<String, Object>> recalculatePhotoColors() {
        Map<String, Object> resp = new HashMap<>();
        try {
            String taskId = UUID.randomUUID().toString();
            photoScanService.recalculateAllPhotoColorsAsync(taskId);
            resp.put("message", "已异步触发照片颜色重新计算");
            resp.put("taskId", taskId);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("照片颜色重新计算失败", e);
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PostMapping("/update-color-categories")
    public ResponseEntity<Map<String, Object>> updateColorCategories() {
        Map<String, Object> resp = new HashMap<>();
        try {
            String taskId = UUID.randomUUID().toString();
            photoScanService.updateAllColorCategoriesAsync(taskId);
            resp.put("message", "已异步触发颜色分类批量更新");
            resp.put("taskId", taskId);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("颜色分类更新失败", e);
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PostMapping("/update-exif-data")
    public ResponseEntity<Map<String, Object>> updateAllExifData() {
        Map<String, Object> resp = new HashMap<>();
        try {
            String taskId = java.util.UUID.randomUUID().toString();
            photoScanService.updateAllExifNumericFieldsAsync(taskId);
            resp.put("taskId", taskId);
            resp.put("message", "已异步触发 EXIF 字段批量更新");
            return ResponseEntity.accepted().body(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }


    /**
     * 手动触发扫描
     */
    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> triggerScan(@RequestParam(required = false) String path) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String target = (path == null || path.isEmpty()) ? null : path;
            photoScanService.scanDirectoryAsync(target);
            resp.put("message", "扫描任务已异步启动");
            resp.put("path", target);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "扫描失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 强制重新扫描（重新处理所有图片，重建缩略图、人脸、标签）
     */
    @PostMapping("/scan/force")
    public ResponseEntity<Map<String, Object>> triggerForceScan(@RequestParam(required = false) String path) {
        Map<String, Object> resp = new HashMap<>();
        try {
            String target = (path == null || path.isEmpty()) ? null : path;
            photoScanService.rescanDirectoryAsync(target);
            resp.put("message", "强制扫描任务已异步启动");
            resp.put("path", target);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "强制扫描失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 更新筛选选项
     */
    @PostMapping("/filter-options/update")
    public ResponseEntity<Map<String, Object>> updateFilterOptions() {
        Map<String, Object> resp = new HashMap<>();
        try {
            filterOptionService.updateAllFilterOptions();
            resp.put("message", "筛选选项更新完成");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("筛选选项更新失败", e);
            resp.put("error", e.getMessage() != null ? e.getMessage() : "更新失败");
            resp.put("stackTrace", e.getStackTrace().toString());
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/debug/photo-data")
    public ResponseEntity<Map<String, Object>> debugPhotoData() {
        Map<String, Object> resp = new HashMap<>();
        try {
            // 查询镜头数据
            var lensData = photoRepository.findDistinctLensModels();
            resp.put("lensModels", lensData);

            // 查询相机数据
            var cameraData = photoRepository.findDistinctCameraModels();
            resp.put("cameraModels", cameraData);

            // 查询一些示例照片的镜头信息
            var samplePhotos = photoRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 5));
            List<Map<String, Object>> photoDetails = new ArrayList<>();
            for (var photo : samplePhotos.getContent()) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("id", photo.getId());
                detail.put("filename", photo.getFilename());
                detail.put("cameraModel", photo.getCameraModel());
                detail.put("lensModel", photo.getLensModel());
                detail.put("focalLength", photo.getFocalLength());
                detail.put("aperture", photo.getAperture());
                detail.put("shutterSpeed", photo.getShutterSpeed());
                detail.put("iso", photo.getIso());
                photoDetails.add(detail);
            }
            resp.put("samplePhotos", photoDetails);

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("查询照片数据失败", e);
            resp.put("error", e.getMessage() != null ? e.getMessage() : "查询失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 获取扫描状态
     */
    @GetMapping("/scan/status")
    public ResponseEntity<Map<String, Object>> getScanStatus() {
        return ResponseEntity.ok(photoScanService.getScanStatus());
    }

    /**
     * 查询后台异步任务状态（包含日志）
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@PathVariable String taskId) {
        try {
            Map<String, Object> resp = photoScanService.getTaskStatus(taskId);
            if (resp.get("found") != null && !(Boolean) resp.get("found")) {
                return ResponseEntity.status(404).body(resp);
            }
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", e.getMessage());
            return ResponseEntity.status(500).body(err);
        }
    }

    /**
     * 分析未扫描的文件
     */
    @GetMapping("/scan/analyze-unscanned")
    public ResponseEntity<Map<String, Object>> analyzeUnscannedFiles() {
        return ResponseEntity.ok(photoScanService.analyzeUnscannedFiles());
    }

    /**
     * 重建单张图片的人脸数据
     */
    @PostMapping("/photos/{id}/rescan-faces")
    public ResponseEntity<Map<String, Object>> rescanFaces(@PathVariable Long id) {
        return ResponseEntity.ok(photoScanService.rescanFacesForPhoto(id));
    }

    /**
     * 全量回填图片哈希（SHA-256）
     */
    @PostMapping("/photos/hash-migrate")
    public ResponseEntity<Map<String, Object>> migrateHashes() {
        Map<String, Object> resp = new HashMap<>();
        try {
            photoScanService.backfillHashesAsync();
            resp.put("message", "哈希回填任务已异步启动");
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "哈希回填失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 清理重复的人脸记录
     */
    @PostMapping("/cleanup/duplicate-faces")
    public ResponseEntity<Map<String, Object>> cleanupDuplicateFaces() {
        Map<String, Object> resp = new HashMap<>();
        try {
            Map<String, Object> result = dataCleanupService.cleanupDuplicateFaces();
            resp.putAll(result);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "清理重复人脸记录失败");
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 初始化管理员账户（仅用于开发环境）
     */
    @PostMapping("/init-admin")
    public ResponseEntity<Map<String, Object>> initAdmin() {
        Map<String, Object> result = new HashMap<>();

        // 检查是否已存在admin用户
        if (adminUserRepository.existsByUsername("admin")) {
            result.put("message", "管理员账户已存在");
            AdminUser admin = adminUserRepository.findByUsername("admin").orElse(null);
            if (admin != null) {
                result.put("username", admin.getUsername());
                result.put("enabled", admin.getEnabled());
            }
        } else {
            // 创建新管理员
            AdminUser admin = new AdminUser();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole("ADMIN");
            admin.setEnabled(true);
            admin.setLoginAttempts(0);
            admin.setLockedUntil(null);
            adminUserRepository.save(admin);
            result.put("message", "管理员账户创建成功");
            result.put("username", "admin");
            result.put("password", "admin123");
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 修改管理员密码
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();

        String username = request.get("username");
        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");

        if (username == null || oldPassword == null || newPassword == null) {
            result.put("error", "缺少必要参数");
            return ResponseEntity.badRequest().body(result);
        }

        // 验证新密码强度
        if (newPassword.length() < 6) {
            result.put("error", "新密码长度不能少于6位");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            AdminUser user = adminUserRepository.findByUsername(username).orElse(null);
            if (user == null) {
                result.put("error", "用户不存在");
                return ResponseEntity.badRequest().body(result);
            }

            // 验证旧密码
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                result.put("error", "旧密码错误");
                return ResponseEntity.badRequest().body(result);
            }

            // 更新密码
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setLoginAttempts(0); // 重置登录失败次数
            user.setLockedUntil(null); // 解除锁定
            adminUserRepository.save(user);

            result.put("message", "密码修改成功");
            result.put("success", true);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("error", "密码修改失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 更改用户名
     */
    @PostMapping("/change-username")
    public ResponseEntity<Map<String, Object>> changeUsername(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();

        String currentUsername = request.get("currentUsername");
        String newUsername = request.get("newUsername");
        String password = request.get("password");

        if (currentUsername == null || newUsername == null || password == null) {
            result.put("error", "缺少必要参数");
            return ResponseEntity.badRequest().body(result);
        }

        // 验证新用户名格式（前端也应验证，这里是后端双重验证）
        if (newUsername.length() < 3 || newUsername.length() > 50) {
            result.put("error", "用户名长度必须在3-50个字符之间");
            return ResponseEntity.badRequest().body(result);
        }

        if (!newUsername.matches("^[a-zA-Z0-9_-]+$")) {
            result.put("error", "用户名只能包含字母、数字、下划线和连字符");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            // 调用服务层更改用户名
            LoginResponse loginResponse = authService.changeUsername(currentUsername, newUsername, password);

            result.put("message", "用户名修改成功");
            result.put("success", true);
            result.put("newUsername", newUsername);
            result.put("token", loginResponse.getToken()); // 返回新token供前端使用

            return ResponseEntity.ok(result);

        } catch (RuntimeException e) {
            result.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("error", "用户名修改失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 测试密码验证
     */
    @PostMapping("/test-password")
    public ResponseEntity<Map<String, Object>> testPassword(@RequestParam String password) {
        Map<String, Object> result = new HashMap<>();
        
        AdminUser admin = adminUserRepository.findByUsername("admin").orElse(null);
        if (admin == null) {
            result.put("error", "管理员账户不存在");
            return ResponseEntity.ok(result);
        }
        
        boolean matches = passwordEncoder.matches(password, admin.getPassword());
        result.put("password", password);
        result.put("storedHash", admin.getPassword());
        result.put("matches", matches);
        
        // 生成新的hash用于参考
        String newHash = passwordEncoder.encode(password);
        result.put("newHash", newHash);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 更新相册标签
     */
    @PutMapping("/albums/{id}/tags")
    public ResponseEntity<AlbumDTO> updateAlbumTags(
            @PathVariable Long id,
            @RequestBody List<Long> tagIds) {
        // 实现标签更新逻辑
        return ResponseEntity.ok().build();
    }

    /**
     * 重新生成相册封面
     */
    @PostMapping("/albums/{id}/regenerate-cover")
    public ResponseEntity<String> regenerateCover(@PathVariable Long id) {
        // 实现封面重新生成逻辑
        return ResponseEntity.ok("封面重新生成中");
    }

    /**
     * 重新分析所有相册的氛围信息
     */
    @PostMapping("/atmosphere/reanalyze-all")
    public ResponseEntity<Map<String, Object>> reanalyzeAllAtmosphere() {
        Map<String, Object> resp = new HashMap<>();
        try {
            photoScanService.reanalyzeAllAtmosphere();
            resp.put("message", "氛围信息重新分析任务已异步启动");
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "氛围分析失败");
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 重新分析指定相册的氛围信息
     */
    @PostMapping("/albums/{id}/reanalyze-atmosphere")
    public ResponseEntity<Map<String, Object>> reanalyzeAlbumAtmosphere(@PathVariable Long id) {
        Map<String, Object> resp = new HashMap<>();
        try {
            photoScanService.reanalyzeAlbumAtmosphere(id);
            resp.put("message", "相册氛围信息重新分析完成");
            resp.put("albumId", id);
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "氛围分析失败");
            resp.put("albumId", id);
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 设置相册特效
     */
    @PutMapping("/albums/{id}/atmosphere-effects")
    public ResponseEntity<Map<String, Object>> setAlbumAtmosphereEffects(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> resp = new HashMap<>();
        try {
            // 从请求体中获取特效配置
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> effects = (List<Map<String, Object>>) request.get("effects");

            if (effects == null) {
                resp.put("error", "缺少特效配置参数");
                return ResponseEntity.badRequest().body(resp);
            }

            // 调用服务设置特效
            Map<String, Object> result = photoScanService.setAlbumAtmosphereEffects(id, effects);
            resp.putAll(result);
            resp.put("success", true);
            resp.put("albumId", id);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "设置特效失败");
            resp.put("albumId", id);
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 获取相册当前特效配置
     */
    @GetMapping("/albums/{id}/atmosphere-effects")
    public ResponseEntity<Map<String, Object>> getAlbumAtmosphereEffects(@PathVariable Long id) {
        Map<String, Object> resp = new HashMap<>();
        try {
            Map<String, Object> result = photoScanService.getAlbumAtmosphereEffects(id);
            resp.putAll(result);
            resp.put("success", true);
            resp.put("albumId", id);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "获取特效配置失败");
            resp.put("albumId", id);
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 批量操作
     */
    @PostMapping("/photos/batch")
    public ResponseEntity<String> batchOperation(
            @RequestParam String operation,
            @RequestBody List<Long> photoIds) {
        // 实现批量操作逻辑
        return ResponseEntity.ok("批量操作完成");
    }

    /**
     * 清理所有数据（只保留账号数据）
     * 危险操作：会删除所有照片、相册、标签、人脸、人物等数据
     */
    @PostMapping("/cleanup/all")
    public ResponseEntity<Map<String, Object>> cleanupAllData() {
        Map<String, Object> resp = new HashMap<>();
        try {
            dataCleanupService.cleanupAllData();
            resp.put("message", "数据清理完成，已删除所有照片、相册、标签、人脸、人物数据，账号数据已保留");
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "数据清理失败");
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 清空所有缩略图（删除缩略图文件并清空数据库路径）
     * 用于重新生成三级缩略图系统
     */
    @PostMapping("/thumbnails/clear")
    public ResponseEntity<Map<String, Object>> clearAllThumbnails() {
        Map<String, Object> resp = new HashMap<>();
        try {
            Map<String, Object> result = photoScanService.clearAllThumbnails();
            resp.putAll(result);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "清空缩略图失败");
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 清空所有人脸数据
     */
    @PostMapping("/faces/clear")
    public ResponseEntity<Map<String, Object>> clearAllFaces() {
        Map<String, Object> resp = new HashMap<>();
        try {
            Map<String, Object> result = photoScanService.clearAllFaces();
            resp.putAll(result);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "清空人脸数据失败");
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 清空所有智能标签
     */
    @PostMapping("/smart-tags/clear")
    public ResponseEntity<Map<String, Object>> clearAllSmartTags() {
        Map<String, Object> resp = new HashMap<>();
        try {
            Map<String, Object> result = photoScanService.clearAllSmartTags();
            resp.putAll(result);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "清空智能标签失败");
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 清理已删除文件的残留数据
     * 删除不存在图片文件的照片记录、人脸数据、标签关联，以及空相册
     */
    @PostMapping("/cleanup/orphaned")
    public ResponseEntity<Map<String, Object>> cleanupOrphanedData() {
        Map<String, Object> resp = new HashMap<>();
        try {
            Map<String, Object> result = photoScanService.cleanupOrphanedData();
            resp.putAll(result);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "清理残留数据失败");
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 更新相册时间字段
     * 重新计算所有相册的拍摄时间和相册名时间
     */
    @PostMapping("/albums/update-times")
    public ResponseEntity<Map<String, Object>> updateAlbumTimes() {
        Map<String, Object> resp = new HashMap<>();
        try {
            albumService.updateAlbumTimeFields();
            resp.put("message", "相册时间字段更新完成");
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "更新相册时间失败");
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 更新所有照片的时间信息
     * 重新从EXIF信息中提取拍摄时间（异步执行）
     */
    @PostMapping("/photos/update-times")
    public ResponseEntity<Map<String, Object>> updateAllPhotoTimes() {
        Map<String, Object> resp = new HashMap<>();
        try {
            photoScanService.updateAllPhotoTimesAsync();
            resp.put("message", "照片时间更新任务已异步启动，请稍后查看日志了解进度");
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "启动照片时间更新任务失败");
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 同步更新所有照片的时间信息
     * 重新从EXIF信息中提取拍摄时间（同步执行，耗时较长）
     */
    @PostMapping("/photos/update-times-sync")
    public ResponseEntity<Map<String, Object>> updateAllPhotoTimesSync() {
        Map<String, Object> resp = new HashMap<>();
        try {
            Map<String, Object> result = photoScanService.updateAllPhotoTimes();
            resp.putAll(result);
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", e.getMessage() != null ? e.getMessage() : "更新照片时间失败");
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 执行ONNX Runtime完整诊断
     */
    @PostMapping("/diagnostics/onnx")
    public ResponseEntity<Map<String, Object>> runONNXDiagnostics() {
        Map<String, Object> resp = new HashMap<>();
        try {
            onnxDiagnosticUtil.performFullDiagnostic();
            resp.put("message", "ONNX Runtime诊断完成，请查看日志输出");
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", "诊断执行失败: " + e.getMessage());
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

}
