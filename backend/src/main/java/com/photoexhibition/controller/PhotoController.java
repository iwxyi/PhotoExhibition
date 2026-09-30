package com.photoexhibition.controller;

import com.photoexhibition.dto.FilterRequest;
import com.photoexhibition.dto.PhotoDTO;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.ProcessingStatus;
import com.photoexhibition.entity.BackgroundJobResourceLane;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.UserAccountRepository;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.BackgroundRemovalService;
import com.photoexhibition.service.BackgroundJobService;
import com.photoexhibition.service.PhotoAssetService;
import com.photoexhibition.service.PhotoScanService;
import com.photoexhibition.service.PhotoService;
import com.photoexhibition.service.PublicUserScopeService;
import com.photoexhibition.service.SystemConfigService;
import com.photoexhibition.service.UserPathService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PhotoController {

    private static final Pattern EMBEDDED_PATH_PATTERN =
        Pattern.compile("(storage://[^\\s,;]+|[A-Za-z]:\\\\[^\\s,;]+|/(?:[^\\s,;])+)");

    private final PhotoService photoService;
    private final SystemConfigService systemConfigService;
    private final PhotoRepository photoRepository;
    private final BackgroundRemovalService backgroundRemovalService;
    private final PublicUserScopeService publicUserScopeService;
    private final UserPathService userPathService;
    private final PhotoAssetService photoAssetService;
    private final AuthService authService;
    private final PhotoScanService photoScanService;
    private final BackgroundJobService backgroundJobService;
    private final UserAccountRepository userAccountRepository;

    /**
     * 图墙模式 - 获取所有图片（瀑布流）
     */
    @GetMapping("/wall")
    public ResponseEntity<Page<PhotoDTO>> getPhotoWall(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userSlug) {
        // 使用系统配置的图墙排序方式
        Pageable pageable = createPhotoSortedPageable(page, size, systemConfigService.getWallSortOrder());
        Page<PhotoDTO> photos = photoService.getAllPhotos(pageable, publicUserScopeService.resolveUserId(userSlug));

        // 设置缓存控制头，防止浏览器缓存排序结果
        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(photos);
    }

    /**
     * 获取所有图片（通用列表）
     */
    @GetMapping
    public ResponseEntity<Page<PhotoDTO>> getAllPhotos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userSlug) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PhotoDTO> photos = photoService.getAllPhotos(pageable, publicUserScopeService.resolveUserId(userSlug));
        return ResponseEntity.ok(photos);
    }

    /**
     * 随机模式 - 获取随机高质量图片
     */
    @GetMapping("/random")
    public ResponseEntity<Page<PhotoDTO>> getRandomPhotos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "70.0") double minQualityScore,
            @RequestParam(required = false) String userSlug) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PhotoDTO> photos = photoService.getRandomHighQualityPhotos(minQualityScore, pageable, publicUserScopeService.resolveUserId(userSlug));
        return ResponseEntity.ok(photos);
    }

    /**
     * 获取相册中的图片
     */
    @GetMapping("/album/{albumId}")
    public ResponseEntity<Page<PhotoDTO>> getPhotosByAlbum(
            @PathVariable Long albumId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean all,
            @RequestParam(defaultValue = "false") boolean includeHidden,
            @RequestParam(required = false) String userSlug,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        Page<PhotoDTO> photos;
        Long userId = publicUserScopeService.resolveUserId(userSlug);
        if (includeHidden) {
            UserAccount currentUser = requireCurrentUser(authorization);
            userId = currentUser.getRole() == com.photoexhibition.entity.UserRole.SUPER_ADMIN
                ? null
                : currentUser.getId();
        }
        if (all) {
            // 返回所有照片，不分页
            photos = photoService.getAllPhotosByAlbum(albumId, userId, includeHidden);
        } else {
            Pageable pageable = PageRequest.of(page, size);
            photos = photoService.getPhotosByAlbum(albumId, pageable, userId, includeHidden);
        }
        return ResponseEntity.ok(photos);
    }

    /**
     * 高级筛选
     */
    @PostMapping("/filter")
    public ResponseEntity<Page<PhotoDTO>> filterPhotos(@RequestBody FilterRequest request,
                                                       @RequestParam(required = false) String userSlug) {
        Pageable pageable;
        if (request.getRandomOrder() != null && request.getRandomOrder()) {
            // 随机排序 - 使用 JpaSort.unsafe 处理原生 SQL 函数
            pageable = PageRequest.of(request.getPage(), request.getSize(), JpaSort.unsafe("RAND()"));
        } else {
            // 使用系统配置的图墙排序方式
            pageable = createPhotoSortedPageable(request.getPage(), request.getSize(), systemConfigService.getWallSortOrder());
        }
        String resolvedSlug = userSlug != null ? userSlug : request.getUserSlug();
        Page<PhotoDTO> photos = photoService.filterPhotos(request, pageable, publicUserScopeService.resolveUserId(resolvedSlug));
        return ResponseEntity.ok(photos);
    }

    /**
     * 获取筛选选项
     */
    @GetMapping("/filter-options")
    public ResponseEntity<Map<String, Object>> getFilterOptions(@RequestParam(required = false) String userSlug) {
        Map<String, Object> options = photoService.getFilterOptions(publicUserScopeService.resolveUserId(userSlug));
        return ResponseEntity.ok(options);
    }

    /**
     * 获取图片详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<PhotoDTO> getPhotoById(@PathVariable Long id,
                                                 @RequestParam(required = false) String userSlug) {
        Long scopedUserId = publicUserScopeService.resolveUserId(userSlug);
        PhotoDTO photo = photoService.getPhotoById(id, scopedUserId);
        // 增加查看次数
        photoService.incrementViewCount(id, scopedUserId);
        return ResponseEntity.ok(photo);
    }

    @GetMapping("/{id}/asset")
    public ResponseEntity<Resource> getPhotoAsset(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "auto") String variant) {
        return photoAssetService.readPhotoAsset(id, variant);
    }

    /**
     * 删除图片
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePhoto(@RequestHeader("Authorization") String authorization,
                                            @PathVariable Long id) {
        UserAccount currentUser = requireCurrentUser(authorization);
        Long scopedUserId = currentUser.getRole() == com.photoexhibition.entity.UserRole.SUPER_ADMIN
            ? null
            : currentUser.getId();
        photoService.deletePhoto(id, scopedUserId);
        return ResponseEntity.ok().build();
    }

    /**
     * 点赞图片（匿名）
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<Integer> likePhoto(@PathVariable Long id,
                                             @RequestParam(required = false) String userSlug) {
        int newCount = photoService.incrementLike(id, publicUserScopeService.resolveUserId(userSlug));
        return ResponseEntity.ok(newCount);
    }

    /**
     * 取消点赞图片（匿名）
     */
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Integer> unlikePhoto(@PathVariable Long id,
                                               @RequestParam(required = false) String userSlug) {
        int newCount = photoService.decrementLike(id, publicUserScopeService.resolveUserId(userSlug));
        return ResponseEntity.ok(newCount);
    }

    /**
     * 创建照片排序的Pageable对象
     * 注意：使用实体属性名，Spring Data JPA 会自动映射到数据库列名
     */
    private Pageable createPhotoSortedPageable(int page, int size, String sort) {
        if (sort == null || sort.isEmpty()) {
            // 默认按拍摄时间倒序排序
            Sort defaultSort = Sort.by(Sort.Direction.DESC, "takenAt");
            return PageRequest.of(page, size, defaultSort);
        }

        Sort sortObj;
        switch (sort) {
            case SystemConfigService.SORT_BY_TAKEN_AT_ASC:
                sortObj = Sort.by(Sort.Direction.ASC, "takenAt");
                break;
            case SystemConfigService.SORT_BY_TAKEN_AT_DESC:
                sortObj = Sort.by(Sort.Direction.DESC, "takenAt");
                break;
            case SystemConfigService.SORT_BY_FILENAME_ASC:
                sortObj = Sort.by(Sort.Direction.ASC, "filename");
                break;
            case SystemConfigService.SORT_BY_FILENAME_DESC:
                sortObj = Sort.by(Sort.Direction.DESC, "filename");
                break;
            case SystemConfigService.SORT_BY_CREATED_AT_ASC:
                sortObj = Sort.by(Sort.Direction.ASC, "createdAt");
                break;
            case SystemConfigService.SORT_BY_CREATED_AT_DESC:
                sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
                break;
            default:
                // 默认按拍摄时间倒序排序
                sortObj = Sort.by(Sort.Direction.DESC, "takenAt");
                break;
        }

        return PageRequest.of(page, size, sortObj);
    }

    // ==================== 背景移除 API ====================

    /**
     * 检查背景移除功能是否可用
     */
    @GetMapping("/{id}/background-removal/available")
    public ResponseEntity<Map<String, Object>> isBackgroundRemovalAvailable(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("enabled", backgroundRemovalService.isModelAvailable());
        result.put("photoExists", photoRepository.existsById(id));
        return ResponseEntity.ok(result);
    }

    /**
     * 为图片移除背景，返回透明 PNG 图片
     * 
     * 使用场景：
     * - 获取已抠出背景的图片
     * - 缓存结果，避免重复计算
     * 
     * @param id 照片ID
     * @param quality 图片质量：small(480), medium(720), large(1080)，默认为 medium
     */
    @GetMapping("/{id}/remove-background")
    public ResponseEntity<byte[]> removeBackground(
            @PathVariable Long id,
            @RequestParam(defaultValue = "medium") String quality) {
        log.info("收到抠图请求: photoId={}, quality={}", id, quality);
        
        // 1. 检查功能是否启用
        if (!backgroundRemovalService.isModelAvailable()) {
            log.warn("背景移除功能未启用或模型未加载");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        // 2. 获取照片信息
        Photo photo = photoRepository.findById(id).orElse(null);
        if (photo == null) {
            log.warn("照片不存在: {}", id);
            return ResponseEntity.notFound().build();
        }

        // 3. 确定使用哪个质量的图片
        int outputMaxSize;
        switch (quality) {
            case "small":
                outputMaxSize = 480;
                break;
            case "large":
                outputMaxSize = 1080;
                break;
            case "medium":
            default:
                outputMaxSize = 720;
                break;
        }

        // 确定源图片路径和对应质量的缓存
        String photoPath = photo.getOriginalPath();
        log.info("源图片路径: {}", userPathService.toDisplayPath(photoPath, true));
        java.util.Optional<java.nio.file.Path> sourcePath = userPathService.tryResolveLocalStoredPhotoPath(photoPath);
        if (sourcePath.isEmpty()) {
            log.warn("当前照片不是本地可解析路径，无法执行同步抠图: {}", userPathService.toDisplayPath(photoPath, true));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        File sourceFile = sourcePath.get().toFile();
        if (!sourceFile.exists()) {
            log.warn("源图片文件不存在: {}", userPathService.toDisplayPath(photoPath, true));
            return ResponseEntity.notFound().build();
        }

        File parentDir = sourceFile.getParentFile();
        File cacheDir = new File(parentDir, ".thumbnails");
        String cachedFileName = "bg_removed_" + photo.getId() + "_" + outputMaxSize + ".png";
        File outputFile = new File(cacheDir, cachedFileName);
        if (outputFile.exists()) {
            try {
                byte[] bytes = Files.readAllBytes(outputFile.toPath());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_PNG);
                headers.setContentLength(bytes.length);
                headers.setCacheControl("public, max-age=31536000");
                return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
            } catch (IOException e) {
                log.warn("读取抠图缓存失败: photoId={}", id, e);
            }
        }
        if (photo.getProcessingStatus() != ProcessingStatus.COMPLETED) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).build();
        }
        UserAccount owner = ownerOf(photo);
        if (owner == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        backgroundJobService.enqueuePhotoJob(owner, owner.getId(), "BACKGROUND_REMOVAL",
            BackgroundJobResourceLane.LOCAL_GPU_AI, List.of(id), false, true, 100, "1", null,
            Map.of("outputMaxSize", outputMaxSize));
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * 异步触发背景移除任务（批量处理）
     * 返回任务ID用于查询进度
     */
    @PostMapping("/{id}/remove-background/async")
    public ResponseEntity<Map<String, Object>> triggerBackgroundRemoval(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();

        if (!backgroundRemovalService.isModelAvailable()) {
            result.put("success", false);
            result.put("message", "背景移除功能未启用");
            return ResponseEntity.ok(result);
        }

        Photo photo = photoRepository.findById(id).orElse(null);
        if (photo == null) {
            result.put("success", false);
            result.put("message", "照片不存在");
            return ResponseEntity.ok(result);
        }

        if (photo.getProcessingStatus() != ProcessingStatus.COMPLETED) {
            result.put("success", false);
            result.put("message", "照片尚未扫描完成，扫描完成后可重新提交");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
        }

        java.util.Optional<java.nio.file.Path> sourcePath = userPathService.tryResolveLocalStoredPhotoPath(photo.getOriginalPath());
        if (sourcePath.isEmpty()) {
            result.put("success", false);
            result.put("message", "当前照片不支持异步抠图");
            result.put("detail", "仅支持本地可解析存储路径");
            return ResponseEntity.badRequest().body(result);
        }

        File sourceFile = sourcePath.get().toFile();
        if (!sourceFile.exists()) {
            result.put("success", false);
            result.put("message", "源图片不存在");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }

        File outputFile = new File(new File(sourceFile.getParentFile(), ".thumbnails"),
            "bg_removed_" + photo.getId() + "_720.png");
        boolean cached = outputFile.exists();
        UserAccount owner = ownerOf(photo);
        if (owner == null) {
            result.put("success", false);
            result.put("message", "照片缺少有效所属账号");
            return ResponseEntity.badRequest().body(result);
        }
        Map<String, Object> job = cached ? null : backgroundJobService.enqueuePhotoJob(owner, owner.getId(),
            "BACKGROUND_REMOVAL", BackgroundJobResourceLane.LOCAL_GPU_AI, List.of(id), false, true,
            100, "1", null, Map.of("outputMaxSize", 720));
        boolean queued = job != null && ((Number) job.get("acceptedItems")).intValue() > 0;

        result.put("success", true);
        result.put("message", cached ? "抠图结果已存在" : queued ? "抠图任务已提交" : "抠图任务已在队列中或已完成");
        result.put("photoId", id);
        result.put("queued", queued);
        result.put("processing", !cached && !queued);
        result.put("cached", cached);
        result.put("status", cached ? "completed" : queued ? "queued" : "processing");
        if (job != null) result.put("job", job);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    // ==================== 批量处理 API ====================

    /**
     * 批量处理相册中的所有照片背景移除
     * 这是一个同步端点，会阻塞直到处理完成
     * 建议使用小批量或后台任务调用
     */
    @PostMapping("/batch-remove-background")
    public ResponseEntity<Map<String, Object>> batchRemoveBackground(
            @RequestHeader("Authorization") String authorization,
            @RequestParam Long albumId,
            @RequestParam(defaultValue = "50") int batchSize,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "false") boolean saveToPhoto) {
        
        Map<String, Object> result = new HashMap<>();
        
        if (!backgroundRemovalService.isModelAvailable()) {
            result.put("success", false);
            result.put("message", "背景移除功能未启用或模型未加载");
            return ResponseEntity.ok(result);
        }

        try {
            UserAccount user = requireCurrentUser(authorization);
            Long scopedUserId = user.getRole() == com.photoexhibition.entity.UserRole.SUPER_ADMIN ? null : user.getId();
            Pageable pageable = PageRequest.of(page, batchSize);
            Page<Photo> photoPage = scopedUserId == null
                ? photoRepository.findByAlbumId(albumId, pageable)
                : photoRepository.findByAlbumIdAndUserId(albumId, scopedUserId, pageable);
            Map<Long, List<Long>> byOwner = new java.util.LinkedHashMap<>();
            for (Photo photo : photoPage) {
                if (photo.getUserId() != null) {
                    byOwner.computeIfAbsent(photo.getUserId(), ignored -> new java.util.ArrayList<>()).add(photo.getId());
                }
            }
            List<Map<String, Object>> jobs = new java.util.ArrayList<>();
            for (Map.Entry<Long, List<Long>> group : byOwner.entrySet()) {
                jobs.add(backgroundJobService.enqueuePhotoJob(user, group.getKey(), "BACKGROUND_REMOVAL",
                    BackgroundJobResourceLane.LOCAL_GPU_AI, group.getValue(), false, true, 100, "1", null,
                    Map.of("albumId", albumId, "saveToPhoto", saveToPhoto)));
            }
            result.put("success", true);
            result.put("message", "批量抠图任务已加入队列");
            result.put("jobs", jobs);
            result.put("processed", 0);
            result.put("failed", 0);
            result.put("total", photoPage.getContent().size());
            result.put("hasMore", !photoPage.isLast());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
            
        } catch (Exception e) {
            log.error("批量处理失败", e);
            result.put("success", false);
            result.put("message", "批量处理失败: " + sanitizeErrorMessage(e.getMessage(), "处理异常"));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 获取背景移除批量/异步处理状态
     */
    @GetMapping("/batch-remove-background/status")
    public ResponseEntity<Map<String, Object>> getBatchStatus(@RequestParam(required = false) String taskId,
                                                               @RequestHeader("Authorization") String authorization) {
        Map<String, Object> result = new HashMap<>();
        result.put("modelAvailable", backgroundRemovalService.isModelAvailable());
        UserAccount viewer = requireCurrentUser(authorization);
        long activeCount = backgroundJobService.activeCount(viewer, "BACKGROUND_REMOVAL");
        result.put("singlePhotoTasksInProgress", activeCount);

        if (taskId != null && !taskId.isBlank()) {
            String normalizedId = taskId.trim();
            if (normalizedId.matches("(?:background-job-)?[0-9]+")) {
                String numericId = normalizedId.replaceFirst("^background-job-", "");
                Long jobId = Long.valueOf(numericId);
                Map<String, Object> job = backgroundJobService.get(viewer, jobId);
                if (!"BACKGROUND_REMOVAL".equals(job.get("jobType"))) {
                    return ResponseEntity.notFound().build();
                }
                result.put("found", true);
                result.put("taskId", normalizedId);
                result.put("job", job);
                result.put("status", job.get("status"));
            } else {
                result.put("found", false);
            }
            Object found = result.get("found");
            if (Boolean.TRUE.equals(found)) {
                result.put("message", "已返回批量背景移除任务状态");
            } else {
                result.put("message", "未找到指定任务，可检查 taskId 是否正确");
            }
        } else if (activeCount > 0) {
            result.put("message", "存在进行中的异步抠图任务");
        } else {
            result.put("message", "当前没有进行中的异步抠图任务");
        }
        result.put("supportsTaskLookup", true);
        result.put("taskQueryHint", "统一任务可通过 /api/admin/background-jobs/{jobId} 查询详情");
        return ResponseEntity.ok(result);
    }

    private UserAccount requireCurrentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("未授权，请先登录");
        }
        return authService.getCurrentUserEntity(authorization.substring(7));
    }

    private UserAccount ownerOf(Photo photo) {
        return photo.getUserId() == null ? null : userAccountRepository.findById(photo.getUserId()).orElse(null);
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
