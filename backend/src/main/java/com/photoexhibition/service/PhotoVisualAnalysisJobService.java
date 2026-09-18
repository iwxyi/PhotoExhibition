package com.photoexhibition.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.PhotoVisualAnalysisJob;
import com.photoexhibition.entity.PhotoVisualAnalysisJobItem;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
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
import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/** One process-wide worker protects the shared visual-model API from burst traffic. */
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
    private final PhotoVisualAnalysisService visualAnalysisService;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "photo-visual-analysis-worker"); t.setDaemon(true); return t;
    });
    private final AtomicBoolean workerScheduled = new AtomicBoolean(false);

    @PostConstruct
    public void recover() {
        jobRepository.findByStatusOrderByCreatedAtAsc("RUNNING").forEach(job -> {
            job.setStatus("QUEUED"); job.setErrorMessage("服务重启，任务已重新排队"); job.setStartedAt(null); jobRepository.save(job);
        });
        scheduleWorker();
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
        for (Map.Entry<Long, List<Long>> entry : idsByOwner.entrySet()) {
            PhotoVisualAnalysisJob job = new PhotoVisualAnalysisJob();
            job.setUserId(entry.getKey()); job.setRequestedByUserId(requester.getId()); job.setForceReanalyze(force);
            job.setTotalItems(entry.getValue().size()); job.setPhotoIdsJson(writeIds(entry.getValue()));
            jobRepository.save(job);
            for (Long photoId : entry.getValue()) {
                Photo photo = photoRepository.findById(photoId).orElseThrow(() -> new IllegalArgumentException("照片不存在: " + photoId));
                PhotoVisualAnalysisJobItem item = new PhotoVisualAnalysisJobItem();
                item.setJobId(job.getId()); item.setPhotoId(photoId); item.setPhotoName(photo.getFilename());
                jobItemRepository.save(item);
            }
            jobs.add(toMap(job));
        }
        scheduleWorker();
        return Map.of("success", true, "jobs", jobs, "queuedPhotos", ids.size(), "message", "已加入 AI 分析队列");
    }

    public List<Map<String, Object>> list(UserAccount user) {
        List<PhotoVisualAnalysisJob> jobs = user.getRole() == UserRole.SUPER_ADMIN
            ? jobRepository.findAll().stream().sorted(Comparator.comparing(PhotoVisualAnalysisJob::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).limit(50).collect(Collectors.toList())
            : jobRepository.findTop50ByUserIdOrderByCreatedAtDesc(user.getId());
        return jobs.stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> get(UserAccount user, Long jobId) {
        PhotoVisualAnalysisJob job = requireVisibleJob(user, jobId);
        ensureItems(job);
        Map<String, Object> result = toMap(job);
        result.put("items", jobItemRepository.findByJobIdOrderByIdAsc(jobId).stream().map(this::itemToMap).collect(Collectors.toList()));
        return result;
    }

    @Scheduled(fixedDelay = 5000, initialDelay = 5000)
    public void pollQueue() { scheduleWorker(); }

    private void scheduleWorker() {
        if (workerScheduled.compareAndSet(false, true)) worker.submit(() -> {
            try {
                while (true) {
                    Optional<PhotoVisualAnalysisJob> next = jobRepository.findByStatusOrderByCreatedAtAsc("QUEUED").stream().findFirst();
                    if (next.isEmpty()) return;
                    process(next.get().getId());
                }
            } finally { workerScheduled.set(false); }
        });
    }

    private void process(Long jobId) {
        PhotoVisualAnalysisJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || !"QUEUED".equals(job.getStatus())) return;
        job.setStatus("RUNNING"); job.setStartedAt(LocalDateTime.now()); job.setErrorMessage(null); jobRepository.save(job);
        try {
            UserAccount owner = userAccountRepository.findById(job.getUserId()).orElseThrow(() -> new IllegalStateException("照片所属用户不存在"));
            ensureItems(job);
            for (Long photoId : readIds(job.getPhotoIdsJson())) {
                PhotoVisualAnalysisJobItem item = jobItemRepository.findByJobIdAndPhotoId(jobId, photoId).orElseThrow(() -> new IllegalStateException("任务照片明细不存在"));
                item.setStatus("RUNNING"); item.setStartedAt(LocalDateTime.now()); item.setErrorMessage(null); jobItemRepository.save(item);
                try {
                    if (!Boolean.TRUE.equals(job.getForceReanalyze()) && visualAnalysisService.isCompleted(photoId)) {
                        job.setSkippedItems(job.getSkippedItems() + 1);
                        item.setStatus("SKIPPED");
                    } else {
                        visualAnalysisService.analyze(photoId, owner);
                        item.setStatus("COMPLETED");
                    }
                } catch (Exception e) {
                    job.setFailedItems(job.getFailedItems() + 1);
                    job.setErrorMessage(shortMessage(e));
                    item.setStatus("FAILED"); item.setErrorMessage(shortMessage(e));
                    log.warn("视觉分析任务 {} 的照片 {} 失败: {}", jobId, photoId, e.getMessage());
                }
                item.setFinishedAt(LocalDateTime.now()); jobItemRepository.save(item);
                job.setProcessedItems(job.getProcessedItems() + 1); job.setLastPhotoId(photoId); jobRepository.save(job);
            }
            job.setStatus("COMPLETED"); job.setFinishedAt(LocalDateTime.now()); jobRepository.save(job);
        } catch (Exception e) {
            job.setStatus("FAILED"); job.setErrorMessage(shortMessage(e)); job.setFinishedAt(LocalDateTime.now()); jobRepository.save(job);
            log.error("视觉分析任务 {} 失败", jobId, e);
        }
    }

    private String writeIds(List<Long> ids) { try { return objectMapper.writeValueAsString(ids); } catch (Exception e) { throw new IllegalStateException("无法保存任务照片列表", e); } }
    private List<Long> readIds(String json) { try { return objectMapper.readValue(json, new TypeReference<List<Long>>() {}); } catch (Exception e) { throw new IllegalStateException("任务照片列表损坏", e); } }
    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private String shortMessage(Exception e) { String value = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); return value.length() > 1000 ? value.substring(0, 1000) : value; }
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
    @PreDestroy public void shutdown() { worker.shutdownNow(); }
}
