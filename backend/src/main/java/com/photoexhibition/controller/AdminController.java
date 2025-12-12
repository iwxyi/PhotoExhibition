package com.photoexhibition.controller;

import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.entity.AdminUser;
import com.photoexhibition.repository.AdminUserRepository;
import com.photoexhibition.service.DataCleanupService;
import com.photoexhibition.service.PhotoScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final PhotoScanService photoScanService;
    private final AdminUserRepository adminUserRepository;
    private final DataCleanupService dataCleanupService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
     * 强制重新扫描（忽略更新时间，重建缩略图、人脸、标签）
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
            adminUserRepository.save(admin);
            result.put("message", "管理员账户创建成功");
            result.put("username", "admin");
            result.put("password", "admin123");
        }
        
        return ResponseEntity.ok(result);
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
}
