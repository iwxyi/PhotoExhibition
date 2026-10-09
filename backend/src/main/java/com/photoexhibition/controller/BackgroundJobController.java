package com.photoexhibition.controller;

import com.photoexhibition.entity.BackgroundJobResourceLane;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.service.AuthService;
import com.photoexhibition.service.BackgroundJobService;
import com.photoexhibition.service.BackgroundJobHistoryService;
import com.photoexhibition.service.ScanTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/background-jobs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BackgroundJobController {
    private final AuthService authService;
    private final BackgroundJobService backgroundJobService;
    private final ScanTaskService scanTaskService;
    private final BackgroundJobHistoryService backgroundJobHistoryService;

    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> records(@RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(defaultValue = "false") boolean systemOnly,
            @RequestParam(defaultValue = "true") boolean currentAccount,
            @RequestParam(defaultValue = "all") String view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UserAccount viewer = currentUser(authorization);
        if (currentAccount) { ownerUserId = viewer.getId(); systemOnly = false; }
        return ResponseEntity.ok(backgroundJobHistoryService.list(viewer, ownerUserId, systemOnly, page, size, true, currentAccount, view));
    }

    @PostMapping("/{jobId}/ignore")
    public ResponseEntity<Map<String, Object>> ignore(@RequestHeader("Authorization") String authorization, @PathVariable Long jobId) {
        return ResponseEntity.ok(backgroundJobService.ignore(currentUser(authorization), jobId));
    }

    @PostMapping("/scans/{taskId}/ignore")
    public ResponseEntity<Map<String, Object>> ignoreScan(@RequestHeader("Authorization") String authorization, @PathVariable Long taskId) {
        return ResponseEntity.ok(scanTaskService.ignoreTask(currentUser(authorization), taskId));
    }

    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> history(@RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(defaultValue = "false") boolean systemOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(backgroundJobHistoryService.list(currentUser(authorization), ownerUserId, systemOnly, page, size));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@RequestHeader("Authorization") String authorization,
                                                           @RequestParam(required = false) Long ownerUserId,
                                                           @RequestParam(required = false) String jobType) {
        UserAccount user = currentUser(authorization);
        return ResponseEntity.ok(jobType == null || jobType.isBlank()
            ? backgroundJobService.list(user, ownerUserId)
            : backgroundJobService.listByType(user, ownerUserId, jobType));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<Map<String, Object>> get(@RequestHeader("Authorization") String authorization,
                                                    @PathVariable Long jobId) {
        return ResponseEntity.ok(backgroundJobService.get(currentUser(authorization), jobId));
    }

    @PostMapping("/{jobId}/pause")
    public ResponseEntity<Map<String, Object>> pause(@RequestHeader("Authorization") String authorization,
                                                      @PathVariable Long jobId) {
        return ResponseEntity.ok(backgroundJobService.pause(currentUser(authorization), jobId));
    }

    @PostMapping("/{jobId}/resume")
    public ResponseEntity<Map<String, Object>> resume(@RequestHeader("Authorization") String authorization,
                                                       @PathVariable Long jobId) {
        return ResponseEntity.ok(backgroundJobService.resume(currentUser(authorization), jobId));
    }

    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@RequestHeader("Authorization") String authorization,
                                                       @PathVariable Long jobId) {
        return ResponseEntity.ok(backgroundJobService.cancel(currentUser(authorization), jobId));
    }

    @PostMapping("/{jobId}/retry")
    public ResponseEntity<Map<String, Object>> retry(@RequestHeader("Authorization") String authorization,
                                                      @PathVariable Long jobId,
                                                      @RequestBody(required = false) Map<String, Object> body) {
        boolean includeHistorical = body != null && Boolean.TRUE.equals(body.get("includeHistorical"));
        return ResponseEntity.ok(backgroundJobService.retry(currentUser(authorization), jobId, includeHistorical));
    }

    @PostMapping("/retry-failed")
    public ResponseEntity<Map<String, Object>> retryFailed(@RequestHeader("Authorization") String authorization,
                                                            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> request = body == null ? Map.of() : body;
        Long ownerUserId = request.get("ownerUserId") == null ? null : Long.valueOf(String.valueOf(request.get("ownerUserId")));
        String jobType = request.get("jobType") == null ? null : String.valueOf(request.get("jobType"));
        String failureGroupId = request.get("failureGroupId") == null ? null : String.valueOf(request.get("failureGroupId"));
        boolean includeHistorical = Boolean.TRUE.equals(request.get("includeHistorical"));
        return ResponseEntity.ok(backgroundJobService.retryFailedBatch(currentUser(authorization), ownerUserId,
            jobType, failureGroupId, includeHistorical));
    }

    @PostMapping("/control/pause-all")
    public ResponseEntity<Map<String, Object>> pauseAll(@RequestHeader("Authorization") String authorization) {
        UserAccount user = currentUser(authorization);
        Map<String, Object> result = new java.util.LinkedHashMap<>(backgroundJobService.setGlobalPaused(user, true));
        result.putAll(scanTaskService.pauseForControl(null, "GLOBAL"));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/control/resume-all")
    public ResponseEntity<Map<String, Object>> resumeAll(@RequestHeader("Authorization") String authorization) {
        UserAccount user = currentUser(authorization);
        Map<String, Object> result = new java.util.LinkedHashMap<>(backgroundJobService.setGlobalPaused(user, false));
        result.putAll(scanTaskService.resumeForControl(null, "GLOBAL"));
        scanTaskService.wakePendingScans();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/control/users/{ownerUserId}/pause")
    public ResponseEntity<Map<String, Object>> pauseOwner(@RequestHeader("Authorization") String authorization,
                                                          @PathVariable Long ownerUserId) {
        UserAccount user = currentUser(authorization);
        Map<String, Object> result = new java.util.LinkedHashMap<>(backgroundJobService.setOwnerPaused(user, ownerUserId, true));
        result.putAll(scanTaskService.pauseForControl(ownerUserId, "OWNER"));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/control/users/{ownerUserId}/resume")
    public ResponseEntity<Map<String, Object>> resumeOwner(@RequestHeader("Authorization") String authorization,
                                                           @PathVariable Long ownerUserId) {
        UserAccount user = currentUser(authorization);
        Map<String, Object> result = new java.util.LinkedHashMap<>(backgroundJobService.setOwnerPaused(user, ownerUserId, false));
        result.putAll(scanTaskService.resumeForControl(ownerUserId, "OWNER"));
        scanTaskService.wakePendingScans();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/control/lanes/{lane}/pause")
    public ResponseEntity<Map<String, Object>> pauseLane(@RequestHeader("Authorization") String authorization,
                                                         @PathVariable BackgroundJobResourceLane lane) {
        return ResponseEntity.ok(backgroundJobService.setLanePaused(currentUser(authorization), lane, true));
    }

    @PostMapping("/control/lanes/{lane}/resume")
    public ResponseEntity<Map<String, Object>> resumeLane(@RequestHeader("Authorization") String authorization,
                                                         @PathVariable BackgroundJobResourceLane lane) {
        Map<String, Object> result = backgroundJobService.setLanePaused(currentUser(authorization), lane, false);
        if (lane == BackgroundJobResourceLane.SCAN_IO) scanTaskService.wakePendingScans();
        return ResponseEntity.ok(result);
    }

    private UserAccount currentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new SecurityException("未授权");
        }
        return authService.getCurrentUserEntity(authorization.substring(7));
    }
}
