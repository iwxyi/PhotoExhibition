package com.photoexhibition.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.PhotoVisualAnalysisJob;
import com.photoexhibition.entity.PhotoVisualAnalysisJobItem;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.entity.BackgroundJobResourceLane;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.PhotoVisualAnalysisJobRepository;
import com.photoexhibition.repository.PhotoVisualAnalysisJobItemRepository;
import com.photoexhibition.repository.PhotoVisualAnalysisRepository;
import com.photoexhibition.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Compatibility view and migration for visual-analysis jobs created before unified dispatch. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoVisualAnalysisJobService {
    private static final int MAX_PHOTOS_PER_REQUEST = 500;
    private final PhotoVisualAnalysisJobRepository jobRepository;
    private final PhotoVisualAnalysisJobItemRepository jobItemRepository;
    private final PhotoVisualAnalysisRepository analysisRepository;
    private final PhotoRepository photoRepository;
    private final UserAccountRepository userAccountRepository;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private final BackgroundJobService backgroundJobService;
    @PostConstruct
    public void recover() {
        migratePendingJobs();
    }

    public Map<String, Object> enqueue(UserAccount requester, List<Long> requestedIds, boolean force) {
        if (!systemConfigService.isAiVisualAnalysisEnabled()) throw new IllegalStateException("AI图片视觉分析未启用");
        if (isBlank(systemConfigService.getAiSearchApiUrl()) || isBlank(systemConfigService.getAiSearchApiKey())) {
            throw new IllegalStateException("AI提供商配置不完整");
        }
        if (requestedIds == null || requestedIds.isEmpty()) throw new IllegalArgumentException("请先选择要分析的照片");
        List<Long> ids = requestedIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) throw new IllegalArgumentException("请先选择要分析的照片");
        if (ids.size() > MAX_PHOTOS_PER_REQUEST) throw new IllegalArgumentException("单次最多加入 " + MAX_PHOTOS_PER_REQUEST + " 张照片");
        Map<Long, List<Long>> idsByOwner = new LinkedHashMap<>();
        for (Long id : ids) {
            Photo photo = photoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("照片不存在: " + id));
            if (photo.getUserId() == null) throw new IllegalArgumentException("照片缺少归属用户: " + id);
            if (requester.getRole() != UserRole.SUPER_ADMIN && !Objects.equals(photo.getUserId(), requester.getId())) throw new SecurityException("无权分析照片: " + id);
            idsByOwner.computeIfAbsent(photo.getUserId(), ignored -> new ArrayList<>()).add(id);
        }
        List<Map<String, Object>> jobs = new ArrayList<>();
        int queuedPhotos = 0;
        for (Map.Entry<Long, List<Long>> entry : idsByOwner.entrySet()) {
            Map<String, Object> queued = backgroundJobService.enqueuePhotoJob(
                requester, entry.getKey(), "VISUAL_ANALYSIS", BackgroundJobResourceLane.REMOTE_AI,
                entry.getValue(), force, false, 100, "1", null,
                Map.of("forceReanalyze", force));
            jobs.add(queued);
            queuedPhotos += ((Number) queued.getOrDefault("acceptedItems", 0)).intValue();
        }
        return Map.of("success", true, "jobs", jobs, "queuedPhotos", queuedPhotos,
            "notQueuedPhotos", ids.size() - queuedPhotos, "message", "已加入 AI 分析队列");
    }

    public List<Map<String, Object>> list(UserAccount user) {
        List<Map<String, Object>> unified = new ArrayList<>(backgroundJobService.listByType(user, null, "VISUAL_ANALYSIS"));
        List<PhotoVisualAnalysisJob> jobs = user.getRole() == UserRole.SUPER_ADMIN
            ? jobRepository.findAll().stream().sorted(Comparator.comparing(PhotoVisualAnalysisJob::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).limit(50).collect(Collectors.toList())
            : jobRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId());
        jobs.stream().map(this::toMap).forEach(item -> {
            item.put("id", -((Number) item.get("id")).longValue());
            item.put("legacyJob", true);
            unified.add(item);
        });
        return unified.stream()
            .sorted(Comparator.comparing(item -> (LocalDateTime) item.get("createdAt"), Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(50)
            .collect(Collectors.toList());
    }

    public Map<String, Object> get(UserAccount user, Long jobId) {
        if (jobId != null && jobId > 0) {
            Map<String, Object> job = backgroundJobService.get(user, jobId);
            if (!"VISUAL_ANALYSIS".equals(job.get("jobType"))) throw new IllegalArgumentException("不是 AI 分析任务");
            return job;
        }
        jobId = jobId == null ? null : Math.abs(jobId);
        PhotoVisualAnalysisJob job = requireVisibleJob(user, jobId);
        ensureItems(job);
        Map<String, Object> result = toMap(job);
        result.put("id", -job.getId());
        result.put("legacyJob", true);
        result.put("items", jobItemRepository.findByJobIdOrderByIdAsc(jobId).stream().map(this::itemToMap).collect(Collectors.toList()));
        return result;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void pollQueue() { migratePendingJobs(); }

    private void migratePendingJobs() {
        List<PhotoVisualAnalysisJob> pending = new ArrayList<>(jobRepository.findByStatusOrderByCreatedAtAsc("QUEUED"));
        pending.addAll(jobRepository.findByStatusOrderByCreatedAtAsc("RUNNING"));
        for (PhotoVisualAnalysisJob job : pending) {
            try {
                ensureItems(job);
                Set<Long> finished = jobItemRepository.findByJobIdOrderByIdAsc(job.getId()).stream()
                    .filter(item -> "COMPLETED".equals(item.getStatus()) || "SKIPPED".equals(item.getStatus()))
                    .map(PhotoVisualAnalysisJobItem::getPhotoId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
                List<Long> remaining = readIds(job.getPhotoIdsJson()).stream().filter(Objects::nonNull)
                    .filter(id -> !finished.contains(id)).distinct().collect(Collectors.toList());
                UserAccount owner = userAccountRepository.findById(job.getUserId())
                    .orElseThrow(() -> new IllegalStateException("视觉分析任务所属账号不存在"));
                if (!remaining.isEmpty()) {
                    backgroundJobService.enqueuePhotoJob(owner, job.getUserId(), "VISUAL_ANALYSIS",
                        BackgroundJobResourceLane.REMOTE_AI, remaining, false, true, 100, "1", null,
                        Map.of("forceReanalyze", Boolean.TRUE.equals(job.getForceReanalyze()), "legacyJobId", job.getId()));
                }
                job.setStatus("MIGRATED");
                job.setErrorMessage("未完成项已迁入统一后台任务");
                job.setFinishedAt(LocalDateTime.now());
                jobRepository.save(job);
            } catch (Exception e) {
                log.error("迁移旧视觉分析任务失败，稍后重试: jobId={}", job.getId(), e);
            }
        }
    }

    private List<Long> readIds(String json) { try { return objectMapper.readValue(json, new TypeReference<List<Long>>() {}); } catch (Exception e) { throw new IllegalStateException("任务照片列表损坏", e); } }
    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private PhotoVisualAnalysisJob requireVisibleJob(UserAccount user, Long jobId) {
        PhotoVisualAnalysisJob job = jobRepository.findById(jobId).orElseThrow(() -> new IllegalArgumentException("AI 分析任务不存在"));
        if (user.getRole() != UserRole.SUPER_ADMIN && !Objects.equals(job.getUserId(), user.getId())) throw new SecurityException("无权查看该任务");
        return job;
    }
    private void ensureItems(PhotoVisualAnalysisJob job) {
        if (jobItemRepository.countByJobId(job.getId()) > 0) return;
        for (Long photoId : readIds(job.getPhotoIdsJson())) {
            PhotoVisualAnalysisJobItem item = new PhotoVisualAnalysisJobItem();
            item.setJobId(job.getId()); item.setPhotoId(photoId);
            photoRepository.findById(photoId).ifPresent(photo -> item.setPhotoName(photo.getFilename()));
            if ("COMPLETED".equals(job.getStatus())) {
                analysisRepository.findByPhotoId(photoId).ifPresentOrElse(analysis -> {
                    item.setStatus(analysis.getStatus()); item.setErrorMessage(analysis.getErrorMessage()); item.setFinishedAt(analysis.getUpdatedAt());
                }, () -> item.setStatus("FAILED"));
            }
            jobItemRepository.save(item);
        }
    }
    private Map<String, Object> itemToMap(PhotoVisualAnalysisJobItem item) { Map<String, Object> m = new LinkedHashMap<>(); m.put("id", item.getId()); m.put("photoId", item.getPhotoId()); m.put("photoName", item.getPhotoName()); m.put("status", item.getStatus()); m.put("errorMessage", item.getErrorMessage()); analysisRepository.findByPhotoId(item.getPhotoId()).ifPresent(analysis -> { m.put("caption", analysis.getCaption()); m.put("analysisJson", analysis.getAnalysisJson()); }); m.put("startedAt", item.getStartedAt()); m.put("finishedAt", item.getFinishedAt()); m.put("updatedAt", item.getUpdatedAt()); return m; }
    private Map<String, Object> toMap(PhotoVisualAnalysisJob j) { Map<String, Object> m = new LinkedHashMap<>(); int processed = Optional.ofNullable(j.getProcessedItems()).orElse(0); int failed = Optional.ofNullable(j.getFailedItems()).orElse(0); int skipped = Optional.ofNullable(j.getSkippedItems()).orElse(0); int total = Optional.ofNullable(j.getTotalItems()).orElse(0); m.put("id", j.getId()); m.put("userId", j.getUserId()); m.put("requestedByUserId", j.getRequestedByUserId()); m.put("status", j.getStatus()); m.put("totalItems", total); m.put("processedItems", processed); m.put("succeededItems", Math.max(0, processed - failed - skipped)); m.put("waitingItems", Math.max(0, total - processed)); m.put("skippedItems", skipped); m.put("failedItems", failed); m.put("lastPhotoId", j.getLastPhotoId()); m.put("errorMessage", j.getErrorMessage());
        List<PhotoVisualAnalysisJobItem> items = jobItemRepository.findByJobIdOrderByIdAsc(j.getId());
        m.put("createdAt", j.getCreatedAt()); m.put("startedAt", j.getStartedAt()); m.put("finishedAt", j.getFinishedAt()); return m; }
}
