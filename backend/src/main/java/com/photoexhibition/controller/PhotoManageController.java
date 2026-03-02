package com.photoexhibition.controller;

import com.photoexhibition.service.PhotoManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/photos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class PhotoManageController {

    private final PhotoManageService photoManageService;

    /**
     * Get move targets for photos in an album (parent dir, sibling dirs, child dirs)
     */
    @GetMapping("/move-targets/{albumId}")
    public ResponseEntity<Map<String, Object>> getMoveTargets(@PathVariable Long albumId) {
        return ResponseEntity.ok(photoManageService.getMoveTargets(albumId));
    }

    /**
     * Batch move photos to a target directory
     */
    @PostMapping("/batch-move")
    public ResponseEntity<Map<String, Object>> batchMovePhotos(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) request.get("photoIds");
        String targetPath = (String) request.get("targetPath");

        if (rawIds == null || targetPath == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少参数"));
        }

        List<Long> photoIds = rawIds.stream().map(Integer::longValue).collect(java.util.stream.Collectors.toList());
        log.info("批量移动照片: {} 张 -> {}", photoIds.size(), targetPath);
        Map<String, Object> result = photoManageService.movePhotos(photoIds, targetPath);
        return ResponseEntity.ok(result);
    }

    /**
     * Batch delete photos (files + DB records)
     */
    @PostMapping("/batch-delete")
    public ResponseEntity<Map<String, Object>> batchDeletePhotos(@RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Integer> rawIds = (List<Integer>) request.get("photoIds");

        if (rawIds == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少参数"));
        }

        List<Long> photoIds = rawIds.stream().map(Integer::longValue).collect(java.util.stream.Collectors.toList());
        log.info("批量删除照片: {} 张", photoIds.size());
        Map<String, Object> result = photoManageService.deletePhotos(photoIds);
        return ResponseEntity.ok(result);
    }
}
