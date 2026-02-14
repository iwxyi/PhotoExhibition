package com.photoexhibition.controller;

import com.photoexhibition.dto.FilterRequest;
import com.photoexhibition.dto.PhotoDTO;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.service.BackgroundRemovalService;
import com.photoexhibition.service.PhotoService;
import com.photoexhibition.service.SystemConfigService;
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
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PhotoController {

    private final PhotoService photoService;
    private final SystemConfigService systemConfigService;
    private final PhotoRepository photoRepository;
    private final BackgroundRemovalService backgroundRemovalService;

    /**
     * 图墙模式 - 获取所有图片（瀑布流）
     */
    @GetMapping("/wall")
    public ResponseEntity<Page<PhotoDTO>> getPhotoWall(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 使用系统配置的图墙排序方式
        Pageable pageable = createPhotoSortedPageable(page, size, systemConfigService.getWallSortOrder());
        Page<PhotoDTO> photos = photoService.getAllPhotos(pageable);

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
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PhotoDTO> photos = photoService.getAllPhotos(pageable);
        return ResponseEntity.ok(photos);
    }

    /**
     * 随机模式 - 获取随机高质量图片
     */
    @GetMapping("/random")
    public ResponseEntity<Page<PhotoDTO>> getRandomPhotos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "70.0") double minQualityScore) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PhotoDTO> photos = photoService.getRandomHighQualityPhotos(minQualityScore, pageable);
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
            @RequestParam(defaultValue = "false") boolean all) {
        Page<PhotoDTO> photos;
        if (all) {
            // 返回所有照片，不分页
            photos = photoService.getAllPhotosByAlbum(albumId);
        } else {
            Pageable pageable = PageRequest.of(page, size);
            photos = photoService.getPhotosByAlbum(albumId, pageable);
        }
        return ResponseEntity.ok(photos);
    }

    /**
     * 高级筛选
     */
    @PostMapping("/filter")
    public ResponseEntity<Page<PhotoDTO>> filterPhotos(@RequestBody FilterRequest request) {
        Pageable pageable;
        if (request.getRandomOrder() != null && request.getRandomOrder()) {
            // 随机排序 - 使用 JpaSort.unsafe 处理原生 SQL 函数
            pageable = PageRequest.of(request.getPage(), request.getSize(), JpaSort.unsafe("RAND()"));
        } else {
            // 使用系统配置的图墙排序方式
            pageable = createPhotoSortedPageable(request.getPage(), request.getSize(), systemConfigService.getWallSortOrder());
        }
        Page<PhotoDTO> photos = photoService.filterPhotos(request, pageable);
        return ResponseEntity.ok(photos);
    }

    /**
     * 获取筛选选项
     */
    @GetMapping("/filter-options")
    public ResponseEntity<Map<String, Object>> getFilterOptions() {
        Map<String, Object> options = photoService.getFilterOptions();
        return ResponseEntity.ok(options);
    }

    /**
     * 获取图片详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<PhotoDTO> getPhotoById(@PathVariable Long id) {
        PhotoDTO photo = photoService.getPhotoById(id);
        // 增加查看次数
        photoService.incrementViewCount(id);
        return ResponseEntity.ok(photo);
    }

    /**
     * 删除图片
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long id) {
        photoService.deletePhoto(id);
        return ResponseEntity.ok().build();
    }

    /**
     * 点赞图片（匿名）
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<Integer> likePhoto(@PathVariable Long id) {
        int newCount = photoService.incrementLike(id);
        return ResponseEntity.ok(newCount);
    }

    /**
     * 取消点赞图片（匿名）
     */
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Integer> unlikePhoto(@PathVariable Long id) {
        int newCount = photoService.decrementLike(id);
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
     */
    @GetMapping("/{id}/remove-background")
    public ResponseEntity<byte[]> removeBackground(@PathVariable Long id) {
        log.info("收到抠图请求: photoId={}", id);
        
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

        // 3. 检查缓存（数据库中已存储的处理结果）
        String cachedPath = photo.getBackgroundRemovedPath();
        if (cachedPath != null && !cachedPath.isEmpty()) {
            File cachedFile = new File(cachedPath);
            if (cachedFile.exists()) {
                log.info("使用缓存文件: {}", cachedPath);
                try {
                    byte[] cachedBytes = Files.readAllBytes(cachedFile.toPath());
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.IMAGE_PNG);
                    headers.setContentLength(cachedBytes.length);
                    headers.setCacheControl("public, max-age=31536000");
                    return new ResponseEntity<>(cachedBytes, headers, HttpStatus.OK);
                } catch (IOException e) {
                    log.warn("读取缓存文件失败: {}", cachedPath, e);
                    // 继续处理，不返回错误
                }
            }
        }

        // 4. 确定源图片路径
        String photoPath = photo.getOriginalPath();
        log.info("源图片路径: {}", photoPath);
        File sourceFile = new File(photoPath);
        if (!sourceFile.exists()) {
            log.warn("源图片文件不存在: {}", photoPath);
            return ResponseEntity.notFound().build();
        }

        try {
            // 5. 执行背景移除（使用并发版本）
            log.info("开始处理抠图(并发): {}", photoPath);
            
            // 确定输出文件路径
            File parentDir = new File(photo.getOriginalPath()).getParentFile();
            File cacheDir = new File(parentDir, ".thumbnails");
            String cachedFileName = "bg_removed_" + photo.getId() + ".png";
            File outputFile = new File(cacheDir, cachedFileName);
            
            // 使用并发方法处理
            BufferedImage resultImage = backgroundRemovalService.removeBackgroundConcurrently(
                photo.getId(), sourceFile, outputFile);
            
            if (resultImage == null) {
                log.error("背景移除返回空结果");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            log.info("抠图处理完成，返回图片");
            // 6. 转换为 PNG 字节流
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resultImage, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            // 7. 保存到缓存文件（如果并发方法没保存）
            try {
                if (cacheDir.exists() && !outputFile.exists()) {
                    ImageIO.write(resultImage, "PNG", outputFile);
                    
                    // 更新数据库路径
                    photo.setBackgroundRemovedPath(outputFile.getAbsolutePath());
                    photoRepository.save(photo);
                    log.info("已保存缓存文件: {}", outputFile.getAbsolutePath());
                }
            } catch (IOException e) {
                log.warn("保存缓存文件失败", e);
            }

            // 8. 返回图片
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(imageBytes.length);
            headers.setCacheControl("public, max-age=31536000"); // 缓存1年

            return new ResponseEntity<>(imageBytes, headers, HttpStatus.OK);
            
        } catch (IOException e) {
            log.error("图片处理失败: {}", photoPath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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

        // TODO: 实现异步任务队列
        result.put("success", true);
        result.put("message", "任务已提交（待实现）");
        result.put("photoId", id);
        
        return ResponseEntity.ok(result);
    }

    // ==================== 批量处理 API ====================

    /**
     * 批量处理相册中的所有照片背景移除
     * 这是一个同步端点，会阻塞直到处理完成
     * 建议使用小批量或后台任务调用
     */
    @PostMapping("/batch-remove-background")
    public ResponseEntity<Map<String, Object>> batchRemoveBackground(
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
            // 获取相册中的照片
            Pageable pageable = PageRequest.of(page, batchSize);
            Page<Photo> photoPage = photoRepository.findByAlbumId(albumId, pageable);
            
            int processed = 0;
            int failed = 0;
            long startTime = System.currentTimeMillis();
            
            for (Photo photo : photoPage.getContent()) {
                try {
                    String photoPath = photo.getOriginalPath();
                    
                    File sourceFile = new File(photoPath);
                    if (!sourceFile.exists()) {
                        log.warn("源文件不存在: {}", photoPath);
                        failed++;
                        continue;
                    }
                    
                    // 生成输出文件路径：原图目录下的 .thumbnails 文件夹
                    File thumbnailDir = new File(sourceFile.getParent(), ".thumbnails");
                    if (!thumbnailDir.exists()) {
                        thumbnailDir.mkdirs();
                    }
                    
                    String baseName = "bg_removed_" + photo.getId();
                    File outputFile = new File(thumbnailDir, baseName + ".png");
                    
                    // 执行背景移除
                    boolean success = backgroundRemovalService.removeBackground(sourceFile, outputFile);
                    
                    if (success) {
                        processed++;
                        // 可选：保存路径到数据库
                        if (saveToPhoto) {
                            photo.setBackgroundRemovedPath(outputFile.getAbsolutePath());
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
            
            result.put("success", true);
            result.put("message", "批量处理完成");
            result.put("processed", processed);
            result.put("failed", failed);
            result.put("total", photoPage.getContent().size());
            result.put("duration", duration + "ms");
            result.put("hasMore", !photoPage.isLast());
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("批量处理失败", e);
            result.put("success", false);
            result.put("message", "批量处理失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    /**
     * 获取批量处理状态（预留接口）
     */
    @GetMapping("/batch-remove-background/status")
    public ResponseEntity<Map<String, Object>> getBatchStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("modelAvailable", backgroundRemovalService.isModelAvailable());
        result.put("message", "批量处理状态查询（待实现任务队列）");
        return ResponseEntity.ok(result);
    }

}

