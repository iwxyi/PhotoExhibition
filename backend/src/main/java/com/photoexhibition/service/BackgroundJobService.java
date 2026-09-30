package com.photoexhibition.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.*;
import com.photoexhibition.repository.BackgroundJobControlRepository;
import com.photoexhibition.repository.BackgroundJobItemRepository;
import com.photoexhibition.repository.BackgroundJobRepository;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackgroundJobService {
    private static final int CLAIM_SIZE = 20;
    private static final List<BackgroundJobStatus> DISPATCHABLE_JOB_STATUSES = List.of(
        BackgroundJobStatus.QUEUED, BackgroundJobStatus.WAITING_DEPENDENCY);
    private static final List<BackgroundJobItemStatus> PENDING_ITEM_STATUSES = List.of(
        BackgroundJobItemStatus.QUEUED, BackgroundJobItemStatus.WAITING_DEPENDENCY);
    private static final List<BackgroundJobItemStatus> DEDUP_ITEM_STATUSES = List.of(
        BackgroundJobItemStatus.QUEUED, BackgroundJobItemStatus.WAITING_DEPENDENCY,
        BackgroundJobItemStatus.RUNNING, BackgroundJobItemStatus.BLOCKED, BackgroundJobItemStatus.SUCCEEDED);
    private static final List<BackgroundJobItemStatus> ACTIVE_ITEM_STATUSES = List.of(
        BackgroundJobItemStatus.QUEUED, BackgroundJobItemStatus.WAITING_DEPENDENCY,
        BackgroundJobItemStatus.RUNNING, BackgroundJobItemStatus.BLOCKED);

    private final BackgroundJobRepository jobRepository;
    private final BackgroundJobItemRepository itemRepository;
    private final BackgroundJobControlRepository controlRepository;
    private final PhotoRepository photoRepository;
    private final AlbumRepository albumRepository;
    private final UserAccountRepository userAccountRepository;
    private final PhotoVisualAnalysisService visualAnalysisService;
    private final PhotoScanService photoScanService;
    private final PhotoAIScoringService photoAIScoringService;
    private final ObjectMapper objectMapper;

    @Autowired @Lazy
    private ModelManagementService modelManagementService;

    private final Map<BackgroundJobResourceLane, ExecutorService> executors = new EnumMap<>(BackgroundJobResourceLane.class);
    private final Map<BackgroundJobResourceLane, AtomicBoolean> laneBusy = new EnumMap<>(BackgroundJobResourceLane.class);
    private final Map<BackgroundJobResourceLane, Long> lastOwnerByLane = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        jobRepository.markExistingRetrySources();
        recoverInterruptedWork();
        for (BackgroundJobResourceLane lane : BackgroundJobResourceLane.values()) {
            executors.put(lane, Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "background-job-" + lane.name().toLowerCase(Locale.ROOT));
                thread.setDaemon(true);
                return thread;
            }));
            laneBusy.put(lane, new AtomicBoolean(false));
        }
    }

    @PreDestroy
    public void shutdown() {
        executors.values().forEach(ExecutorService::shutdownNow);
    }

    @Transactional
    public Map<String, Object> enqueuePhotoJob(UserAccount requester,
                                               Long ownerUserId,
                                               String jobType,
                                               BackgroundJobResourceLane lane,
                                               Collection<Long> photoIds,
                                               boolean force,
                                               boolean requiresScanComplete,
                                               int priority,
                                               String pipelineVersion,
                                               Long sourceJobId,
                                               Map<String, Object> parameters) {
        Objects.requireNonNull(requester, "未授权");
        Long effectiveOwner = ownerUserId == null ? requester.getId() : ownerUserId;
        if (requester.getRole() != UserRole.SUPER_ADMIN && !Objects.equals(effectiveOwner, requester.getId())) {
            throw new SecurityException("无权为其他账号创建任务");
        }
        if (jobType == null || jobType.isBlank()) throw new IllegalArgumentException("任务类型不能为空");
        if (lane == null) throw new IllegalArgumentException("资源队列不能为空");
        String version = pipelineVersion == null || pipelineVersion.isBlank() ? "1" : pipelineVersion;
        Map<String, Object> normalizedParameters = parameters == null ? Map.of() : new TreeMap<>(parameters);
        String parametersJson = writeParameters(normalizedParameters);
        String parametersHash = sha256(parametersJson);
        List<Long> ids = photoIds == null ? List.of() : photoIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) throw new IllegalArgumentException("任务没有照片");

        BackgroundJob job = new BackgroundJob();
        job.setJobType(jobType);
        job.setResourceLane(lane);
        job.setOwnerUserId(effectiveOwner);
        job.setRequestedByUserId(requester.getId());
        job.setPriority(priority);
        job.setPipelineVersion(version);
        job.setForceReprocess(force);
        job.setSourceJobId(sourceJobId);
        job.setParametersJson(parametersJson);
        job.setParametersHash(parametersHash);
        jobRepository.save(job);

        int accepted = 0;
        int deduplicated = 0;
        int notScanned = 0;
        int missing = 0;
        for (Long photoId : ids) {
            Photo photo = photoRepository.findById(photoId).orElse(null);
            if (photo == null) {
                missing++;
                continue;
            }
            if (!Objects.equals(photo.getUserId(), effectiveOwner)) {
                if (requester.getRole() == UserRole.SUPER_ADMIN) {
                    throw new IllegalArgumentException("照片 #" + photoId + " 不属于指定账号");
                }
                throw new SecurityException("无权处理照片 #" + photoId);
            }
            if (photo.getProcessingStatus() != ProcessingStatus.COMPLETED) {
                notScanned++;
                continue;
            }
            String targetKey = "PHOTO:" + photoId;
            if (!force && itemRepository
                .findFirstByOwnerUserIdAndTargetKeyAndStageAndPipelineVersionAndParametersHashAndStatusInOrderByUpdatedAtDesc(
                    effectiveOwner, targetKey, jobType, version, parametersHash,
                    ("BACKGROUND_CACHE_CLEAR".equals(jobType) || "BACKGROUND_REMOVAL".equals(jobType))
                        ? ACTIVE_ITEM_STATUSES : DEDUP_ITEM_STATUSES)
                .isPresent()) {
                deduplicated++;
                continue;
            }
            BackgroundJobItem item = new BackgroundJobItem();
            item.setJobId(job.getId());
            item.setOwnerUserId(effectiveOwner);
            item.setPhotoId(photoId);
            item.setTargetKey(targetKey);
            item.setStage(jobType);
            item.setPipelineVersion(version);
            item.setParametersHash(parametersHash);
            item.setRequiresScanComplete(false);
            itemRepository.save(item);
            accepted++;
        }

        job.setTotalItems(accepted);
        if (accepted == 0) {
            job.setStatus(BackgroundJobStatus.SKIPPED);
            job.setSkippedItems(deduplicated + notScanned + missing);
            job.setFinishedAt(LocalDateTime.now());
            job.setErrorSummary("所有照片均已完成或已在队列中");
        }
        jobRepository.save(job);
        Map<String, Object> result = toJobMap(job, false);
        result.put("acceptedItems", accepted);
        result.put("deduplicatedItems", deduplicated);
        result.put("notScannedItems", notScanned);
        result.put("missingItems", missing);
        return result;
    }

    @Transactional
    public Map<String, Object> enqueueAlbumJob(UserAccount requester, Long ownerUserId, String jobType,
                                               Collection<Long> albumIds, boolean force) {
        return enqueueAlbumJob(requester, ownerUserId, jobType, albumIds, force, null);
    }

    @Transactional
    public Map<String, Object> enqueueAlbumJob(UserAccount requester, Long ownerUserId, String jobType,
                                               Collection<Long> albumIds, boolean force, Long sourceJobId) {
        Objects.requireNonNull(requester, "未授权");
        if (!"ALBUM_ATMOSPHERE_REBUILD".equals(jobType)) throw new IllegalArgumentException("未知相册任务类型");
        Long effectiveOwner = ownerUserId == null ? requester.getId() : ownerUserId;
        if (requester.getRole() != UserRole.SUPER_ADMIN && !Objects.equals(effectiveOwner, requester.getId())) {
            throw new SecurityException("无权为其他账号创建任务");
        }
        List<Long> ids = albumIds == null ? List.of() : albumIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (ids.isEmpty()) throw new IllegalArgumentException("任务没有相册");
        String parametersJson = writeParameters(Map.of());
        String hash = sha256(parametersJson);
        BackgroundJob job = new BackgroundJob();
        job.setJobType(jobType);
        job.setResourceLane(BackgroundJobResourceLane.MAINTENANCE);
        job.setOwnerUserId(effectiveOwner);
        job.setRequestedByUserId(requester.getId());
        job.setPriority(100);
        job.setPipelineVersion("1");
        job.setForceReprocess(force);
        job.setSourceJobId(sourceJobId);
        job.setParametersJson(parametersJson);
        job.setParametersHash(hash);
        jobRepository.save(job);
        int accepted = 0;
        int missing = 0;
        int deduplicated = 0;
        for (Long albumId : ids) {
            Album album = albumRepository.findById(albumId).orElse(null);
            if (album == null) { missing++; continue; }
            if (!Objects.equals(album.getUserId(), effectiveOwner)) throw new SecurityException("相册不属于指定账号");
            String targetKey = "ALBUM:" + albumId;
            if (!force && itemRepository.findFirstByOwnerUserIdAndTargetKeyAndStageAndPipelineVersionAndParametersHashAndStatusInOrderByUpdatedAtDesc(
                effectiveOwner, targetKey, jobType, "1", hash, ACTIVE_ITEM_STATUSES).isPresent()) {
                deduplicated++;
                continue;
            }
            BackgroundJobItem item = new BackgroundJobItem();
            item.setJobId(job.getId());
            item.setOwnerUserId(effectiveOwner);
            item.setTargetKey(targetKey);
            item.setStage(jobType);
            item.setPipelineVersion("1");
            item.setParametersHash(hash);
            item.setRequiresScanComplete(false);
            itemRepository.save(item);
            accepted++;
        }
        job.setTotalItems(accepted);
        if (accepted == 0) {
            job.setStatus(BackgroundJobStatus.SKIPPED);
            job.setSkippedItems(missing + deduplicated);
            job.setFinishedAt(LocalDateTime.now());
        }
        jobRepository.save(job);
        Map<String, Object> result = toJobMap(job, false);
        result.put("acceptedItems", accepted);
        result.put("missingItems", missing);
        result.put("deduplicatedItems", deduplicated);
        return result;
    }

    public List<Map<String, Object>> list(UserAccount viewer, Long ownerUserId) {
        requireViewer(viewer);
        List<BackgroundJob> jobs;
        if (viewer.getRole() == UserRole.SUPER_ADMIN && ownerUserId == null) {
            jobs = jobRepository.findTop200ByOrderByCreatedAtDesc();
        } else {
            Long visibleOwner = ownerUserId == null ? viewer.getId() : ownerUserId;
            if (viewer.getRole() != UserRole.SUPER_ADMIN && !Objects.equals(visibleOwner, viewer.getId())) {
                throw new SecurityException("无权查看其他账号任务");
            }
            jobs = jobRepository.findTop100ByOwnerUserIdOrderByCreatedAtDesc(visibleOwner);
        }
        List<Map<String, Object>> result = jobs.stream().map(job -> toJobMap(job, false)).collect(Collectors.toList());
        Set<Long> ownerIds = jobs.stream().map(BackgroundJob::getOwnerUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!ownerIds.isEmpty()) {
            Map<Long, UserAccountRepository.UserIdentityProjection> owners = userAccountRepository.findIdentityByIdIn(ownerIds)
                .stream().collect(Collectors.toMap(UserAccountRepository.UserIdentityProjection::getId, identity -> identity));
            result.forEach(item -> {
                UserAccountRepository.UserIdentityProjection identity = owners.get(item.get("ownerUserId"));
                if (identity != null) {
                    item.put("ownerUsername", identity.getUsername());
                    item.put("ownerNickname", identity.getNickname());
                    item.put("ownerLabel", identity.getNickname() == null || identity.getNickname().isBlank()
                        ? identity.getUsername() : identity.getNickname());
                }
            });
        }
        return result;
    }

    public List<Map<String, Object>> listByType(UserAccount viewer, Long ownerUserId, String jobType) {
        return list(viewer, ownerUserId).stream()
            .filter(job -> Objects.equals(jobType, job.get("jobType")))
            .collect(Collectors.toList());
    }

    public long activeCount(UserAccount viewer, String jobType) {
        requireViewer(viewer);
        List<BackgroundJobStatus> statuses = List.of(BackgroundJobStatus.QUEUED,
            BackgroundJobStatus.WAITING_DEPENDENCY, BackgroundJobStatus.RUNNING,
            BackgroundJobStatus.PAUSED, BackgroundJobStatus.BLOCKED);
        return viewer.getRole() == UserRole.SUPER_ADMIN
            ? jobRepository.countByJobTypeAndStatusIn(jobType, statuses)
            : jobRepository.countByOwnerUserIdAndJobTypeAndStatusIn(viewer.getId(), jobType, statuses);
    }

    public Map<String, Object> get(UserAccount viewer, Long jobId) {
        BackgroundJob job = requireVisibleJob(viewer, jobId);
        return toJobMap(job, true);
    }

    public Map<String, Object> getSummary(UserAccount viewer, Long jobId) {
        return toJobMap(requireVisibleJob(viewer, jobId), false);
    }

    @Transactional
    public Map<String, Object> pause(UserAccount actor, Long jobId) {
        BackgroundJob job = requireVisibleJob(actor, jobId);
        if (job.getStatus().isTerminal()) return toJobMap(job, false);
        job.setPauseRequested(true);
        if (job.getStatus() != BackgroundJobStatus.RUNNING) job.setStatus(BackgroundJobStatus.PAUSED);
        jobRepository.save(job);
        return toJobMap(job, false);
    }

    @Transactional
    public Map<String, Object> resume(UserAccount actor, Long jobId) {
        BackgroundJob job = requireVisibleJob(actor, jobId);
        if (job.getStatus().isTerminal()) throw new IllegalStateException("终态任务不能恢复，请使用重试");
        if (job.getStatus() == BackgroundJobStatus.BLOCKED) {
            throw new IllegalStateException("阻塞任务请先恢复资源后重试失败项");
        }
        job.setPauseRequested(false);
        job.setBlockingReason(null);
        job.setStatus(BackgroundJobStatus.QUEUED);
        jobRepository.save(job);
        return toJobMap(job, false);
    }

    @Transactional
    public Map<String, Object> cancel(UserAccount actor, Long jobId) {
        BackgroundJob job = requireVisibleJob(actor, jobId);
        if (job.getStatus().isTerminal()) return toJobMap(job, false);
        job.setCancelRequested(true);
        if (job.getStatus() != BackgroundJobStatus.RUNNING) finishCanceled(job);
        jobRepository.save(job);
        return toJobMap(job, false);
    }

    @Transactional
    public Map<String, Object> retry(UserAccount actor, Long jobId, boolean includeHistorical) {
        requireViewer(actor);
        BackgroundJob source = jobRepository.findForUpdate(jobId)
            .orElseThrow(() -> new IllegalArgumentException("后台任务不存在"));
        if (actor.getRole() != UserRole.SUPER_ADMIN && !Objects.equals(actor.getId(), source.getOwnerUserId())) {
            throw new SecurityException("无权访问该任务");
        }
        if (source.getStatus() == BackgroundJobStatus.IGNORED) throw new IllegalStateException("已忽略的任务不能重试，请创建新任务");
        Optional<BackgroundJob> existing = jobRepository.findFirstBySourceJobIdOrderByIdAsc(jobId);
        if (existing.isPresent()) {
            source.setStatus(BackgroundJobStatus.RETRIED);
            jobRepository.save(source);
            return toJobMap(existing.get(), false);
        }
        if (source.getStatus() != BackgroundJobStatus.FAILED && source.getStatus() != BackgroundJobStatus.PARTIAL_SUCCESS
            && source.getStatus() != BackgroundJobStatus.BLOCKED) throw new IllegalStateException("只能重试失败任务");
        List<BackgroundJobItem> candidates = itemRepository.findByJobIdOrderByIdAsc(jobId).stream()
            .filter(item -> Boolean.TRUE.equals(item.getRetryable()))
            .filter(item -> item.getStatus() == BackgroundJobItemStatus.FAILED
                || item.getStatus() == BackgroundJobItemStatus.BLOCKED
                || (includeHistorical && item.getStatus() == BackgroundJobItemStatus.SKIPPED))
            .collect(Collectors.toList());
        if (candidates.isEmpty()) throw new IllegalStateException("没有可重试的失败项");
        if (source.getStatus() == BackgroundJobStatus.BLOCKED) {
            setControl("LANE", source.getResourceLane().name(), false, actor.getId());
        }
        Map<String, Object> result;
        if ("ALBUM_ATMOSPHERE_REBUILD".equals(source.getJobType())) {
            List<Long> albumIds = candidates.stream().map(BackgroundJobItem::getTargetKey).filter(Objects::nonNull)
                .filter(key -> key.startsWith("ALBUM:")).map(key -> Long.valueOf(key.substring(6))).collect(Collectors.toList());
            result = enqueueAlbumJob(actor, source.getOwnerUserId(), source.getJobType(), albumIds, true, source.getId());
        } else {
            result = enqueuePhotoJob(actor, source.getOwnerUserId(), source.getJobType(), source.getResourceLane(), candidates.stream()
            .map(BackgroundJobItem::getPhotoId).collect(Collectors.toList()), true, false,
            source.getPriority(), source.getPipelineVersion(), source.getId(), readParameters(source.getParametersJson()));
        }
        source.setStatus(BackgroundJobStatus.RETRIED);
        jobRepository.save(source);
        return result;
    }

    @Transactional
    public Map<String, Object> ignore(UserAccount actor, Long jobId) {
        BackgroundJob job = requireVisibleJob(actor, jobId);
        if (job.getStatus() == BackgroundJobStatus.IGNORED) return toJobMap(job, false);
        if (job.getStatus() != BackgroundJobStatus.FAILED && job.getStatus() != BackgroundJobStatus.PARTIAL_SUCCESS) {
            throw new IllegalStateException("只能忽略已结束的失败任务");
        }
        job.setStatus(BackgroundJobStatus.IGNORED);
        jobRepository.save(job);
        return toJobMap(job, false);
    }

    @Transactional
    public Map<String, Object> retryFailedBatch(UserAccount actor,
                                                Long ownerUserId,
                                                String jobType,
                                                String failureGroupId,
                                                boolean includeHistorical) {
        requireViewer(actor);
        Long effectiveOwner = ownerUserId == null && actor.getRole() == UserRole.SUPER_ADMIN ? null
            : (ownerUserId == null ? actor.getId() : ownerUserId);
        if (actor.getRole() != UserRole.SUPER_ADMIN && !Objects.equals(actor.getId(), effectiveOwner)) {
            throw new SecurityException("无权重试其他账号任务");
        }
        List<BackgroundJob> visibleSources = jobRepository.findByStatusInOrderByCreatedAtDesc(List.of(
            BackgroundJobStatus.FAILED, BackgroundJobStatus.PARTIAL_SUCCESS, BackgroundJobStatus.BLOCKED));
        List<BackgroundJob> sources = visibleSources.stream()
            .filter(job -> effectiveOwner == null || Objects.equals(effectiveOwner, job.getOwnerUserId()))
            .filter(job -> jobType == null || jobType.isBlank() || jobType.equals(job.getJobType()))
            .filter(job -> failureGroupId == null || failureGroupId.isBlank() || failureGroupId.equals(job.getFailureGroupId()))
            .filter(job -> job.getStatus() == BackgroundJobStatus.FAILED
                || job.getStatus() == BackgroundJobStatus.PARTIAL_SUCCESS
                || job.getStatus() == BackgroundJobStatus.BLOCKED)
            .collect(Collectors.toList());
        if (!includeHistorical && (failureGroupId == null || failureGroupId.isBlank()) && !sources.isEmpty()) {
            Map<String, String> latestGroupByOwnerAndType = new HashMap<>();
            List<BackgroundJob> latest = new ArrayList<>();
            for (BackgroundJob source : sources) {
                String key = source.getOwnerUserId() + ":" + source.getJobType();
                if (!latestGroupByOwnerAndType.containsKey(key)) {
                    latestGroupByOwnerAndType.put(key, source.getFailureGroupId());
                    latest.add(source);
                } else if (source.getFailureGroupId() != null
                    && source.getFailureGroupId().equals(latestGroupByOwnerAndType.get(key))) {
                    latest.add(source);
                }
            }
            sources = latest;
        }
        List<Map<String, Object>> retried = new ArrayList<>();
        for (BackgroundJob source : sources) {
            List<Long> photoIds = itemRepository.findByJobIdOrderByIdAsc(source.getId()).stream()
                .filter(item -> Boolean.TRUE.equals(item.getRetryable()))
                .filter(item -> item.getStatus() == BackgroundJobItemStatus.FAILED || item.getStatus() == BackgroundJobItemStatus.BLOCKED)
                .map(BackgroundJobItem::getPhotoId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
            List<Long> albumIds = itemRepository.findByJobIdOrderByIdAsc(source.getId()).stream()
                .filter(item -> Boolean.TRUE.equals(item.getRetryable()))
                .filter(item -> item.getStatus() == BackgroundJobItemStatus.FAILED || item.getStatus() == BackgroundJobItemStatus.BLOCKED)
                .map(BackgroundJobItem::getTargetKey).filter(key -> key != null && key.startsWith("ALBUM:"))
                .map(key -> Long.valueOf(key.substring(6))).distinct().collect(Collectors.toList());
            if (!photoIds.isEmpty() || !albumIds.isEmpty()) {
                retried.add(retry(actor, source.getId(), includeHistorical));
            }
        }
        if (retried.isEmpty()) throw new IllegalStateException("没有可重试的失败项");
        return Map.of("jobs", retried, "sourceJobCount", sources.size(), "message", "失败项已重新入队");
    }

    @Transactional
    public Map<String, Object> setGlobalPaused(UserAccount actor, boolean paused) {
        requireSuperAdmin(actor);
        Map<String, Object> result = new LinkedHashMap<>(setControl("GLOBAL", "ALL", paused, actor.getId()));
        if (!paused) result.put("resumedJobs", resumeScopeJobs(null, null));
        return result;
    }

    @Transactional
    public Map<String, Object> setOwnerPaused(UserAccount actor, Long ownerUserId, boolean paused) {
        requireViewer(actor);
        if (actor.getRole() != UserRole.SUPER_ADMIN && !Objects.equals(actor.getId(), ownerUserId)) {
            throw new SecurityException("无权控制其他账号任务");
        }
        Map<String, Object> result = new LinkedHashMap<>(setControl("OWNER", String.valueOf(ownerUserId), paused, actor.getId()));
        if (!paused) result.put("resumedJobs", resumeScopeJobs(ownerUserId, null));
        return result;
    }

    @Transactional
    public Map<String, Object> setLanePaused(UserAccount actor, BackgroundJobResourceLane lane, boolean paused) {
        requireSuperAdmin(actor);
        Map<String, Object> result = new LinkedHashMap<>(setControl("LANE", lane.name(), paused, actor.getId()));
        if (!paused) result.put("resumedJobs", resumeScopeJobs(null, lane));
        return result;
    }

    public Map<String, Object> overview() {
        List<BackgroundJob> jobs = jobRepository.findAll();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("threadType", "UNIFIED_BACKGROUND_JOBS");
        result.put("label", "统一后台任务");
        result.put("globalPaused", isControlPaused("GLOBAL", "ALL"));
        result.put("runningTaskCount", jobs.stream().filter(job -> job.getStatus() == BackgroundJobStatus.RUNNING).count());
        result.put("queuedTaskCount", jobs.stream().filter(job -> DISPATCHABLE_JOB_STATUSES.contains(job.getStatus())).count());
        result.put("pausedTaskCount", jobs.stream().filter(job -> job.getStatus() == BackgroundJobStatus.PAUSED).count());
        result.put("running", jobs.stream().anyMatch(job -> job.getStatus() == BackgroundJobStatus.RUNNING));
        result.put("recentTasks", jobs.stream()
            .sorted(Comparator.comparing(BackgroundJob::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(10).map(job -> toJobMap(job, false)).collect(Collectors.toList()));
        Map<Long, List<BackgroundJob>> byOwner = jobs.stream()
            .filter(job -> job.getOwnerUserId() != null)
            .collect(Collectors.groupingBy(BackgroundJob::getOwnerUserId, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> accountSummaries = new ArrayList<>();
        userAccountRepository.findAll().forEach(account -> {
            List<BackgroundJob> accountJobs = byOwner.getOrDefault(account.getId(), List.of());
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("ownerUserId", account.getId());
            summary.put("username", account.getUsername());
            summary.put("nickname", account.getNickname());
            summary.put("jobCount", accountJobs.size());
            summary.put("runningTaskCount", accountJobs.stream().filter(job -> job.getStatus() == BackgroundJobStatus.RUNNING).count());
            summary.put("queuedTaskCount", accountJobs.stream().filter(job -> DISPATCHABLE_JOB_STATUSES.contains(job.getStatus())).count());
            summary.put("pausedTaskCount", accountJobs.stream().filter(job -> job.getStatus() == BackgroundJobStatus.PAUSED).count());
            summary.put("failedTaskCount", accountJobs.stream().filter(job -> job.getStatus() == BackgroundJobStatus.FAILED
                || job.getStatus() == BackgroundJobStatus.PARTIAL_SUCCESS || job.getStatus() == BackgroundJobStatus.BLOCKED).count());
            summary.put("pausedByControl", isControlPaused("OWNER", String.valueOf(account.getId())));
            accountSummaries.add(summary);
        });
        result.put("accountSummaries", accountSummaries);
        return result;
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 2000)
    public void dispatch() {
        if (isControlPaused("GLOBAL", "ALL")) return;
        for (BackgroundJobResourceLane lane : BackgroundJobResourceLane.values()) {
            AtomicBoolean busy = laneBusy.get(lane);
            ExecutorService executor = executors.get(lane);
            if (busy == null || executor == null || !busy.compareAndSet(false, true)) continue;
            BackgroundJob job = chooseNextJob(lane);
            if (job == null) {
                busy.set(false);
                continue;
            }
            executor.submit(() -> {
                try {
                    processChunk(job.getId());
                } catch (Exception e) {
                    log.error("统一后台任务执行失败: jobId={}", job.getId(), e);
                    markUnexpectedJobFailure(job.getId(), e);
                } finally {
                    busy.set(false);
                }
            });
        }
    }

    private synchronized BackgroundJob chooseNextJob(BackgroundJobResourceLane lane) {
        List<BackgroundJob> candidates = jobRepository
            .findByResourceLaneAndStatusInOrderByPriorityDescCreatedAtAsc(lane, DISPATCHABLE_JOB_STATUSES)
            .stream()
            .filter(job -> !Boolean.TRUE.equals(job.getPauseRequested()) && !Boolean.TRUE.equals(job.getCancelRequested()))
            .filter(job -> !isControlPaused("OWNER", String.valueOf(job.getOwnerUserId())))
            .filter(job -> !isControlPaused("LANE", lane.name()))
            .collect(Collectors.toList());
        if (candidates.isEmpty()) return null;
        LocalDateTime now = LocalDateTime.now();
        int highestPriority = candidates.stream().mapToInt(job -> effectivePriority(job, now)).max().orElse(0);
        List<BackgroundJob> highest = candidates.stream()
            .filter(job -> effectivePriority(job, now) == highestPriority)
            .collect(Collectors.toList());
        Long lastOwner = lastOwnerByLane.get(lane);
        List<Long> owners = highest.stream().map(BackgroundJob::getOwnerUserId).distinct().collect(Collectors.toList());
        int previousOwnerIndex = lastOwner == null ? -1 : owners.indexOf(lastOwner);
        Long selectedOwner = owners.get((previousOwnerIndex + 1 + owners.size()) % owners.size());
        BackgroundJob selected = highest.stream().filter(job -> Objects.equals(job.getOwnerUserId(), selectedOwner))
            .findFirst().orElse(highest.get(0));
        selected.setStatus(BackgroundJobStatus.RUNNING);
        if (selected.getStartedAt() == null) selected.setStartedAt(LocalDateTime.now());
        selected.setBlockingReason(null);
        jobRepository.save(selected);
        lastOwnerByLane.put(lane, selected.getOwnerUserId());
        return selected;
    }

    private int effectivePriority(BackgroundJob job, LocalDateTime now) {
        int base = Optional.ofNullable(job.getPriority()).orElse(0);
        if (job.getCreatedAt() == null) return base;
        long waitingMinutes = Math.max(0L, Duration.between(job.getCreatedAt(), now).toMinutes());
        return base + (int) Math.min(1000L, waitingMinutes / 5L);
    }

    private int resumeScopeJobs(Long ownerUserId, BackgroundJobResourceLane lane) {
        List<BackgroundJob> paused = jobRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(
            List.of(BackgroundJobStatus.PAUSED));
        int resumed = 0;
        for (BackgroundJob job : paused) {
            if (ownerUserId != null && !Objects.equals(ownerUserId, job.getOwnerUserId())) continue;
            if (lane != null && lane != job.getResourceLane()) continue;
            if (Boolean.TRUE.equals(job.getPauseRequested())) continue;
            if (isControlPaused("GLOBAL", "ALL")
                || isControlPaused("OWNER", String.valueOf(job.getOwnerUserId()))
                || isControlPaused("LANE", job.getResourceLane().name())) continue;
            job.setStatus(BackgroundJobStatus.QUEUED);
            job.setBlockingReason(null);
            jobRepository.save(job);
            resumed++;
        }
        return resumed;
    }

    private void processChunk(Long jobId) {
        BackgroundJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == BackgroundJobStatus.IGNORED || job.getStatus() == BackgroundJobStatus.RETRIED) return;
        int handled = 0;
        List<BackgroundJobItem> items = itemRepository.findTop50ByJobIdAndStatusInOrderByIdAsc(jobId, PENDING_ITEM_STATUSES);
        for (BackgroundJobItem item : items) {
            if (item.getNextAttemptAt() != null && item.getNextAttemptAt().isAfter(LocalDateTime.now())) {
                continue;
            }
            job = jobRepository.findById(jobId).orElse(null);
            if (job == null || job.getStatus() == BackgroundJobStatus.RETRIED || job.getStatus() == BackgroundJobStatus.IGNORED) return;
            if (Boolean.TRUE.equals(job.getCancelRequested())) {
                finishCanceled(job);
                return;
            }
            if (Boolean.TRUE.equals(job.getPauseRequested())
                || isControlPaused("GLOBAL", "ALL")
                || isControlPaused("OWNER", String.valueOf(job.getOwnerUserId()))
                || isControlPaused("LANE", job.getResourceLane().name())) {
                job.setStatus(BackgroundJobStatus.PAUSED);
                jobRepository.save(job);
                return;
            }
            if (item.getPhotoId() != null) {
                Photo currentPhoto = photoRepository.findById(item.getPhotoId()).orElse(null);
                if (currentPhoto == null) {
                    markItemSkipped(item, "TARGET_DELETED", "照片已删除");
                    continue;
                }
                if (!Objects.equals(currentPhoto.getUserId(), job.getOwnerUserId())) {
                    markItemSkipped(item, "TARGET_MOVED", "照片已转移账号");
                    continue;
                }
                if (currentPhoto.getProcessingStatus() != ProcessingStatus.COMPLETED) {
                    markItemSkipped(item, "SCAN_INCOMPLETE", "照片不再满足扫描完成条件");
                    continue;
                }
            }
            if ("ALBUM_ATMOSPHERE_REBUILD".equals(job.getJobType()) && item.getTargetKey() != null && item.getTargetKey().startsWith("ALBUM:")) {
                Album album = albumRepository.findById(Long.valueOf(item.getTargetKey().substring(6))).orElse(null);
                if (album == null) {
                    markItemSkipped(item, "TARGET_DELETED", "相册已删除");
                    continue;
                }
                if (!Objects.equals(album.getUserId(), job.getOwnerUserId())) {
                    markItemSkipped(item, "TARGET_MOVED", "相册已转移账号");
                    continue;
                }
            }
            executeItem(job, item);
            if (job.getStatus() == BackgroundJobStatus.BLOCKED) return;
            handled++;
            if (handled >= CLAIM_SIZE) break;
        }
        refreshJobState(jobId, false);
    }

    private void executeItem(BackgroundJob job, BackgroundJobItem item) {
        item.setStatus(BackgroundJobItemStatus.RUNNING);
        item.setAttemptCount(Optional.ofNullable(item.getAttemptCount()).orElse(0) + 1);
        item.setStartedAt(LocalDateTime.now());
        item.setFinishedAt(null);
        item.setNextAttemptAt(null);
        item.setErrorCode(null);
        item.setErrorMessage(null);
        itemRepository.save(item);
        try {
            UserAccount owner = userAccountRepository.findById(job.getOwnerUserId())
                .orElseThrow(() -> new IllegalStateException("任务所属账号不存在"));
            executeHandler(job, item, owner);
            item.setStatus(BackgroundJobItemStatus.SUCCEEDED);
            item.setRetryable(false);
        } catch (Exception e) {
            Failure failure = classifyFailure(e);
            boolean retryAutomatically = failure.autoRetry
                && Optional.ofNullable(item.getAttemptCount()).orElse(0) < 3;
            item.setStatus(failure.blocked ? BackgroundJobItemStatus.BLOCKED
                : (retryAutomatically ? BackgroundJobItemStatus.QUEUED : BackgroundJobItemStatus.FAILED));
            item.setErrorCode(failure.code);
            item.setErrorMessage(shortMessage(e));
            item.setRetryable(failure.retryable);
            if (retryAutomatically) {
                item.setNextAttemptAt(LocalDateTime.now().plusSeconds(5L * item.getAttemptCount()));
            }
            if (failure.groupWide) {
                String groupId = job.getFailureGroupId();
                if (groupId == null || !groupId.startsWith(failure.code + ":")) {
                    groupId = failure.code + ":" + UUID.randomUUID();
                }
                item.setFailureGroupId(groupId);
                job.setFailureGroupId(groupId);
                job.setBlockingReason(failure.code);
                job.setStatus(BackgroundJobStatus.BLOCKED);
                setControl("LANE", job.getResourceLane().name(), true, null);
            }
            job.setErrorSummary(shortMessage(e));
            jobRepository.save(job);
        } finally {
            item.setFinishedAt(LocalDateTime.now());
            itemRepository.save(item);
        }
    }

    private void executeHandler(BackgroundJob job, BackgroundJobItem item, UserAccount owner) {
        Long photoId = item.getPhotoId();
        Map<String, Object> parameters = readParameters(job.getParametersJson());
        switch (job.getJobType()) {
            case "MODEL_REBUILD_FACE_DETECTION":
            case "MODEL_REBUILD_FACE_RECOGNITION":
            case "MODEL_REBUILD_IMAGE_CLASSIFICATION":
            case "MODEL_REBUILD_SALIENCY_DETECTION":
            case "MODEL_REBUILD_SCENE_RECOGNITION":
            case "MODEL_REBUILD_EMOTION_ANALYSIS":
            case "MODEL_REBUILD_BACKGROUND_REMOVAL":
                modelManagementService.rebuildPhoto(String.valueOf(parameters.get("modelKey")), photoId,
                    booleanParameter(parameters, "includeMissingItems", false),
                    booleanParameter(parameters, "forceRebuild", false),
                    booleanParameter(parameters, "preserveBindings", true));
                return;
            case "VISUAL_ANALYSIS":
                if (!booleanParameter(parameters, "forceReanalyze", false)
                    && visualAnalysisService.isCompleted(photoId)) return;
                visualAnalysisService.analyze(photoId, owner);
                return;
            case "FACE_RESCAN":
                Map<String, Object> faceResult = photoScanService.rescanFacesForPhoto(photoId,
                    booleanParameter(parameters, "preserveBindings", true));
                if (faceResult == null || faceResult.containsKey("error")) {
                    throw new IllegalStateException(faceResult == null ? "人脸重建未返回结果" : String.valueOf(faceResult.get("error")));
                }
                return;
            case "FACE_EMBEDDING":
                requireSuccessfulResult(photoScanService.rebuildFaceEmbeddingsForPhoto(photoId));
                return;
            case "AI_SCORING":
                Photo photo = photoRepository.findById(photoId).orElseThrow(() -> new IllegalArgumentException("照片不存在"));
                photoAIScoringService.rescorePhoto(photo);
                return;
            case "BACKGROUND_REMOVAL":
                int outputMaxSize = parameters.containsKey("outputMaxSize")
                    ? ((Number) parameters.get("outputMaxSize")).intValue() : 0;
                requireSuccessfulResult(outputMaxSize == 0
                    ? photoScanService.removeBackgroundForPhoto(photoId, true)
                    : photoScanService.removeBackgroundForPhoto(photoId, true, outputMaxSize));
                return;
            case "COLOR_RECALCULATE":
                photoScanService.recalculateColorForPhoto(photoId);
                return;
            case "EXIF_REBUILD":
                photoScanService.rebuildExifForPhoto(photoId);
                return;
            case "PHOTO_TIME_REBUILD":
                requireSuccessfulResult(photoScanService.updatePhotoTimeForPhoto(photoId));
                return;
            case "HASH_REBUILD":
                photoScanService.rebuildHashForPhoto(photoId);
                return;
            case "BACKGROUND_CACHE_CLEAR":
                photoScanService.clearBackgroundCacheForPhoto(photoId);
                return;
            case "ALBUM_ATMOSPHERE_REBUILD":
                if (item.getTargetKey() == null || !item.getTargetKey().startsWith("ALBUM:")) {
                    throw new IllegalArgumentException("相册任务缺少有效目标");
                }
                Long albumId = Long.valueOf(item.getTargetKey().substring(6));
                photoScanService.reanalyzeAlbumAtmosphere(albumId);
                return;
            case "COLOR_CATEGORY":
                photoScanService.updateColorCategoryForPhoto(photoId);
                return;
            default:
                throw new IllegalArgumentException("没有注册任务处理器: " + job.getJobType());
        }
    }

    private void requireSuccessfulResult(Map<String, Object> result) {
        if (result == null || result.containsKey("error") || Boolean.FALSE.equals(result.get("success"))) {
            throw new IllegalStateException(result == null ? "任务未返回结果"
                : String.valueOf(result.getOrDefault("error", "任务处理失败")));
        }
    }

    private void refreshJobState(Long jobId, boolean waitingDependency) {
        BackgroundJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() == BackgroundJobStatus.RETRIED || job.getStatus() == BackgroundJobStatus.IGNORED) return;
        long total = itemRepository.countByJobId(jobId);
        if (total == 0 && job.getTotalItems() > 0) {
            job.setStatus(BackgroundJobStatus.FAILED);
            job.setErrorSummary("任务子项丢失，无法汇总进度");
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
            return;
        }
        int succeeded = Math.toIntExact(itemRepository.countByJobIdAndStatus(jobId, BackgroundJobItemStatus.SUCCEEDED));
        int skipped = Math.toIntExact(itemRepository.countByJobIdAndStatus(jobId, BackgroundJobItemStatus.SKIPPED));
        int failed = Math.toIntExact(itemRepository.countByJobIdAndStatus(jobId, BackgroundJobItemStatus.FAILED));
        long blocked = itemRepository.countByJobIdAndStatus(jobId, BackgroundJobItemStatus.BLOCKED);
        long terminal = (long) succeeded + skipped + failed
            + itemRepository.countByJobIdAndStatus(jobId, BackgroundJobItemStatus.CANCELED);
        job.setProcessedItems(Math.toIntExact(terminal));
        job.setSucceededItems(succeeded);
        job.setSkippedItems(skipped);
        job.setFailedItems(failed);
        if (job.getStatus() == BackgroundJobStatus.BLOCKED && blocked > 0) {
            jobRepository.save(job);
            return;
        }
        if (terminal >= total) {
            if (failed > 0 && succeeded + skipped > 0) job.setStatus(BackgroundJobStatus.PARTIAL_SUCCESS);
            else if (failed > 0) job.setStatus(BackgroundJobStatus.FAILED);
            else if (succeeded == 0 && skipped > 0) {
                job.setStatus(BackgroundJobStatus.SKIPPED);
                job.setErrorSummary(null);
                job.setBlockingReason(null);
            }
            else {
                job.setStatus(BackgroundJobStatus.SUCCEEDED);
                job.setErrorSummary(null);
                job.setBlockingReason(null);
            }
            job.setFinishedAt(LocalDateTime.now());
        } else if (waitingDependency && succeeded + skipped + failed == 0) {
            job.setStatus(BackgroundJobStatus.WAITING_DEPENDENCY);
            job.setBlockingReason("SCAN_NOT_COMPLETED");
        } else {
            job.setStatus(BackgroundJobStatus.QUEUED);
        }
        jobRepository.save(job);
    }

    @Transactional
    public void recoverInterruptedWork() {
        List<BackgroundJob> runningJobs = jobRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(List.of(BackgroundJobStatus.RUNNING));
        for (BackgroundJob job : runningJobs) {
            job.setStatus(Boolean.TRUE.equals(job.getPauseRequested()) ? BackgroundJobStatus.PAUSED : BackgroundJobStatus.QUEUED);
            job.setErrorSummary("服务重启，未完成批次已重新排队");
            jobRepository.save(job);
        }
        itemRepository.findAll().stream()
            .filter(item -> item.getStatus() == BackgroundJobItemStatus.RUNNING)
            .forEach(item -> {
                item.setStatus(BackgroundJobItemStatus.QUEUED);
                item.setErrorCode("INTERRUPTED");
                item.setErrorMessage("服务重启，当前处理项将重新执行");
                itemRepository.save(item);
            });
    }

    private void finishCanceled(BackgroundJob job) {
        itemRepository.findByJobIdOrderByIdAsc(job.getId()).stream()
            .filter(item -> !item.getStatus().isTerminal())
            .forEach(item -> {
                item.setStatus(BackgroundJobItemStatus.CANCELED);
                item.setFinishedAt(LocalDateTime.now());
                itemRepository.save(item);
            });
        job.setStatus(BackgroundJobStatus.CANCELED);
        job.setFinishedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    private void markUnexpectedJobFailure(Long jobId, Exception exception) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(BackgroundJobStatus.FAILED);
            job.setErrorSummary(shortMessage(exception));
            job.setFinishedAt(LocalDateTime.now());
            jobRepository.save(job);
        });
    }

    private void markItemSkipped(BackgroundJobItem item, String code, String message) {
        item.setStatus(BackgroundJobItemStatus.SKIPPED);
        item.setErrorCode(code);
        item.setErrorMessage(message);
        item.setRetryable(false);
        item.setFinishedAt(LocalDateTime.now());
        itemRepository.save(item);
    }

    private Failure classifyFailure(Exception exception) {
        String message = shortMessage(exception).toLowerCase(Locale.ROOT);
        if (message.contains("quota") || message.contains("额度") || message.contains("token") && message.contains("limit")) {
            return new Failure("QUOTA_EXHAUSTED", true, true, true, false);
        }
        if (message.contains("model") && (message.contains("不存在") || message.contains("unavailable") || message.contains("未加载"))) {
            return new Failure("MODEL_UNAVAILABLE", true, true, true, false);
        }
        if (message.contains("timeout") || message.contains("超时") || message.contains("connection") || message.contains("网络")) {
            return new Failure("TRANSIENT_NETWORK", true, false, false, true);
        }
        if (message.contains("不存在") || message.contains("not found")) {
            return new Failure("SOURCE_MISSING", true, false, false, false);
        }
        return new Failure("PROCESSING_FAILED", true, false, false, false);
    }

    private Map<String, Object> setControl(String scopeType, String scopeKey, boolean paused, Long actorId) {
        BackgroundJobControl control = controlRepository.findByScopeTypeAndScopeKey(scopeType, scopeKey).orElseGet(BackgroundJobControl::new);
        control.setScopeType(scopeType);
        control.setScopeKey(scopeKey);
        control.setPaused(paused);
        control.setUpdatedByUserId(actorId);
        controlRepository.save(control);
        return Map.of("scopeType", scopeType, "scopeKey", scopeKey, "paused", paused);
    }

    private boolean isControlPaused(String scopeType, String scopeKey) {
        return controlRepository.findByScopeTypeAndScopeKey(scopeType, scopeKey)
            .map(control -> Boolean.TRUE.equals(control.getPaused())).orElse(false);
    }

    private BackgroundJob requireVisibleJob(UserAccount viewer, Long jobId) {
        requireViewer(viewer);
        BackgroundJob job = jobRepository.findById(jobId).orElseThrow(() -> new IllegalArgumentException("后台任务不存在"));
        if (viewer.getRole() != UserRole.SUPER_ADMIN && !Objects.equals(viewer.getId(), job.getOwnerUserId())) {
            throw new SecurityException("无权访问该任务");
        }
        return job;
    }

    private void requireViewer(UserAccount viewer) {
        if (viewer == null) throw new SecurityException("未授权");
    }

    private void requireSuperAdmin(UserAccount actor) {
        requireViewer(actor);
        if (actor.getRole() != UserRole.SUPER_ADMIN) throw new SecurityException("仅超级管理员可执行此操作");
    }

    private Map<String, Object> toJobMap(BackgroundJob job, boolean includeItems) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", job.getId());
        result.put("jobType", job.getJobType());
        result.put("resourceLane", job.getResourceLane());
        result.put("status", job.getStatus());
        result.put("ownerUserId", job.getOwnerUserId());
        result.put("requestedByUserId", job.getRequestedByUserId());
        result.put("priority", job.getPriority());
        result.put("pipelineVersion", job.getPipelineVersion());
        result.put("parameters", readParameters(job.getParametersJson()));
        result.put("parametersHash", job.getParametersHash());
        result.put("forceReprocess", job.getForceReprocess());
        result.put("sourceJobId", job.getSourceJobId());
        result.put("totalItems", job.getTotalItems());
        result.put("processedItems", job.getProcessedItems());
        result.put("succeededItems", job.getSucceededItems());
        result.put("skippedItems", job.getSkippedItems());
        result.put("failedItems", job.getFailedItems());
        result.put("pauseRequested", job.getPauseRequested());
        result.put("cancelRequested", job.getCancelRequested());
        result.put("blockingReason", job.getBlockingReason());
        result.put("failureGroupId", job.getFailureGroupId());
        result.put("errorSummary", job.getErrorSummary());
        result.put("createdAt", job.getCreatedAt());
        result.put("startedAt", job.getStartedAt());
        result.put("finishedAt", job.getFinishedAt());
        result.put("updatedAt", job.getUpdatedAt());
        if (includeItems) {
            result.put("items", itemRepository.findByJobIdOrderByIdAsc(job.getId()).stream()
                .map(this::toItemMap).collect(Collectors.toList()));
        }
        return result;
    }

    private Map<String, Object> toItemMap(BackgroundJobItem item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId());
        result.put("photoId", item.getPhotoId());
        result.put("targetKey", item.getTargetKey());
        result.put("stage", item.getStage());
        result.put("status", item.getStatus());
        result.put("attemptCount", item.getAttemptCount());
        result.put("errorCode", item.getErrorCode());
        result.put("errorMessage", item.getErrorMessage());
        result.put("retryable", item.getRetryable());
        result.put("failureGroupId", item.getFailureGroupId());
        result.put("startedAt", item.getStartedAt());
        result.put("finishedAt", item.getFinishedAt());
        result.put("nextAttemptAt", item.getNextAttemptAt());
        if (item.getPhotoId() != null) {
            photoRepository.findById(item.getPhotoId()).ifPresent(photo -> result.put("photoName", photo.getFilename()));
        }
        return result;
    }

    private String shortMessage(Exception exception) {
        String value = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private String writeParameters(Map<String, Object> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception e) {
            throw new IllegalArgumentException("任务参数无法序列化", e);
        }
    }

    private Map<String, Object> readParameters(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("后台任务参数损坏: {}", e.getMessage());
            return Map.of();
        }
    }

    private boolean booleanParameter(Map<String, Object> parameters, String name, boolean defaultValue) {
        Object value = parameters.get(name);
        return value instanceof Boolean ? (Boolean) value : defaultValue;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算任务参数摘要", e);
        }
    }

    private static final class Failure {
        final String code;
        final boolean retryable;
        final boolean blocked;
        final boolean groupWide;
        final boolean autoRetry;

        private Failure(String code, boolean retryable, boolean blocked, boolean groupWide, boolean autoRetry) {
            this.code = code;
            this.retryable = retryable;
            this.blocked = blocked;
            this.groupWide = groupWide;
            this.autoRetry = autoRetry;
        }
    }
}
