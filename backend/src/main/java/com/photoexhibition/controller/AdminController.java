package com.photoexhibition.controller;

import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.LoginResponse;
import com.photoexhibition.entity.AdminUser;
import com.photoexhibition.entity.OperationType;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.repository.AdminUserRepository;
import com.photoexhibition.service.AlbumService;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.DataCleanupService;
import com.photoexhibition.service.AiSearchService;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.PhotoAIScoringRepository;
import com.photoexhibition.service.FilterOptionService;
import com.photoexhibition.service.PhotoManageService;
import com.photoexhibition.service.PhotoScanService;
import com.photoexhibition.service.PhotoAIScoringService;
import com.photoexhibition.service.SimilarPhotoSearchService;
import com.photoexhibition.service.BackgroundRemovalService;
import com.photoexhibition.service.OnnxConfigurationException;
import com.photoexhibition.service.OperationLogService;
import com.photoexhibition.service.ScanTaskService;
import com.photoexhibition.service.UserPathService;
import com.photoexhibition.util.ONNXDiagnosticUtil;
import java.lang.NoClassDefFoundError;
import java.lang.UnsatisfiedLinkError;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.File;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
@RestControllerAdvice
public class AdminController {

    private static final Pattern EMBEDDED_PATH_PATTERN =
        Pattern.compile("(storage://[^\\s,;]+|[A-Za-z]:\\\\[^\\s,;]+|/(?:[^\\s,;])+)");

    private final PhotoScanService photoScanService;
    private final PhotoManageService photoManageService;
    private final AdminUserRepository adminUserRepository;
    private final DataCleanupService dataCleanupService;
    private final AlbumService albumService;
    private final FilterOptionService filterOptionService;
    private final PhotoRepository photoRepository;
    private final AuthService authService;
    private final ONNXDiagnosticUtil onnxDiagnosticUtil;
    private final PhotoAIScoringService aiScoringService;
    private final PhotoAIScoringRepository aiScoringRepository;
    private final SimilarPhotoSearchService similarPhotoSearchService;
    private final BackgroundRemovalService backgroundRemovalService;
    private final AiSearchService aiSearchService;
    private final ScanTaskService scanTaskService;
    private final OperationLogService operationLogService;
    private final UserPathService userPathService;

    /**
     * 全局异常处理器 - 处理各种异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception e) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("success", false);
        resp.put("details", sanitizeErrorMessage(e.getMessage(), "系统异常"));
        resp.put("exceptionType", e.getClass().getSimpleName());

        // 处理ONNX相关错误
        if (e instanceof com.photoexhibition.service.OnnxConfigurationException) {
            resp.put("error", "ONNX环境配置错误");
            if (e.getMessage() != null && e.getMessage().contains("ONNX")) {
                resp.put("diagnostic", "请根据以下诊断信息配置ONNX运行时：\n" + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            }
        } else if (e.getCause() instanceof NoClassDefFoundError || e.getCause() instanceof UnsatisfiedLinkError) {
            resp.put("error", "ONNX运行时库缺失");
            resp.put("message", "AI评分正在使用基础规则评分模式（无AI增强）");
            resp.put("details", sanitizeErrorMessage(e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), "ONNX运行时库缺失"));
            resp.put("diagnostic", "当前正在使用降级的评分算法。如果需要AI增强功能，请检查ONNX运行时库是否正确安装。");
            // 对于ONNX错误，我们返回成功状态，因为降级评分是正常行为
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } else if (e instanceof java.io.IOException) {
            resp.put("error", "I/O错误");
        } else {
            resp.put("error", "系统错误");
        }

        return ResponseEntity.status(500).body(resp);
    }
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // 任务状态跟踪（用于后台异步任务）
    private static class TaskStatus {
        public String taskId;
        public String status;
        public boolean complete;
        public LocalDateTime startTime;
        public LocalDateTime endTime;
        public List<String> logs;
        public int current;
        public int total;

        public TaskStatus(String taskId, String status, boolean complete, LocalDateTime startTime, LocalDateTime endTime, List<String> logs) {
            this.taskId = taskId;
            this.status = status;
            this.complete = complete;
            this.startTime = startTime;
            this.endTime = endTime;
            this.logs = logs;
            this.current = 0;
            this.total = 0;
        }
    }

    private final ConcurrentHashMap<String, TaskStatus> tasks = new ConcurrentHashMap<>();
    private final ThreadLocal<String> currentTaskId = new ThreadLocal<>();
    private final AtomicInteger scanCurrent = new AtomicInteger(0);

    /**
     * 单独触发更新所有照片的 EXIF 字段（用于已存在图片的后处理）
     * 包括数值字段（快门秒数、焦距mm、光圈值）和字符串字段（ISO、镜头型号）
     */
    @PostMapping("/recalculate-photo-colors")
    public ResponseEntity<Map<String, Object>> recalculatePhotoColors(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            String taskId = UUID.randomUUID().toString();
            photoScanService.recalculateAllPhotoColorsAsync(taskId);
            resp.put("message", "已异步触发照片颜色重新计算");
            resp.put("taskId", taskId);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("照片颜色重新计算失败", e);
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "照片颜色重新计算失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PostMapping("/update-color-categories")
    public ResponseEntity<Map<String, Object>> updateColorCategories(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            String taskId = UUID.randomUUID().toString();
            photoScanService.updateAllColorCategoriesAsync(taskId);
            resp.put("message", "已异步触发颜色分类批量更新");
            resp.put("taskId", taskId);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("颜色分类更新失败", e);
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "颜色分类更新失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @PostMapping("/update-exif-data")
    public ResponseEntity<Map<String, Object>> updateAllExifData(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            String taskId = java.util.UUID.randomUUID().toString();
            photoScanService.updateAllExifNumericFieldsAsync(taskId);
            resp.put("taskId", taskId);
            resp.put("message", "已异步触发 EXIF 字段批量更新");
            return ResponseEntity.accepted().body(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "EXIF 更新失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }


    /**
     * 手动触发扫描
     */
    @PostMapping("/scan")
    public ResponseEntity<Map<String, Object>> triggerScan(@RequestHeader("Authorization") String authorization,
                                                           HttpServletRequest request,
                                                           @RequestParam(required = false) String path,
                                                           @RequestParam(required = false) Long storageProviderId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            resp.putAll(scanTaskService.enqueueScan(user, path, false, storageProviderId));
            Map<String, Object> operationDetails = new HashMap<>();
            operationDetails.put("path", path);
            operationDetails.put("mode", "INCREMENTAL_SCAN");
            operationDetails.put("storageProviderId", storageProviderId);
            operationLogService.log(
                user,
                OperationType.SCAN_START,
                "SCAN_TASK",
                (Long) resp.get("id"),
                path,
                operationDetails,
                request.getRemoteAddr()
            );
            return ResponseEntity.accepted().body(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "扫描失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 强制重新扫描（重新处理所有图片，重建缩略图、人脸、标签）
     */
    @PostMapping("/scan/force")
    public ResponseEntity<Map<String, Object>> triggerForceScan(@RequestHeader("Authorization") String authorization,
                                                                HttpServletRequest request,
                                                                @RequestParam(required = false) String path,
                                                                @RequestParam(required = false) Long storageProviderId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            resp.putAll(scanTaskService.enqueueScan(user, path, true, storageProviderId));
            Map<String, Object> operationDetails = new HashMap<>();
            operationDetails.put("path", path);
            operationDetails.put("mode", "FULL_SCAN");
            operationDetails.put("storageProviderId", storageProviderId);
            operationLogService.log(
                user,
                OperationType.SCAN_START,
                "SCAN_TASK",
                (Long) resp.get("id"),
                path,
                operationDetails,
                request.getRemoteAddr()
            );
            return ResponseEntity.accepted().body(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "强制扫描失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "更新失败"));
            resp.put("errorType", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 异步执行批量AI重新评分任务（强制覆盖现有评分）
     */
    @Async
    /**
     * 检查Spring应用上下文是否仍然活跃
     */
    private boolean isApplicationContextActive() {
        try {
            // 尝试访问一个Spring管理的Bean来检查上下文状态
            return photoRepository != null && photoRepository.count() >= 0;
        } catch (Exception e) {
            // 如果出现异常，说明上下文可能已经关闭
            return false;
        }
    }

    @Async
    public void processAllAIScoringsAsync(String taskId, List<Long> photoIds) {
        try {
            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();

            log.info("开始更新 {} 张照片的AI评分（强制重新评分，覆盖现有评分）", photoIds.size());

            for (int i = 0; i < photoIds.size(); i++) {
                Long photoId = photoIds.get(i);

                try {
                    // 检查Spring上下文是否仍然活跃
                    if (!isApplicationContextActive()) {
                        log.warn("Spring应用上下文已关闭，停止AI分析任务");
                        errors.add("任务被中断：Spring应用上下文已关闭");
                        break;
                    }

                    var photo = photoRepository.findById(photoId).orElse(null);
                    if (photo != null) {
                        // 检查照片文件是否存在
                        var imagePath = userPathService.tryResolveLocalStoredPhotoPath(photo.getOriginalPath());
                        if (imagePath.isPresent() && imagePath.get().toFile().exists()) {
                            aiScoringService.rescorePhoto(photo); // 强制重新评分，覆盖现有评分
                            successCount++;
                        } else {
                            log.warn("照片文件不存在或无法映射到本地路径: {}",
                                    userPathService.toDisplayPath(photo.getOriginalPath(), true));
                            failCount++;
                        }
                    } else {
                        failCount++;
                        errors.add("照片 " + photoId + ": 不存在");
                    }
                } catch (Exception e) {
                    // 检查是否是上下文关闭相关的错误
                    if (e.getMessage() != null && e.getMessage().contains("has been closed")) {
                        log.warn("检测到Spring上下文关闭，停止AI分析任务");
                        errors.add("任务被中断：Spring应用上下文已关闭");
                        break;
                    }
                    failCount++;
                    String errorMsg;
                    // 检查是否是ONNX配置异常
                    if (e instanceof com.photoexhibition.service.OnnxConfigurationException) {
                        errorMsg = "照片 " + photoId + ": ONNX环境配置错误 - " + sanitizeErrorMessage(e.getMessage(), "系统异常");
                        log.warn("AI重新评分失败 - ONNX配置错误: {}", sanitizeErrorMessage(e.getMessage(), "系统异常"));
                    } else {
                        errorMsg = "照片 " + photoId + ": " + sanitizeErrorMessage(e.getMessage(), "系统异常");
                        log.warn("AI重新评分失败 - {}", errorMsg, e);
                    }
                    errors.add(errorMsg);
                }

                // 每处理100张照片记录一次进度
                if ((i + 1) % 100 == 0) {
                    log.info("AI评分进度: {}/{}", i + 1, photoIds.size());
                }
            }

            log.info("AI重新评分完成 - 成功: {}, 失败: {}", successCount, failCount);
            if (!errors.isEmpty()) {
                if (errors.size() <= 10) {
                    log.warn("评分失败详情: {}", String.join("; ", errors));
                } else {
                    log.warn("评分失败数量: {}, 示例错误: {}", errors.size(), errors.get(0));
                }
            }

            // 更新任务状态
            TaskStatus task = tasks.get(taskId);
            if (task != null) {
                task.status = "COMPLETED";
                task.complete = true;
                task.endTime = LocalDateTime.now();
                task.logs.add(String.format("批量AI重新评分完成。成功: %d，失败: %d", successCount, failCount));
                if (!errors.isEmpty()) {
                    task.logs.add("失败详情: " + String.join("; ", errors));
                }
            }
        } catch (Exception e) {
            log.error("批量AI重新评分任务异常: {}", e.getMessage(), e);
            TaskStatus task = tasks.get(taskId);
            if (task != null) {
                task.status = "FAILED";
                task.complete = true;
                task.endTime = LocalDateTime.now();
                task.logs.add("批量AI重新评分任务异常: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            }
        } finally {
            currentTaskId.remove();
        }
    }

    /**
     * 清空所有照片的AI分析记录
     */
    @PostMapping("/ai-analysis/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllAIAnalyses(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            log.info("开始清空所有照片的AI分析记录");

            // 删除所有AI分析记录
            long beforeCount = aiScoringRepository.count();
            aiScoringRepository.deleteAll();
            long afterCount = aiScoringRepository.count();
            long deletedCount = beforeCount - afterCount;
            log.info("成功删除 {} 条AI评分记录", deletedCount);

            resp.put("success", true);
            resp.put("message", "已清空所有AI评分记录，共删除 " + deletedCount + " 条记录");
            resp.put("deletedCount", deletedCount);

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("清空AI评分记录失败", e);
            resp.put("success", false);
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "清空AI评分记录失败"));
            resp.put("details", "清空AI评分记录失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 更新所有照片的AI分析（强制重新分析，覆盖现有分析）
     */
    @PostMapping("/ai-analysis/update-all")
    public ResponseEntity<Map<String, Object>> updateAllAIAnalyses(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long scopedUserId = resolveScopedUserId(user);
            log.info("开始更新所有照片的AI分析（强制重新分析，覆盖现有分析）");

            // 获取所有照片ID（分页查询，避免一次性加载所有实体）
            List<Long> photoIds = new ArrayList<>();
            int pageSize = 1000; // 每次查询1000个ID
            int page = 0;
            Pageable pageable;
            Page<Photo> photoPage;

            do {
                pageable = PageRequest.of(page, pageSize);
                photoPage = scopedUserId == null
                    ? photoRepository.findAll(pageable)
                    : photoRepository.findByUserId(scopedUserId, pageable);
                photoPage.getContent().forEach(photo -> photoIds.add(photo.getId()));
                page++;
            } while (photoPage.hasNext());

            resp.put("totalPhotos", photoIds.size());
            resp.put("message", "AI评分更新开始处理（强制重新评分，覆盖现有评分）");

            // 启动异步任务
            String taskId = UUID.randomUUID().toString();
            currentTaskId.set(taskId);
            tasks.put(taskId, new TaskStatus(taskId, "PROCESSING", false, LocalDateTime.now(), null, new ArrayList<>()));

            // 使用Spring的异步方法执行任务
            processAllAIScoringsAsync(taskId, photoIds);

            resp.put("taskId", taskId);
            resp.put("status", "processing");
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("AI评分更新启动失败", e);
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "更新失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 测试AI评分功能（对单个照片进行评分）
     */
    @PostMapping("/ai-scoring/test/{photoId}")
    public ResponseEntity<Map<String, Object>> testAIScoring(@RequestHeader("Authorization") String authorization,
                                                             @PathVariable Long photoId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long scopedUserId = resolveScopedUserId(user);
            var photo = scopedUserId == null
                ? photoRepository.findById(photoId).orElse(null)
                : photoRepository.findByIdAndUserId(photoId, scopedUserId).orElse(null);
            if (photo == null) {
                resp.put("error", "照片不存在: " + photoId);
                return ResponseEntity.status(404).body(resp);
            }

            log.info("测试AI评分功能 - 照片ID: {}", photoId);

            // 执行AI评分
            var scoring = aiScoringService.scorePhoto(photo);

            if (scoring != null) {
                resp.put("success", true);
                resp.put("photoId", photoId);
                resp.put("overallScore", scoring.getOverallScore());
                resp.put("technicalScore", scoring.getTechnicalScore());
                resp.put("compositionScore", scoring.getCompositionScore());
                resp.put("appealScore", scoring.getAppealScore());
                resp.put("processingTimeMs", scoring.getProcessingTimeMs());
                resp.put("message", "AI评分测试成功");
            } else {
                resp.put("success", false);
                resp.put("message", "AI评分返回null，可能评分失败");
            }

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("AI评分测试失败 - 照片ID: {}", photoId, e);

            // 尝试获取详细的错误信息
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = "AI评分测试失败";
            }

            resp.put("success", false);
            resp.put("error", errorMessage);
            resp.put("details", errorMessage);
            resp.put("exceptionType", e.getClass().getSimpleName());

            // 如果是IOException（我们自定义的ONNX错误），添加诊断信息
            if (e instanceof java.io.IOException && e.getMessage() != null && e.getMessage().contains("ONNX")) {
                resp.put("diagnostic", "请根据以下诊断信息配置ONNX运行时：\n" + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            }

            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/debug/numeric-fields")
    public ResponseEntity<Map<String, Object>> debugNumericFields(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long scopedUserId = resolveScopedUserId(user);
            // 查询数值字段统计
            resp.put("totalPhotos", scopedUserId == null ? photoRepository.countAllPhotos() : photoRepository.countByUserId(scopedUserId));
            resp.put("focalLengthCount", scopedUserId == null ? photoRepository.countPhotosWithFocalLength() : photoRepository.countPhotosWithFocalLengthByUserId(scopedUserId));
            resp.put("apertureCount", scopedUserId == null ? photoRepository.countPhotosWithAperture() : photoRepository.countPhotosWithApertureByUserId(scopedUserId));
            resp.put("shutterSpeedCount", scopedUserId == null ? photoRepository.countPhotosWithShutterSpeed() : photoRepository.countPhotosWithShutterSpeedByUserId(scopedUserId));
            resp.put("isoCount", scopedUserId == null ? photoRepository.countPhotosWithIso() : photoRepository.countPhotosWithIsoByUserId(scopedUserId));

            // 测试范围查询 - 暂时注释掉不存在的方法
            // Double[] focalRange = photoRepository.findFocalLengthRange();
            // Double[] apertureRange = photoRepository.findApertureRange();
            // Double[] shutterRange = photoRepository.findShutterSpeedRange();
            // Integer[] isoRange = photoRepository.findIsoRange();

            // resp.put("focalRange", focalRange);
            // resp.put("apertureRange", apertureRange);
            // resp.put("shutterRange", shutterRange);
            // resp.put("isoRange", isoRange);

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "执行失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    @GetMapping("/debug/photo-data")
    public ResponseEntity<Map<String, Object>> debugPhotoData(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long scopedUserId = resolveScopedUserId(user);
            // 查询镜头数据
            var lensData = scopedUserId == null
                ? photoRepository.findDistinctLensModels()
                : photoRepository.findDistinctLensModelsByUserId(scopedUserId);
            resp.put("lensModels", lensData);

            // 查询相机数据
            var cameraData = scopedUserId == null
                ? photoRepository.findDistinctCameraModels()
                : photoRepository.findDistinctCameraModelsByUserId(scopedUserId);
            resp.put("cameraModels", cameraData);

            // 查询一些示例照片的镜头信息
            var samplePhotos = scopedUserId == null
                ? photoRepository.findAll(org.springframework.data.domain.PageRequest.of(0, 5))
                : photoRepository.findByUserId(scopedUserId, org.springframework.data.domain.PageRequest.of(0, 5));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "查询失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 获取扫描状态
     */
    @GetMapping("/scan/status")
    public ResponseEntity<Map<String, Object>> getScanStatus(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> status = new HashMap<>(photoScanService.getScanStatus());
        UserAccount user = requireCurrentUser(authorization);
        status.putAll(scanTaskService.getStatusSummary(user));
        status.put("scanning", Boolean.TRUE.equals(status.get("scanning")) || Boolean.TRUE.equals(status.get("queueActive")));
        return ResponseEntity.ok(status);
    }

    @GetMapping("/scan/tasks")
    public ResponseEntity<List<Map<String, Object>>> listScanTasks(@RequestHeader("Authorization") String authorization) {
        UserAccount user = requireCurrentUser(authorization);
        return ResponseEntity.ok(scanTaskService.listTasks(user));
    }

    @GetMapping("/scan/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> getScanTask(@RequestHeader("Authorization") String authorization,
                                                           @PathVariable Long taskId) {
        UserAccount user = requireCurrentUser(authorization);
        return ResponseEntity.ok(scanTaskService.getTask(user, taskId));
    }

    @PostMapping("/scan/tasks/{taskId}/retry")
    public ResponseEntity<Map<String, Object>> retryScanTask(@RequestHeader("Authorization") String authorization,
                                                             HttpServletRequest request,
                                                             @PathVariable Long taskId) {
        UserAccount user = requireCurrentUser(authorization);
        Map<String, Object> resp = scanTaskService.retryTask(user, taskId);
        operationLogService.log(user, OperationType.SCAN_RESUME, "SCAN_TASK", taskId,
            (String) resp.get("rootPath"), Map.of("taskId", taskId, "action", "retry"), request.getRemoteAddr());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/scan/tasks/{taskId}/pause")
    public ResponseEntity<Map<String, Object>> pauseScanTask(@RequestHeader("Authorization") String authorization,
                                                             HttpServletRequest request,
                                                             @PathVariable Long taskId) {
        UserAccount user = requireCurrentUser(authorization);
        Map<String, Object> resp = scanTaskService.pauseTask(user, taskId);
        operationLogService.log(user, OperationType.UPDATE, "SCAN_TASK", taskId,
            (String) resp.get("rootPath"), Map.of("taskId", taskId, "action", "pause"), request.getRemoteAddr());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/scan/tasks/{taskId}/cancel")
    public ResponseEntity<Map<String, Object>> cancelScanTask(@RequestHeader("Authorization") String authorization,
                                                              HttpServletRequest request,
                                                              @PathVariable Long taskId) {
        UserAccount user = requireCurrentUser(authorization);
        Map<String, Object> resp = scanTaskService.cancelTask(user, taskId);
        operationLogService.log(user, OperationType.DELETE, "SCAN_TASK", taskId,
            (String) resp.get("rootPath"), Map.of("taskId", taskId, "action", "cancel"), request.getRemoteAddr());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/operation-logs")
    public ResponseEntity<List<Map<String, Object>>> listOperationLogs(@RequestHeader("Authorization") String authorization) {
        UserAccount user = requireCurrentUser(authorization);
        return ResponseEntity.ok(operationLogService.listRecentLogs(user));
    }

    @GetMapping("/login-records")
    public ResponseEntity<List<Map<String, Object>>> listLoginRecords(@RequestHeader("Authorization") String authorization) {
        UserAccount user = requireCurrentUser(authorization);
        return ResponseEntity.ok(authService.listRecentLoginRecords(user));
    }

    /**
     * 获取本次扫描跳过的文件详情列表
     */
    @GetMapping("/scan/skipped-files")
    public ResponseEntity<List<PhotoScanService.SkippedFileRecord>> getSkippedFiles() {
        return ResponseEntity.ok(photoScanService.getSkippedFileRecords());
    }

    /**
     * 查询后台异步任务状态（包含日志）
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> getTaskStatus(@RequestHeader("Authorization") String authorization,
                                                             @PathVariable String taskId) {
        try {
            requireSuperAdminUser(authorization);
            Map<String, Object> resp = photoScanService.getTaskStatus(taskId);
            if (resp.get("found") != null && !(Boolean) resp.get("found")) {
                return ResponseEntity.status(404).body(resp);
            }
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", sanitizeErrorMessage(e.getMessage(), "执行失败"));
            return ResponseEntity.status(500).body(err);
        }
    }

    /**
     * 停止任务
     */
    @PostMapping("/tasks/{taskId}/stop")
    public ResponseEntity<Map<String, Object>> stopTask(@RequestHeader("Authorization") String authorization,
                                                        @PathVariable String taskId) {
        try {
            requireSuperAdminUser(authorization);
            Map<String, Object> resp = photoScanService.stopTask(taskId);
            if (resp.get("found") != null && !(Boolean) resp.get("found")) {
                return ResponseEntity.status(404).body(resp);
            }
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", sanitizeErrorMessage(e.getMessage(), "执行失败"));
            return ResponseEntity.status(500).body(err);
        }
    }

    /**
     * 分析未扫描的文件
     */
    @GetMapping("/scan/analyze-unscanned")
    public ResponseEntity<Map<String, Object>> analyzeUnscannedFiles(@RequestHeader("Authorization") String authorization) {
        requireSuperAdminUser(authorization);
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
     * 仅重算单张图片已有的人脸 embedding，保留人物绑定
     */
    @PostMapping("/photos/{id}/rebuild-face-embeddings")
    public ResponseEntity<Map<String, Object>> rebuildFaceEmbeddings(@PathVariable Long id) {
        return ResponseEntity.ok(photoScanService.rebuildFaceEmbeddingsForPhoto(id));
    }

    private UserAccount requireCurrentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("未授权，请先登录");
        }
        return authService.getCurrentUserEntity(authorization.substring(7));
    }

    private UserAccount requireSuperAdminUser(String authorization) {
        UserAccount user = requireCurrentUser(authorization);
        if (user.getRole() != com.photoexhibition.entity.UserRole.SUPER_ADMIN) {
            throw new RuntimeException("仅超级管理员可执行此操作");
        }
        return user;
    }

    private Long resolveScopedUserId(UserAccount user) {
        return albumService.resolveScopedUserId(user);
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "哈希回填失败"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 清理重复的人脸记录
     */
    @PostMapping("/cleanup/duplicate-faces")
    public ResponseEntity<Map<String, Object>> cleanupDuplicateFaces(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            Map<String, Object> result = dataCleanupService.cleanupDuplicateFaces();
            resp.putAll(result);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "清理重复人脸记录失败"));
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
            authService.changePassword(username, oldPassword, newPassword);
            result.put("message", "密码修改成功");
            result.put("success", true);
            return ResponseEntity.ok(result);

        } catch (RuntimeException e) {
            result.put("error", sanitizeErrorMessage(e.getMessage(), "密码修改失败"));
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("error", "密码修改失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
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
            result.put("error", sanitizeErrorMessage(e.getMessage(), "用户名修改失败"));
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            result.put("error", "用户名修改失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "氛围分析失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "氛围分析失败"));
            resp.put("albumId", id);
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    // ==================== AI 分析管理接口 ====================

    /**
     * 重新分析所有照片（批量AI分析）
     */
    @PostMapping("/ai-analysis/rescore-all")
    public ResponseEntity<Map<String, Object>> rescoreAllPhotos(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long scopedUserId = resolveScopedUserId(user);
            // 启动异步任务重新分析所有照片
            String taskId = UUID.randomUUID().toString();
            resp.put("taskId", taskId);
            resp.put("message", "AI重新分析任务已启动，请通过任务状态接口查询进度");
            resp.put("success", true);

            // 异步执行批量评分
            new Thread(() -> {
                try {
                    performBatchAIScoring(taskId, scopedUserId);
                } catch (Exception e) {
                    log.error("批量AI评分任务失败", e);
                }
            }).start();

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "AI评分任务启动失败"));
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 重新评分指定相册的所有图片
     */
    @PostMapping("/albums/{albumId}/ai-scoring/rescore")
    public ResponseEntity<Map<String, Object>> rescoreAlbumPhotos(@RequestHeader("Authorization") String authorization,
                                                                  @PathVariable Long albumId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long scopedUserId = resolveScopedUserId(user);
            albumService.getAlbumById(albumId, scopedUserId);
            // 启动异步任务重新评分指定相册的图片
            String taskId = UUID.randomUUID().toString();
            resp.put("taskId", taskId);
            resp.put("albumId", albumId);
            resp.put("message", "相册AI重新评分任务已启动，请通过任务状态接口查询进度");
            resp.put("success", true);

            // 异步执行相册评分
            new Thread(() -> {
                try {
                    performAlbumAIScoring(taskId, albumId, scopedUserId);
                } catch (Exception e) {
                    log.error("相册AI评分任务失败", e);
                }
            }).start();

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "相册AI评分任务启动失败"));
            resp.put("albumId", albumId);
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 获取AI评分统计信息
     */
    @GetMapping("/ai-scoring/stats")
    public ResponseEntity<Map<String, Object>> getAIScoringStats(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            UserAccount user = requireCurrentUser(authorization);
            Long scopedUserId = resolveScopedUserId(user);
            // 获取评分统计信息
            Map<String, Object> stats = new HashMap<>();

            // 总图片数量
            long totalPhotos = scopedUserId == null ? photoRepository.count() : photoRepository.countByUserId(scopedUserId);

            // 已评分图片数量
            long scoredPhotos = scopedUserId == null
                ? aiScoringRepository.countCompletedScorings()
                : aiScoringRepository.countCompletedScoringsByUserId(scopedUserId);

            // 评分完成的数量（需要从AI评分表统计）
            // 这里简化处理，实际应该查询AI评分表

            stats.put("totalPhotos", totalPhotos);
            stats.put("scoredPhotos", scoredPhotos);
            stats.put("scoringEnabled", true); // 从配置中读取

            resp.put("stats", stats);
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取AI评分统计失败"));
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 执行批量AI评分
     */
    private void performBatchAIScoring(String taskId) {
        performBatchAIScoring(taskId, null);
    }

    private void performBatchAIScoring(String taskId, Long scopedUserId) {
        try {
            log.info("开始批量AI评分任务: {}", taskId);
            int pageSize = 200;
            long total = scopedUserId == null ? photoRepository.count() : photoRepository.countByUserId(scopedUserId);
            int processed = 0;
            int successCount = 0;
            int failureCount = 0;

            log.info("共需处理 {} 张照片", total);

            int pageNum = 0;
            while (processed < total) {
                Page<Photo> photoPage = scopedUserId == null
                    ? photoRepository.findAll(PageRequest.of(pageNum, pageSize))
                    : photoRepository.findByUserId(scopedUserId, PageRequest.of(pageNum, pageSize));
                if (photoPage.isEmpty()) {
                    break;
                }
                for (com.photoexhibition.entity.Photo photo : photoPage.getContent()) {
                    try {
                        com.photoexhibition.entity.PhotoAIScoring scoring = aiScoringService.scorePhoto(photo);
                        if (scoring != null) {
                            successCount++;
                            log.debug("AI评分成功: 照片ID={}, 评分={}", photo.getId(), scoring.getOverallScore());
                        } else {
                            failureCount++;
                            log.warn("AI评分失败: 照片ID={}", photo.getId());
                        }
                    } catch (Exception e) {
                        failureCount++;
                        log.error("AI评分异常: 照片ID={}, 错误={}", photo.getId(), e.getMessage());
                    }

                    processed++;
                    if (processed % 100 == 0) {
                        log.info("AI评分进度: {}/{} (成功:{}, 失败:{})", processed, total, successCount, failureCount);
                    }
                }
                pageNum++;
            }

            log.info("批量AI评分任务完成: 总计={}, 成功={}, 失败={}", total, successCount, failureCount);

        } catch (Exception e) {
            log.error("批量AI评分任务执行失败", e);
        }
    }

    /**
     * 执行相册AI评分
     */
    private void performAlbumAIScoring(String taskId, Long albumId) {
        performAlbumAIScoring(taskId, albumId, null);
    }

    private void performAlbumAIScoring(String taskId, Long albumId, Long scopedUserId) {
        try {
            log.info("开始相册AI评分任务: {}, 相册ID: {}", taskId, albumId);
            int pageSize = 200;
            long total = scopedUserId == null
                ? photoRepository.countByAlbumId(albumId)
                : photoRepository.countByAlbumIdAndUserId(albumId, scopedUserId);
            int processed = 0;
            int successCount = 0;
            int failureCount = 0;

            log.info("相册 {} 共需处理 {} 张照片", albumId, total);

            int pageNum = 0;
            while (processed < total) {
                Page<Photo> albumPhotos = scopedUserId == null
                    ? photoRepository.findByAlbumId(albumId, PageRequest.of(pageNum, pageSize))
                    : photoRepository.findByAlbumIdAndUserId(albumId, scopedUserId, PageRequest.of(pageNum, pageSize));
                if (albumPhotos.isEmpty()) {
                    break;
                }
                for (com.photoexhibition.entity.Photo photo : albumPhotos.getContent()) {
                    try {
                        com.photoexhibition.entity.PhotoAIScoring scoring = aiScoringService.scorePhoto(photo);
                        if (scoring != null) {
                            successCount++;
                            log.debug("相册AI评分成功: 照片ID={}, 评分={}", photo.getId(), scoring.getOverallScore());
                        } else {
                            failureCount++;
                            log.warn("相册AI评分失败: 照片ID={}", photo.getId());
                        }
                    } catch (Exception e) {
                        failureCount++;
                        log.error("相册AI评分异常: 照片ID={}, 错误={}", photo.getId(), e.getMessage());
                    }

                    processed++;
                    if (processed % 50 == 0) {
                        log.info("相册AI评分进度: {}/{} (成功:{}, 失败:{})", processed, total, successCount, failureCount);
                    }
                }
                pageNum++;
            }

            log.info("相册AI评分任务完成: 相册ID={}, 总计={}, 成功={}, 失败={}", albumId, total, successCount, failureCount);

        } catch (Exception e) {
            log.error("相册AI评分任务执行失败", e);
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "设置特效失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "获取特效配置失败"));
            resp.put("albumId", id);
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 批量操作（移动到、删除、隐藏/显示照片）
     */
    @PostMapping("/photos/batch")
    public ResponseEntity<Map<String, Object>> batchOperation(
            @RequestParam String operation,
            @RequestBody List<Long> photoIds) {
        Map<String, Object> resp = new HashMap<>();
        try {
            int count = 0;
            switch (operation) {
                case "hide":
                    count = photoManageService.hidePhotos(photoIds);
                    resp.put("message", "已隐藏 " + count + " 张照片");
                    break;
                case "show":
                    count = photoManageService.showPhotos(photoIds);
                    resp.put("message", "已显示 " + count + " 张照片");
                    break;
                case "delete":
                    count = photoManageService.deletePhotosReturningCount(photoIds);
                    resp.put("message", "已删除 " + count + " 张照片");
                    break;
                default:
                    resp.put("error", "未知操作: " + operation);
                    return ResponseEntity.badRequest().body(resp);
            }
            resp.put("success", true);
            resp.put("count", count);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "操作失败"));
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 清理所有数据（只保留账号数据）
     * 危险操作：会删除所有照片、相册、标签、人脸、人物等数据
     */
    @PostMapping("/cleanup/all")
    public ResponseEntity<Map<String, Object>> cleanupAllData(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            dataCleanupService.cleanupAllData();
            resp.put("message", "数据清理完成，已删除所有照片、相册、标签、人脸、人物数据，账号数据已保留");
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "数据清理失败"));
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 清空所有缩略图（删除缩略图文件并清空数据库路径）
     * 用于重新生成三级缩略图系统
     */
    @PostMapping("/thumbnails/clear")
    public ResponseEntity<Map<String, Object>> clearAllThumbnails(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            Map<String, Object> result = photoScanService.clearAllThumbnails();
            resp.putAll(result);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "清空缩略图失败"));
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 清空所有人脸数据
     */
    @PostMapping("/faces/clear")
    public ResponseEntity<Map<String, Object>> clearAllFaces(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            Map<String, Object> result = photoScanService.clearAllFaces();
            resp.putAll(result);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "清空人脸数据失败"));
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 清空所有智能标签
     */
    @PostMapping("/smart-tags/clear")
    public ResponseEntity<Map<String, Object>> clearAllSmartTags(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            Map<String, Object> result = photoScanService.clearAllSmartTags();
            resp.putAll(result);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "清空智能标签失败"));
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 清理已删除文件的残留数据
     * 删除不存在图片文件的照片记录、人脸数据、标签关联，以及空相册
     */
    @PostMapping("/cleanup/orphaned")
    public ResponseEntity<Map<String, Object>> cleanupOrphanedData(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            Map<String, Object> result = photoScanService.cleanupOrphanedData();
            resp.putAll(result);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "清理残留数据失败"));
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 更新相册时间字段
     * 重新计算所有相册的拍摄时间和相册名时间
     */
    @PostMapping("/albums/update-times")
    public ResponseEntity<Map<String, Object>> updateAlbumTimes(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            albumService.updateAlbumTimeFields();
            resp.put("message", "相册时间字段更新完成");
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "更新相册时间失败"));
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 更新所有照片的时间信息
     * 重新从EXIF信息中提取拍摄时间（异步执行）
     */
    @PostMapping("/photos/update-times")
    public ResponseEntity<Map<String, Object>> updateAllPhotoTimes(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            photoScanService.updateAllPhotoTimesAsync();
            resp.put("message", "照片时间更新任务已异步启动，请稍后查看日志了解进度");
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "启动照片时间更新任务失败"));
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
            resp.put("error", sanitizeErrorMessage(e.getMessage(), "更新照片时间失败"));
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 执行ONNX Runtime完整诊断
     */
    @PostMapping("/diagnostics/onnx")
    public ResponseEntity<Map<String, Object>> runONNXDiagnostics(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        try {
            requireSuperAdminUser(authorization);
            onnxDiagnosticUtil.performFullDiagnostic();
            resp.put("message", "ONNX Runtime诊断完成，请查看日志输出");
            resp.put("success", true);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            resp.put("error", "诊断执行失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            resp.put("success", false);
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 搜索相似照片
     */
    @GetMapping("/photos/{photoId}/similar")
    public ResponseEntity<Map<String, Object>> findSimilarPhotos(
            @RequestHeader("Authorization") String authorization,
            @PathVariable Long photoId,
            @RequestParam(defaultValue = "10") int limit) {
        Map<String, Object> resp = new HashMap<>();

        try {
            UserAccount user = requireCurrentUser(authorization);
            Long scopedUserId = resolveScopedUserId(user);
            var similarPhotos = similarPhotoSearchService.findSimilarPhotos(photoId, limit, scopedUserId);

            List<Map<String, Object>> result = similarPhotos.stream()
                .map(resultItem -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("photoId", resultItem.photoId);
                    item.put("similarityScore", resultItem.similarityScore);
                    item.put("matchReasons", resultItem.matchReasons);

                    // 包含照片基本信息
                    if (resultItem.photo != null) {
                        Map<String, Object> photoInfo = new HashMap<>();
                        photoInfo.put("id", resultItem.photo.getId());
                        photoInfo.put("filename", resultItem.photo.getFilename());
                        photoInfo.put("thumbnailPath", userPathService.toDisplayPath(resultItem.photo.getThumbnailPath(), true));
                        photoInfo.put("originalPath", userPathService.toDisplayPath(resultItem.photo.getOriginalPath(), true));
                        photoInfo.put("takenAt", resultItem.photo.getTakenAt());
                        photoInfo.put("albumId", resultItem.photo.getAlbumId());
                        item.put("photo", photoInfo);
                    }

                    return item;
                })
                .collect(Collectors.toList());

            resp.put("success", true);
            resp.put("data", result);
            resp.put("total", result.size());

        } catch (Exception e) {
            log.error("搜索相似照片失败: {}", e.getMessage(), e);
            resp.put("success", false);
            resp.put("error", "搜索相似照片失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            return ResponseEntity.status(500).body(resp);
        }

        return ResponseEntity.ok(resp);
    }

    // ==================== 背景移除批量处理 API ====================

    /**
     * 批量处理相册中的所有照片背景移除
     * 同步处理，会阻塞直到完成
     */
    @PostMapping("/photos/batch-remove-background")
    public ResponseEntity<Map<String, Object>> batchRemoveBackground(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) Long albumId,
            @RequestParam(defaultValue = "50") int batchSize,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "true") boolean saveToPhoto) {
        
        Map<String, Object> resp = new HashMap<>();
        UserAccount user = requireCurrentUser(authorization);
        Long scopedUserId = resolveScopedUserId(user);
        
        if (!backgroundRemovalService.isModelAvailable()) {
            resp.put("success", false);
            resp.put("message", "背景移除功能未启用或模型未加载");
            log.warn("背景移除功能未启用或模型未加载");
            return ResponseEntity.ok(resp);
        }

        try {
            // 获取照片列表：如果 albumId 为空，则处理所有照片
            Pageable pageable = PageRequest.of(page, batchSize);
            Page<Photo> photoPage;
            String taskDescription;
            
            if (albumId != null) {
                albumService.getAlbumById(albumId, scopedUserId);
                photoPage = scopedUserId == null
                    ? photoRepository.findByAlbumId(albumId, pageable)
                    : photoRepository.findByAlbumIdAndUserId(albumId, scopedUserId, pageable);
                taskDescription = "相册 " + albumId;
            } else {
                photoPage = scopedUserId == null
                    ? photoRepository.findAll(pageable)
                    : photoRepository.findByUserId(scopedUserId, pageable);
                taskDescription = scopedUserId == null ? "全部照片" : "当前用户照片";
            }
            
            int processed = 0;
            int failed = 0;
            long startTime = System.currentTimeMillis();
            
            log.info("开始批量处理 {} 的背景移除，共 {} 张照片", taskDescription, photoPage.getContent().size());
            
            for (Photo photo : photoPage.getContent()) {
                try {
                    String photoPath = photo.getOriginalPath();
                    java.util.Optional<java.nio.file.Path> resolvedSourcePath = userPathService.tryResolveLocalStoredPhotoPath(photoPath);
                    if (resolvedSourcePath.isEmpty()) {
                        log.warn("抠图跳过非本地或不可解析路径: photoId={}, path={}", photo.getId(), photoPath);
                        failed++;
                        continue;
                    }

                    File sourceFile = resolvedSourcePath.get().toFile();
                    if (!sourceFile.exists()) {
                        log.warn("源文件不存在: photoId={}, path={}", photo.getId(), userPathService.toDisplayPath(photoPath, true));
                        failed++;
                        continue;
                    }
                    
                    // 生成输出文件路径：原图目录下的 .thumbnails 文件夹
                    File thumbnailDir = new File(sourceFile.getParent(), ".thumbnails");
                    if (!thumbnailDir.exists()) {
                        thumbnailDir.mkdirs();
                    }
                    
                    String baseName = photo.getFilename();
                    int dotIndex = baseName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        baseName = baseName.substring(0, dotIndex);
                    }
                    File outputFile = new File(thumbnailDir, baseName + "_no_bg.png");
                    
                    // 执行背景移除
                    boolean success = backgroundRemovalService.removeBackground(sourceFile, outputFile);
                    
                    if (success) {
                        processed++;
                        // 可选：保存路径到数据库
                        if (saveToPhoto) {
                            photo.setBackgroundRemovedPath(userPathService.tryBuildStoragePathReference(outputFile.getAbsolutePath(), photo.getUserId())
                                .orElse(outputFile.getAbsolutePath()));
                            photoRepository.save(photo);
                        }
                    } else {
                        failed++;
                    }
                    
                } catch (Exception e) {
                    log.error("处理照片失败: {}", photo.getId(), e);
                    failed++;
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("批量处理完成: 成功 {}, 失败 {}, 耗时 {}ms", processed, failed, duration);
            
            resp.put("success", true);
            resp.put("message", "批量处理完成");
            resp.put("processed", processed);
            resp.put("failed", failed);
            resp.put("total", photoPage.getContent().size());
            resp.put("duration", duration + "ms");
            resp.put("hasMore", !photoPage.isLast());
            
            return ResponseEntity.ok(resp);
            
        } catch (Exception e) {
            log.error("批量处理失败", e);
            resp.put("success", false);
            resp.put("message", "批量处理失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            return ResponseEntity.status(500).body(resp);
        }
    }

    /**
     * 检查背景移除功能状态
     */
    @GetMapping("/background-removal/status")
    public ResponseEntity<Map<String, Object>> getBackgroundRemovalStatus() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("available", backgroundRemovalService.isModelAvailable());
        resp.put("message", backgroundRemovalService.isModelAvailable() ? "功能可用" : "功能未启用或模型未加载");
        return ResponseEntity.ok(resp);
    }

    /**
     * 批量移除背景（抠图处理）
     * 兼容旧版API：albumId可选，不传则处理所有图片
     */
    @PostMapping("/background-removal/batch")
    public ResponseEntity<Map<String, Object>> batchBackgroundRemoval(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) Long albumId,
            @RequestParam(defaultValue = "50") int batchSize,
            @RequestParam(defaultValue = "false") boolean saveToPhoto,
            @RequestParam(defaultValue = "false") boolean force) {
        
        Map<String, Object> resp = new HashMap<>();
        UserAccount user = requireCurrentUser(authorization);
        Long scopedUserId = resolveScopedUserId(user);
        
        if (!backgroundRemovalService.isModelAvailable()) {
            resp.put("success", false);
            resp.put("message", "背景移除功能未启用或模型未加载");
            return ResponseEntity.ok(resp);
        }

        try {
            if (albumId != null) {
                albumService.getAlbumById(albumId, scopedUserId);
            }
            // 生成任务ID
            String taskId = "bg-remove-" + System.currentTimeMillis();
            
            // 启动异步任务
            photoScanService.batchBackgroundRemovalAsync(taskId, albumId, batchSize, saveToPhoto, force, scopedUserId);
            
            resp.put("success", true);
            resp.put("message", "批量背景移除任务已启动");
            resp.put("taskId", taskId);
            resp.put("statusUrl", "/api/admin/tasks/" + taskId);
            
            log.info("批量背景移除任务已启动: {}", taskId);
            
        } catch (Exception e) {
            log.error("启动批量背景移除失败", e);
            resp.put("success", false);
            resp.put("message", "启动失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            return ResponseEntity.status(500).body(resp);
        }

        return ResponseEntity.ok(resp);
    }
    
    /**
     * 清空所有抠图缓存（删除所有背景移除处理后的图片文件）
     */
    @DeleteMapping("/photos/clear-background-cache")
    public ResponseEntity<Map<String, Object>> clearBackgroundCache(@RequestHeader("Authorization") String authorization) {
        Map<String, Object> resp = new HashMap<>();
        requireSuperAdminUser(authorization);
        
        try {
            // 生成任务ID
            String taskId = "clear-bg-cache-" + System.currentTimeMillis();
            
            // 启动异步任务
            photoScanService.clearBackgroundCacheAsync(taskId);
            
            resp.put("success", true);
            resp.put("message", "清空抠图缓存任务已启动");
            resp.put("taskId", taskId);
            resp.put("statusUrl", "/api/admin/tasks/" + taskId);
            
            log.info("清空抠图缓存任务已启动: {}", taskId);
            
        } catch (Exception e) {
            log.error("清空抠图缓存失败", e);
            resp.put("success", false);
            resp.put("message", "清空抠图缓存失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            return ResponseEntity.status(500).body(resp);
        }

        return ResponseEntity.ok(resp);
    }

    /**
     * 清空 AI 搜索结果缓存
     */
    @DeleteMapping("/ai-search/clear-cache")
    public ResponseEntity<Map<String, Object>> clearSearchCache(
            @RequestParam(required = false) String query) {
        Map<String, Object> resp = new HashMap<>();

        try {
            if (query != null && !query.isBlank()) {
                aiSearchService.clearSearchCache(query);
                resp.put("success", true);
                resp.put("message", "已清除指定搜索缓存: " + query);
            } else {
                aiSearchService.clearSearchCache();
                resp.put("success", true);
                resp.put("message", "已清除所有 AI 搜索缓存");
            }
        } catch (Exception e) {
            log.error("清除搜索缓存失败", e);
            resp.put("success", false);
            resp.put("message", "清除搜索缓存失败: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            return ResponseEntity.status(500).body(resp);
        }

        return ResponseEntity.ok(resp);
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

}
