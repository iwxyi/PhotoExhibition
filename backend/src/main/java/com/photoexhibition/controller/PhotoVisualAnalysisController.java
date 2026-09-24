package com.photoexhibition.controller;

import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.PhotoVisualAnalysisService;
import com.photoexhibition.service.PhotoVisualAnalysisJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/admin/photos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PhotoVisualAnalysisController {
    private final AuthService authService;
    private final PhotoVisualAnalysisService photoVisualAnalysisService;
    private final PhotoVisualAnalysisJobService photoVisualAnalysisJobService;

    @GetMapping("/{photoId}/visual-analysis")
    public ResponseEntity<Map<String, Object>> get(@RequestHeader("Authorization") String authorization, @PathVariable Long photoId) {
        return ResponseEntity.ok(photoVisualAnalysisService.get(photoId, currentUser(authorization)));
    }

    @PostMapping("/{photoId}/visual-analysis")
    public ResponseEntity<Map<String, Object>> analyze(@RequestHeader("Authorization") String authorization, @PathVariable Long photoId) {
        return ResponseEntity.ok(photoVisualAnalysisService.analyze(photoId, currentUser(authorization)));
    }

    @PostMapping("/visual-analysis/jobs")
    public ResponseEntity<Map<String, Object>> enqueue(@RequestHeader("Authorization") String authorization, @RequestBody Map<String, Object> body) {
        Object rawIds = body.get("photoIds");
        java.util.List<Long> photoIds = rawIds instanceof java.util.List ? ((java.util.List<?>) rawIds).stream().filter(java.util.Objects::nonNull).map(value -> Long.valueOf(String.valueOf(value))).collect(java.util.stream.Collectors.toList()) : java.util.List.of();
        boolean force = !Boolean.FALSE.equals(body.get("force"));
        return ResponseEntity.ok(photoVisualAnalysisJobService.enqueue(currentUser(authorization), photoIds, force));
    }

    @GetMapping("/visual-analysis/jobs")
    public ResponseEntity<java.util.List<Map<String, Object>>> jobs(@RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(photoVisualAnalysisJobService.list(currentUser(authorization)));
    }

    @GetMapping("/visual-analysis/jobs/{jobId}")
    public ResponseEntity<Map<String, Object>> job(@RequestHeader("Authorization") String authorization, @PathVariable Long jobId) {
        return ResponseEntity.ok(photoVisualAnalysisJobService.get(currentUser(authorization), jobId));
    }

    private UserAccount currentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException("未授权");
        return authService.getCurrentUserEntity(authorization.substring(7));
    }
}
