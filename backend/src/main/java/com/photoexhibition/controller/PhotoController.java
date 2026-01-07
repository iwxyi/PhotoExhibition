package com.photoexhibition.controller;

import com.photoexhibition.dto.FilterRequest;
import com.photoexhibition.dto.PhotoDTO;
import com.photoexhibition.service.PhotoService;
import com.photoexhibition.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/photos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PhotoController {

    private final PhotoService photoService;
    private final SystemConfigService systemConfigService;

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
        Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
        Page<PhotoDTO> photos = photoService.filterPhotos(request, pageable);
        return ResponseEntity.ok(photos);
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
}

