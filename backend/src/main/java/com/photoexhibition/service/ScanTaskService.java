package com.photoexhibition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.ScanTask;
import com.photoexhibition.entity.ScanTaskStatus;
import com.photoexhibition.entity.ScanTaskType;
import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.repository.ScanTaskRepository;
import com.photoexhibition.repository.StorageProviderRepository;
import com.photoexhibition.repository.UserAccountRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScanTaskService {

    @Data
    private static final class ScanCheckpoint {
        private int processedItems;
        private int totalItems;
        private int skippedItems;
        private int failedItems;
        private String rootPath;
        private String lastProcessedPath;
        private String lastProcessedType;
        private String resumeFromPath;
        private String resumeFromType;
        private String updatedAt;
    }

    private static final List<ScanTaskStatus> QUEUEABLE_STATUSES = List.of(
        ScanTaskStatus.PENDING,
        ScanTaskStatus.QUEUED
    );

    private static final List<ScanTaskStatus> MERGEABLE_STATUSES = List.of(
        ScanTaskStatus.PENDING,
        ScanTaskStatus.QUEUED,
        ScanTaskStatus.RUNNING
    );

    private final ScanTaskRepository scanTaskRepository;
    private final PhotoScanService photoScanService;
    private final UserPathService userPathService;
    private final SystemConfigService systemConfigService;
    private final StorageProviderService storageProviderService;
    private final StorageProviderRepository storageProviderRepository;
    private final UserAccountRepository userAccountRepository;
    private final ObjectMapper objectMapper;

    private final AtomicInteger activeWorkerCount = new AtomicInteger(0);
    private final java.util.Set<Long> activeTaskIds = ConcurrentHashMap.newKeySet();
    private final AtomicReference<String> lastDispatchedQueueOwner = new AtomicReference<>(null);
    private final ConcurrentHashMap<Long, PhotoScanService.ScanControlAction> taskControlActions = new ConcurrentHashMap<>();

    private final ExecutorService queueExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger threadCounter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "photo-scan-queue-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    });

    @PostConstruct
    public void init() {
        recoverInterruptedTasks();
        scheduleWorkerIfNeeded();
    }

    @PreDestroy
    public void shutdown() {
        for (Long taskId : new ArrayList<>(activeTaskIds)) {
            scanTaskRepository.findById(taskId).ifPresent(task -> {
                if (task.getStatus() == ScanTaskStatus.RUNNING) {
                    task.setStatus(ScanTaskStatus.PAUSED);
                    task.setErrorMessage("服务关闭，任务已暂停");
                    task.setFinishedAt(LocalDateTime.now());
                    scanTaskRepository.save(task);
                }
            });
        }
        queueExecutor.shutdownNow();
    }

    @Transactional
    public Map<String, Object> enqueueScan(UserAccount currentUser, String requestedPath, boolean force) {
        return enqueueTask(currentUser, requestedPath, force ? ScanTaskType.FULL_SCAN : ScanTaskType.INCREMENTAL_SCAN, force ? 200 : 100, false, null);
    }

    @Transactional
    public Map<String, Object> enqueueUploadScan(UserAccount currentUser, String requestedPath, Long storageProviderId) {
        return enqueueTask(currentUser, requestedPath, ScanTaskType.UPLOAD_SCAN, 300, false, storageProviderId);
    }

    @Transactional
    protected Map<String, Object> enqueueTask(UserAccount currentUser, String requestedPath, ScanTaskType taskType, int priority, boolean scheduled, Long storageProviderId) {
        ensureScanSupported(currentUser, storageProviderId, taskType);
        Path rootPath = resolveRequestedRoot(currentUser, requestedPath, storageProviderId);
        Long ownerUserId = resolveTaskOwner(currentUser, rootPath);
        String normalizedRootPath = rootPath.toString();

        Optional<ScanTask> mergedTask = tryMergeExistingTask(currentUser, ownerUserId, normalizedRootPath, taskType, priority, scheduled, storageProviderId);
        if (mergedTask.isPresent()) {
            Map<String, Object> response = toTaskMap(mergedTask.get());
            response.put("merged", true);
            response.put("message", buildMergedMessage(taskType, mergedTask.get()));
            return response;
        }

        ScanTask task = new ScanTask();
        task.setUserId(ownerUserId);
        task.setRequestedByUserId(currentUser != null ? currentUser.getId() : null);
        task.setTaskType(taskType);
        task.setStatus(ScanTaskStatus.QUEUED);
        task.setRootPath(normalizedRootPath);
        task.setStorageProviderId(storageProviderId);
        task.setPriority(priority);
        task.setScheduledTask(scheduled);
        task.setCheckpointJson(buildCheckpointJson(task));
        scanTaskRepository.save(task);

        scheduleWorkerWhenTransactionCommitted();

        Map<String, Object> response = toTaskMap(task);
        response.put("message", taskType == ScanTaskType.UPLOAD_SCAN ? "上传扫描任务已加入队列" : "扫描任务已加入队列");
        return response;
    }

    @Transactional
    public List<Map<String, Object>> listTasks(UserAccount currentUser) {
        recoverStaleRunningTasks();
        ensurePendingTasksScheduled();
        List<ScanTask> tasks;
        if (currentUser != null && currentUser.getRole() == UserRole.SUPER_ADMIN) {
            tasks = scanTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                .sorted(Comparator.comparing(ScanTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(50)
                .collect(Collectors.toList());
        } else if (currentUser != null) {
            tasks = scanTaskRepository.findTop50ByRequestedByUserIdOrderByCreatedAtDesc(currentUser.getId());
        } else {
            tasks = List.of();
        }

        return tasks.stream()
            .map(this::toTaskMap)
            .collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> getTask(UserAccount currentUser, Long taskId) {
        recoverStaleRunningTasks();
        ensurePendingTasksScheduled();
        ScanTask task = requireVisibleTask(currentUser, taskId);
        return toTaskMap(task);
    }

    @Transactional
    public Map<String, Object> enqueueScan(UserAccount currentUser, String requestedPath, boolean force, Long storageProviderId) {
        return enqueueTask(currentUser, requestedPath, force ? ScanTaskType.FULL_SCAN : ScanTaskType.INCREMENTAL_SCAN, force ? 200 : 100, false, storageProviderId);
    }

    @Transactional
    public Map<String, Object> retryTask(UserAccount currentUser, Long taskId) {
        ScanTask task = requireVisibleTask(currentUser, taskId);
        if (task.getStatus() == ScanTaskStatus.RUNNING) {
            throw new RuntimeException("运行中的任务不能重试");
        }
        if (task.getStatus() == ScanTaskStatus.QUEUED || task.getStatus() == ScanTaskStatus.PENDING) {
            return toTaskMap(task);
        }
        if (task.getStatus() == ScanTaskStatus.COMPLETED) {
            throw new RuntimeException("已完成任务请重新创建新的扫描任务");
        }

        task.setStatus(ScanTaskStatus.QUEUED);
        task.setFinishedAt(null);
        task.setStartedAt(null);
        task.setErrorMessage(null);
        if (hasProgress(task)) {
            task.setTaskType(ScanTaskType.RESUME_SCAN);
        } else if (task.getTaskType() == ScanTaskType.RESUME_SCAN) {
            task.setTaskType(ScanTaskType.INCREMENTAL_SCAN);
        }
        task.setCheckpointJson(buildCheckpointJson(task));
        scanTaskRepository.save(task);
        scheduleWorkerWhenTransactionCommitted();
        return toTaskMap(task);
    }

    @Transactional
    public Map<String, Object> pauseTask(UserAccount currentUser, Long taskId) {
        ScanTask task = requireVisibleTask(currentUser, taskId);
        if (task.getStatus() != ScanTaskStatus.RUNNING) {
            throw new RuntimeException("只有运行中的任务才能暂停");
        }
        taskControlActions.put(taskId, PhotoScanService.ScanControlAction.PAUSE);
        Map<String, Object> resp = toTaskMap(task);
        resp.put("message", "已发送暂停请求，当前文件处理完后生效");
        return resp;
    }

    @Transactional
    public Map<String, Object> cancelTask(UserAccount currentUser, Long taskId) {
        ScanTask task = requireVisibleTask(currentUser, taskId);
        if (task.getStatus() == ScanTaskStatus.RUNNING) {
            taskControlActions.put(taskId, PhotoScanService.ScanControlAction.CANCEL);
            Map<String, Object> resp = toTaskMap(task);
            resp.put("message", "已发送取消请求，当前文件处理完后生效");
            return resp;
        }
        if (task.getStatus() == ScanTaskStatus.COMPLETED || task.getStatus() == ScanTaskStatus.CANCELED) {
            return toTaskMap(task);
        }

        task.setStatus(ScanTaskStatus.CANCELED);
        task.setFinishedAt(LocalDateTime.now());
        task.setErrorMessage(task.getErrorMessage() == null ? "任务已取消" : task.getErrorMessage());
        task.setCheckpointJson(buildCheckpointJson(task));
        scanTaskRepository.save(task);
        return toTaskMap(task);
    }

    @Transactional
    public Map<String, Object> getStatusSummary(UserAccount currentUser) {
        recoverStaleRunningTasks();
        ensurePendingTasksScheduled();
        List<ScanTask> visibleTasks = resolveVisibleTasks(currentUser);
        List<ScanTask> globalQueueableTasks = scanTaskRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(QUEUEABLE_STATUSES);
        List<ScanTask> globalRunningTasks = scanTaskRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(task -> task.getStatus() == ScanTaskStatus.RUNNING)
            .sorted(Comparator.comparing(ScanTask::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
        List<ScanTask> queuedTasks = visibleTasks.stream()
            .filter(task -> task.getStatus() == ScanTaskStatus.PENDING
                || task.getStatus() == ScanTaskStatus.QUEUED)
            .collect(Collectors.toList());
        long queuedCount = queuedTasks.size();
        long pausedCount = visibleTasks.stream()
            .filter(task -> task.getStatus() == ScanTaskStatus.PAUSED)
            .count();
        int queuedImageCount = queuedTasks.stream()
            .mapToInt(this::estimateRemainingItems)
            .sum();

        List<ScanTask> runningTasks = visibleTasks.stream()
            .filter(task -> task.getStatus() == ScanTaskStatus.RUNNING)
            .sorted(Comparator.comparing(ScanTask::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .collect(Collectors.toList());
        int runningImageCount = runningTasks.stream()
            .mapToInt(this::estimateRemainingItems)
            .sum();

        Optional<ScanTask> currentTask = runningTasks.stream()
            .filter(task -> task.getStatus() == ScanTaskStatus.RUNNING)
            .findFirst();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("queuedTaskCount", queuedCount);
        summary.put("queuedOwnerCount", queuedTasks.stream()
            .map(this::resolveQueueOwnerKey)
            .filter(ownerKey -> ownerKey != null && !ownerKey.isBlank())
            .distinct()
            .count());
        summary.put("queuedOwnerSummaries", buildQueuedOwnerSummaries(queuedTasks));
        summary.put("pausedTaskCount", pausedCount);
        summary.put("runningTaskCount", runningTasks.size());
        summary.put("queuedImageCount", queuedImageCount);
        summary.put("runningImageCount", runningImageCount);
        summary.put("currentTask", currentTask.map(this::toTaskMap).orElse(null));
        summary.put("runningTasks", runningTasks.stream()
            .limit(5)
            .map(this::toTaskMap)
            .collect(Collectors.toList()));
        summary.put("recentTasks", visibleTasks.stream()
            .sorted(Comparator.comparing(ScanTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .limit(10)
            .map(this::toTaskMap)
            .collect(Collectors.toList()));
        summary.put("queueActive", currentTask.isPresent() || queuedCount > 0);
        summary.put("currentUserQueue", buildCurrentUserQueueSummary(currentUser, globalRunningTasks, globalQueueableTasks));
        return summary;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSuperAdminQueueOverview() {
        recoverStaleRunningTasks();
        List<ScanTask> allTasks = scanTaskRepository.findAllByOrderByCreatedAtDesc();
        List<ScanTask> queueableTasks = scanTaskRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(QUEUEABLE_STATUSES);
        List<ScanTask> runningTasks = allTasks.stream()
            .filter(task -> task.getStatus() == ScanTaskStatus.RUNNING)
            .sorted(Comparator.comparing(ScanTask::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("threadType", "SCAN_QUEUE");
        summary.put("label", "扫描队列线程");
        summary.put("configuredWorkers", systemConfigService.getScanWorkerCount());
        summary.put("activeWorkers", activeWorkerCount.get());
        summary.put("activeTaskIds", new ArrayList<>(activeTaskIds));
        summary.put("runningTaskCount", runningTasks.size());
        summary.put("queuedTaskCount", queueableTasks.size());
        summary.put("runningImageCount", runningTasks.stream().mapToInt(this::estimateRemainingItems).sum());
        summary.put("queuedImageCount", queueableTasks.stream().mapToInt(this::estimateRemainingItems).sum());
        summary.put("pausedTaskCount", allTasks.stream().filter(task -> task.getStatus() == ScanTaskStatus.PAUSED).count());
        summary.put("queueActive", !runningTasks.isEmpty() || !queueableTasks.isEmpty());
        summary.put("runningTasks", runningTasks.stream().limit(8).map(this::toTaskMap).collect(Collectors.toList()));
        summary.put("queuedOwnerSummaries", buildQueuedOwnerSummaries(queueableTasks));
        summary.put("recentTasks", allTasks.stream().limit(10).map(this::toTaskMap).collect(Collectors.toList()));
        return summary;
    }

    private Map<String, Object> buildCurrentUserQueueSummary(UserAccount currentUser,
                                                             List<ScanTask> globalRunningTasks,
                                                             List<ScanTask> globalQueueableTasks) {
        if (currentUser == null || currentUser.getRole() == UserRole.SUPER_ADMIN) {
            return null;
        }
        Long currentUserId = currentUser.getId();
        boolean hasRunningTask = globalRunningTasks.stream()
            .anyMatch(task -> Objects.equals(resolveQueueOwnerUserId(task), currentUserId));
        List<ScanTask> ownQueueTasks = globalQueueableTasks.stream()
            .filter(task -> Objects.equals(resolveQueueOwnerUserId(task), currentUserId))
            .collect(Collectors.toList());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("hasRunningTask", hasRunningTask);
        summary.put("queuedTaskCount", ownQueueTasks.size());
        summary.put("pendingImageCount", ownQueueTasks.stream()
            .mapToInt(this::estimateRemainingItems)
            .sum());
        summary.put("aheadTaskCount", 0);
        summary.put("aheadImageCount", 0);
        summary.put("message", hasRunningTask ? "你的照片正在扫描中" : "当前没有排队中的扫描任务");

        if (hasRunningTask) {
            return summary;
        }
        if (ownQueueTasks.isEmpty()) {
            return summary;
        }

        ScanTask firstOwnTask = ownQueueTasks.get(0);
        int firstOwnIndex = globalQueueableTasks.indexOf(firstOwnTask);
        if (firstOwnIndex < 0) {
            firstOwnIndex = 0;
        }
        List<ScanTask> aheadQueueTasks = globalQueueableTasks.subList(0, firstOwnIndex);
        int aheadRunningImages = globalRunningTasks.stream()
            .filter(task -> !Objects.equals(resolveQueueOwnerUserId(task), currentUserId))
            .mapToInt(this::estimateRemainingItems)
            .sum();
        int aheadQueuedImages = aheadQueueTasks.stream()
            .filter(task -> !Objects.equals(resolveQueueOwnerUserId(task), currentUserId))
            .mapToInt(this::estimateRemainingItems)
            .sum();

        summary.put("aheadTaskCount", aheadQueueTasks.size() + (globalRunningTasks.isEmpty() ? 0 : globalRunningTasks.size()));
        summary.put("aheadImageCount", aheadRunningImages + aheadQueuedImages);
        summary.put("message", aheadRunningImages + aheadQueuedImages > 0
            ? "当前仍有更早进入队列的图片等待扫描"
            : "即将轮到你的照片开始扫描");
        return summary;
    }

    private int estimateRemainingItems(ScanTask task) {
        if (task == null) {
            return 0;
        }
        int totalItems = Math.max(defaultInt(task.getTotalItems()), readCheckpoint(task).getTotalItems());
        int processedItems = Math.max(defaultInt(task.getProcessedItems()), readCheckpoint(task).getProcessedItems());
        int remaining = Math.max(totalItems - processedItems, 0);
        if (remaining > 0) {
            return remaining;
        }
        if (task.getStatus() == ScanTaskStatus.COMPLETED || task.getStatus() == ScanTaskStatus.CANCELED) {
            return 0;
        }
        int estimated = photoScanService.estimateScannableFileCount(task.getRootPath());
        return Math.max(estimated - processedItems, estimated > 0 ? 1 : 0);
    }

    private List<Map<String, Object>> buildQueuedOwnerSummaries(List<ScanTask> queuedTasks) {
        if (queuedTasks == null || queuedTasks.isEmpty()) {
            return List.of();
        }

        Map<Long, UserAccount> userMap = userAccountRepository.findAllById(queuedTasks.stream()
                .map(this::resolveQueueOwnerUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()))
            .stream()
            .collect(Collectors.toMap(UserAccount::getId, user -> user));

        Map<String, List<ScanTask>> groupedTasks = queuedTasks.stream()
            .collect(Collectors.groupingBy(
                task -> firstNonBlank(resolveQueueOwnerKey(task), "SYSTEM"),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        return groupedTasks.entrySet().stream()
            .map(entry -> toQueuedOwnerSummary(entry.getKey(), entry.getValue(), userMap))
            .sorted(Comparator
                .comparing((Map<String, Object> item) -> ((Number) item.get("taskCount")).intValue()).reversed()
                .thenComparing(item -> String.valueOf(item.get("ownerLabel")), Comparator.nullsLast(String::compareTo)))
            .limit(5)
            .collect(Collectors.toList());
    }

    private Map<String, Object> toQueuedOwnerSummary(String ownerKey, List<ScanTask> tasks, Map<Long, UserAccount> userMap) {
        Long ownerUserId = tasks.stream()
            .map(this::resolveQueueOwnerUserId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
        UserAccount owner = ownerUserId != null ? userMap.get(ownerUserId) : null;
        int highestPriority = tasks.stream()
            .map(ScanTask::getPriority)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(0);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ownerKey", ownerKey);
        summary.put("ownerUserId", ownerUserId);
        summary.put("ownerLabel", buildQueueOwnerLabel(ownerKey, owner));
        summary.put("taskCount", tasks.size());
        summary.put("highestPriority", highestPriority);
        return summary;
    }

    @Scheduled(fixedDelayString = "${photo.scan.scan-interval}000", initialDelayString = "${photo.scan.scan-interval}000")
    @Async
    public void enqueueScheduledScan() {
        if (!systemConfigService.isScanSchedulerEnabled()) {
            return;
        }

        boolean existsPendingScheduledTask = scanTaskRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(QUEUEABLE_STATUSES).stream()
            .anyMatch(task -> Boolean.TRUE.equals(task.getScheduledTask()));
        if (existsPendingScheduledTask) {
            return;
        }

        boolean existsRunningScheduledTask = !scanTaskRepository.findByStatusAndScheduledTaskTrue(ScanTaskStatus.RUNNING).isEmpty();
        if (existsRunningScheduledTask) {
            return;
        }

        Path rootPath = userPathService.resolvePhotoBasePath();
        enqueueTask(null, rootPath.toString(), ScanTaskType.INCREMENTAL_SCAN, 10, true, null);
    }

    private synchronized void scheduleWorkerIfNeeded() {
        recoverStaleRunningTasks();
        int desiredWorkers = systemConfigService.getScanWorkerCount();
        while (hasPendingTasks() && activeWorkerCount.get() < desiredWorkers) {
            activeWorkerCount.incrementAndGet();
            queueExecutor.submit(this::processQueueLoop);
        }
    }

    private void scheduleWorkerWhenTransactionCommitted() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    scheduleWorkerIfNeeded();
                }
            });
        } else {
            scheduleWorkerIfNeeded();
        }
    }

    private void ensurePendingTasksScheduled() {
        if (activeWorkerCount.get() > 0) {
            return;
        }
        if (!hasPendingTasks()) {
            return;
        }
        scheduleWorkerIfNeeded();
    }

    private void processQueueLoop() {
        try {
            while (true) {
                ScanTask task = claimNextTask().orElse(null);
                if (task == null) {
                    return;
                }
                runTask(task);
            }
        } finally {
            activeWorkerCount.updateAndGet(value -> Math.max(0, value - 1));
            if (hasPendingTasks()) {
                scheduleWorkerIfNeeded();
            }
        }
    }

    @Transactional
    protected synchronized Optional<ScanTask> claimNextTask() {
        List<ScanTask> tasks = scanTaskRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(QUEUEABLE_STATUSES);
        if (tasks.isEmpty()) {
            return Optional.empty();
        }

        ScanTask task = chooseNextTaskForClaim(tasks, lastDispatchedQueueOwner.get());
        ScanTaskStatus previousStatus = task.getStatus();
        task.setStatus(ScanTaskStatus.RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setFinishedAt(null);
        task.setErrorMessage(null);
        if (previousStatus == ScanTaskStatus.PAUSED) {
            task.setTaskType(ScanTaskType.RESUME_SCAN);
        }
        scanTaskRepository.save(task);
        activeTaskIds.add(task.getId());
        lastDispatchedQueueOwner.set(resolveQueueOwnerKey(task));
        return Optional.of(task);
    }

    private void runTask(ScanTask task) {
        TaskProgressTracker tracker = new TaskProgressTracker(task.getId());
        try {
            photoScanService.runWithStorageContext(task.getStorageProviderId(), task.getUserId(), () -> {
                if (task.getTaskType() == ScanTaskType.FULL_SCAN) {
                    photoScanService.rescanDirectory(task.getRootPath(), tracker);
                } else {
                    photoScanService.scanDirectory(task.getRootPath(), tracker);
                }
            });
        } catch (Exception e) {
            log.warn("扫描任务执行失败: taskId={}, rootPath={}", task.getId(), task.getRootPath(), e);
        } finally {
            activeTaskIds.remove(task.getId());
        }
    }

    @Transactional
    protected void recoverInterruptedTasks() {
        List<ScanTask> tasks = new ArrayList<>(scanTaskRepository.findAllByOrderByCreatedAtDesc());
        boolean changed = false;
        for (ScanTask task : tasks) {
            if (task.getStatus() == ScanTaskStatus.RUNNING) {
                task.setStatus(hasProgress(task) ? ScanTaskStatus.QUEUED : ScanTaskStatus.PENDING);
                task.setTaskType(hasProgress(task) ? ScanTaskType.RESUME_SCAN : task.getTaskType());
                task.setErrorMessage("服务重启，任务待恢复");
                task.setFinishedAt(LocalDateTime.now());
                task.setCheckpointJson(buildCheckpointJson(task));
                changed = true;
            }
        }
        if (changed) {
            scanTaskRepository.saveAll(tasks);
        }
    }

    private boolean hasPendingTasks() {
        return !scanTaskRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(QUEUEABLE_STATUSES).isEmpty();
    }

    @Transactional
    protected void recoverStaleRunningTasks() {
        List<ScanTask> staleTasks = scanTaskRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(task -> task.getStatus() == ScanTaskStatus.RUNNING)
            .filter(task -> !activeTaskIds.contains(task.getId()))
            .collect(Collectors.toList());
        if (staleTasks.isEmpty()) {
            return;
        }
        for (ScanTask task : staleTasks) {
            task.setStatus(hasProgress(task) ? ScanTaskStatus.QUEUED : ScanTaskStatus.PENDING);
            task.setTaskType(hasProgress(task) ? ScanTaskType.RESUME_SCAN : task.getTaskType());
            task.setErrorMessage("检测到任务未被工作线程持有，已自动恢复排队");
            task.setFinishedAt(LocalDateTime.now());
            task.setCheckpointJson(buildCheckpointJson(task));
        }
        scanTaskRepository.saveAll(staleTasks);
    }

    ScanTask chooseNextTaskForClaim(List<ScanTask> tasks, String lastOwnerKey) {
        if (tasks == null || tasks.isEmpty()) {
            return null;
        }

        int highestPriority = tasks.stream()
            .map(ScanTask::getPriority)
            .filter(Objects::nonNull)
            .max(Integer::compareTo)
            .orElse(defaultInt(tasks.get(0).getPriority()));

        List<ScanTask> highestPriorityTasks = tasks.stream()
            .filter(task -> defaultInt(task.getPriority()) == highestPriority)
            .sorted(Comparator.comparing(ScanTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());

        if (highestPriorityTasks.size() <= 1 || lastOwnerKey == null || lastOwnerKey.isBlank()) {
            return highestPriorityTasks.get(0);
        }

        List<ScanTask> alternativeOwners = highestPriorityTasks.stream()
            .filter(task -> !Objects.equals(resolveQueueOwnerKey(task), lastOwnerKey))
            .collect(Collectors.toList());
        if (!alternativeOwners.isEmpty()) {
            return alternativeOwners.get(0);
        }
        return highestPriorityTasks.get(0);
    }

    private Optional<ScanTask> tryMergeExistingTask(UserAccount currentUser,
                                                    Long ownerUserId,
                                                    String normalizedRootPath,
                                                    ScanTaskType incomingTaskType,
                                                    int priority,
                                                    boolean scheduled,
                                                    Long storageProviderId) {
        List<ScanTask> candidates = resolveMergeableCandidates(currentUser, ownerUserId).stream()
            .filter(task -> samePath(task.getRootPath(), normalizedRootPath))
            .sorted(Comparator.comparing(ScanTask::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .collect(Collectors.toList());

        for (ScanTask task : candidates) {
            if (task.getStatus() == ScanTaskStatus.RUNNING) {
                if (canRunningTaskCover(task.getTaskType(), incomingTaskType)) {
                    return Optional.of(task);
                }
                continue;
            }

            boolean changed = false;
            if (task.getRequestedByUserId() == null && currentUser != null) {
                task.setRequestedByUserId(currentUser.getId());
                changed = true;
            }
            if (task.getStorageProviderId() == null && storageProviderId != null) {
                task.setStorageProviderId(storageProviderId);
                changed = true;
            }
            if (priority > defaultInt(task.getPriority())) {
                task.setPriority(priority);
                changed = true;
            }
            if (scheduled && !Boolean.TRUE.equals(task.getScheduledTask())) {
                task.setScheduledTask(true);
                changed = true;
            }

            ScanTaskType upgradedType = chooseMergedTaskType(task.getTaskType(), incomingTaskType);
            if (upgradedType != task.getTaskType()) {
                task.setTaskType(upgradedType);
                changed = true;
            }

            if (changed) {
                task.setCheckpointJson(buildCheckpointJson(task));
                scanTaskRepository.save(task);
            }
            return Optional.of(task);
        }
        return Optional.empty();
    }

    private List<ScanTask> resolveMergeableCandidates(UserAccount currentUser, Long ownerUserId) {
        if (currentUser != null && currentUser.getRole() != UserRole.SUPER_ADMIN) {
            return scanTaskRepository.findByRequestedByUserIdAndStatusInOrderByCreatedAtDesc(currentUser.getId(), MERGEABLE_STATUSES).stream()
                .filter(task -> sameNullableLong(task.getUserId(), ownerUserId))
                .collect(Collectors.toList());
        }
        if (ownerUserId != null) {
            return scanTaskRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(ownerUserId, MERGEABLE_STATUSES);
        }
        return scanTaskRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(MERGEABLE_STATUSES);
    }

    private String buildMergedMessage(ScanTaskType incomingTaskType, ScanTask task) {
        if (task.getStatus() == ScanTaskStatus.RUNNING) {
            return "检测到相同目录已有扫描任务运行中，已复用当前任务";
        }
        return incomingTaskType == ScanTaskType.UPLOAD_SCAN
            ? "检测到相同目录已有待执行扫描任务，已自动合并"
            : "检测到相同目录已有扫描任务，已更新队列中的任务";
    }

    private String resolveQueueOwnerKey(ScanTask task) {
        if (task == null) {
            return "SYSTEM";
        }
        Long ownerUserId = resolveQueueOwnerUserId(task);
        if (ownerUserId != null) {
            if (task.getRequestedByUserId() != null) {
                return "REQUESTER:" + ownerUserId;
            }
            return "OWNER:" + ownerUserId;
        }
        return Boolean.TRUE.equals(task.getScheduledTask()) ? "SCHEDULED" : "SYSTEM";
    }

    private Long resolveQueueOwnerUserId(ScanTask task) {
        if (task == null) {
            return null;
        }
        if (task.getRequestedByUserId() != null) {
            return task.getRequestedByUserId();
        }
        return task.getUserId();
    }

    private String buildQueueOwnerLabel(String ownerKey, UserAccount owner) {
        if (owner != null) {
            String displayName = firstNonBlank(owner.getNickname(), owner.getProjectNameZh(), owner.getProjectNameEn(), owner.getUsername());
            return firstNonBlank(displayName, "用户#" + owner.getId());
        }
        if ("SCHEDULED".equals(ownerKey)) {
            return "定时任务";
        }
        if ("SYSTEM".equals(ownerKey)) {
            return "系统任务";
        }
        return ownerKey;
    }

    private ScanTaskType chooseMergedTaskType(ScanTaskType existingType, ScanTaskType incomingType) {
        if (incomingType == ScanTaskType.FULL_SCAN || existingType == ScanTaskType.FULL_SCAN) {
            return ScanTaskType.FULL_SCAN;
        }
        if (incomingType == ScanTaskType.INCREMENTAL_SCAN || incomingType == ScanTaskType.RESUME_SCAN) {
            return ScanTaskType.INCREMENTAL_SCAN;
        }
        if (existingType == ScanTaskType.RESUME_SCAN) {
            return ScanTaskType.RESUME_SCAN;
        }
        return existingType == null ? incomingType : existingType;
    }

    private boolean canRunningTaskCover(ScanTaskType existingType, ScanTaskType incomingType) {
        if (existingType == ScanTaskType.FULL_SCAN) {
            return true;
        }
        if (existingType == ScanTaskType.INCREMENTAL_SCAN || existingType == ScanTaskType.RESUME_SCAN) {
            return incomingType != ScanTaskType.FULL_SCAN;
        }
        return existingType == ScanTaskType.UPLOAD_SCAN && incomingType == ScanTaskType.UPLOAD_SCAN;
    }

    private boolean sameNullableLong(Long left, Long right) {
        return Objects.equals(left, right);
    }

    private boolean samePath(String left, String right) {
        if (left == null || right == null) {
            return Objects.equals(left, right);
        }
        return Paths.get(left).normalize().toString().equals(Paths.get(right).normalize().toString());
    }

    private void clearTaskControlAction(Long taskId) {
        if (taskId != null) {
            taskControlActions.remove(taskId);
        }
    }

    private boolean hasProgress(ScanTask task) {
        return (task.getProcessedItems() != null && task.getProcessedItems() > 0)
            || (task.getLastProcessedPath() != null && !task.getLastProcessedPath().isBlank());
    }

    private List<ScanTask> resolveVisibleTasks(UserAccount currentUser) {
        if (currentUser != null && currentUser.getRole() == UserRole.SUPER_ADMIN) {
            return scanTaskRepository.findAllByOrderByCreatedAtDesc();
        }
        if (currentUser == null) {
            return List.of();
        }
        return scanTaskRepository.findTop50ByRequestedByUserIdOrderByCreatedAtDesc(currentUser.getId());
    }

    private ScanTask requireVisibleTask(UserAccount currentUser, Long taskId) {
        ScanTask task = scanTaskRepository.findById(taskId)
            .orElseThrow(() -> new RuntimeException("扫描任务不存在"));
        if (currentUser == null) {
            throw new RuntimeException("未授权");
        }
        if (currentUser.getRole() == UserRole.SUPER_ADMIN) {
            return task;
        }
        if (!Objects.equals(task.getRequestedByUserId(), currentUser.getId())) {
            throw new RuntimeException("无权访问该扫描任务");
        }
        return task;
    }

    private Path resolveRequestedRoot(UserAccount currentUser, String requestedPath, Long storageProviderId) {
        StorageProvider provider = resolveStorageProvider(storageProviderId);
        if (provider != null && provider.getType() != null && provider.getType() != com.photoexhibition.entity.StorageType.LOCAL) {
            StorageProviderService.BrowserStorageContext browserContext =
                storageProviderService.resolveBrowserStorage(currentUser, storageProviderId);
            Path scopedRoot = browserContext.getScopedRoot();
            if (requestedPath == null || requestedPath.isBlank()) {
                return scopedRoot;
            }
            Path candidate = Paths.get(requestedPath.trim());
            if (!candidate.isAbsolute()) {
                String clean = requestedPath.startsWith("./") ? requestedPath.substring(2) : requestedPath;
                Path relative = Paths.get(clean).normalize();
                if (currentUser != null) {
                    relative = userPathService.stripLeadingUserSegment(relative, currentUser.getId());
                }
                candidate = scopedRoot.resolve(relative);
            }
            candidate = candidate.normalize();
            if (!candidate.startsWith(scopedRoot)) {
                throw new IllegalArgumentException("路径超出当前存储可扫描范围");
            }
            return candidate;
        }
        if (currentUser == null) {
            Path base = userPathService.resolvePhotoBasePath();
            if (requestedPath == null || requestedPath.isBlank()) {
                return base;
            }
            Path candidate = Paths.get(requestedPath.trim());
            if (!candidate.isAbsolute()) {
                String clean = requestedPath.startsWith("./") ? requestedPath.substring(2) : requestedPath;
                Path relative = Paths.get(clean).normalize();
                Long scopedUserId = userPathService.extractUserIdFromPath(base.resolve(relative).normalize().toString());
                if (scopedUserId != null) {
                    relative = userPathService.stripLeadingUserSegment(relative, scopedUserId);
                    candidate = base.resolve(String.valueOf(scopedUserId)).resolve(relative);
                } else {
                    candidate = base.resolve(relative);
                }
            }
            return candidate.toAbsolutePath().normalize();
        }
        return userPathService.resolveScopedPath(requestedPath, currentUser);
    }

    private Long resolveTaskOwner(UserAccount currentUser, Path rootPath) {
        if (currentUser != null && systemConfigService.isMultiUserEnabled()) {
            return currentUser.getId();
        }
        return userPathService.extractUserIdFromPath(rootPath.toString());
    }

    private void ensureScanSupported(UserAccount currentUser, Long storageProviderId, ScanTaskType taskType) {
        if (storageProviderId == null) {
            return;
        }
        StorageProvider provider = resolveStorageProvider(storageProviderId);
        if (provider == null) {
            throw new RuntimeException("存储提供者不存在");
        }
        Map<String, Object> capability = storageProviderService.describeProviderCapabilities(provider, currentUser);
        if (Boolean.TRUE.equals(capability.get("scanSupported"))) {
            return;
        }
        String supportMessage = capability.get("supportMessage") == null ? null : String.valueOf(capability.get("supportMessage"));
        String actionLabel = taskType == ScanTaskType.UPLOAD_SCAN ? "上传后自动扫描" : "扫描任务";
        if (supportMessage == null || supportMessage.isBlank()) {
            throw new RuntimeException(actionLabel + "不可用：当前存储暂不支持自动扫描");
        }
        throw new RuntimeException(actionLabel + "不可用：" + supportMessage);
    }

    private Map<String, Object> toTaskMap(ScanTask task) {
        ScanCheckpoint checkpoint = readCheckpoint(task);
        StorageProvider storageProvider = resolveStorageProvider(task.getStorageProviderId());
        UserAccount requestedByUser = resolveTaskDisplayUser(task);
        Map<String, Object> providerCapability = storageProvider == null
            ? buildDefaultProviderCapability()
            : storageProviderService.describeProviderCapabilities(storageProvider, requestedByUser);
        if (providerCapability == null) {
            providerCapability = buildDefaultProviderCapability();
        }
        String ownerKey = resolveQueueOwnerKey(task);
        int totalItems = Math.max(defaultInt(task.getTotalItems()), checkpoint.totalItems);
        int processedItems = Math.max(defaultInt(task.getProcessedItems()), checkpoint.processedItems);
        int skippedItems = Math.max(defaultInt(task.getSkippedItems()), checkpoint.skippedItems);
        int failedItems = Math.max(defaultInt(task.getFailedItems()), checkpoint.failedItems);
        String lastProcessedPath = firstNonBlank(task.getLastProcessedPath(), checkpoint.lastProcessedPath);
        String lastProcessedType = firstNonBlank(checkpoint.lastProcessedType, inferPathType(lastProcessedPath));
        String resumeFromPath = firstNonBlank(checkpoint.resumeFromPath, lastProcessedPath);
        String resumeFromType = firstNonBlank(checkpoint.resumeFromType, lastProcessedType);
        String rootPathDisplay = toDisplayPath(task.getRootPath());
        String lastProcessedPathDisplay = toDisplayPath(lastProcessedPath);
        String resumeFromPathDisplay = toDisplayPath(resumeFromPath);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", task.getId());
        resp.put("taskId", task.getId());
        resp.put("userId", task.getUserId());
        resp.put("requestedByUserId", task.getRequestedByUserId());
        resp.put("requestedByUsername", requestedByUser != null ? requestedByUser.getUsername() : null);
        resp.put("requestedByUserNickname", requestedByUser != null ? requestedByUser.getNickname() : null);
        resp.put("ownerLabel", buildQueueOwnerLabel(ownerKey, requestedByUser));
        resp.put("storageProviderId", task.getStorageProviderId());
        resp.put("storageProviderName", storageProvider != null ? storageProvider.getName() : null);
        resp.put("storageProviderType", storageProvider != null && storageProvider.getType() != null ? storageProvider.getType().name() : null);
        resp.put("scanSupported", Boolean.TRUE.equals(providerCapability.get("scanSupported")));
        resp.put("supportMessage", providerCapability.get("supportMessage"));
        resp.put("taskType", task.getTaskType() != null ? task.getTaskType().name() : null);
        resp.put("status", task.getStatus() != null ? task.getStatus().name() : null);
        resp.put("rootPath", rootPathDisplay);
        resp.put("rootPathDisplay", rootPathDisplay);
        resp.put("priority", task.getPriority());
        resp.put("totalItems", totalItems);
        resp.put("processedItems", processedItems);
        resp.put("skippedItems", skippedItems);
        resp.put("failedItems", failedItems);
        resp.put("progressPercent", totalItems <= 0 ? 0 : Math.min(100, (processedItems * 100) / totalItems));
        resp.put("lastProcessedPath", lastProcessedPathDisplay);
        resp.put("lastProcessedPathDisplay", lastProcessedPathDisplay);
        resp.put("lastProcessedType", lastProcessedType);
        resp.put("resumeFromPath", resumeFromPathDisplay);
        resp.put("resumeFromPathDisplay", resumeFromPathDisplay);
        resp.put("resumeFromType", resumeFromType);
        resp.put("checkpointUpdatedAt", checkpoint.updatedAt);
        resp.put("scheduledTask", task.getScheduledTask());
        resp.put("errorMessage", task.getErrorMessage());
        resp.put("startedAt", task.getStartedAt());
        resp.put("finishedAt", task.getFinishedAt());
        resp.put("createdAt", task.getCreatedAt());
        resp.put("updatedAt", task.getUpdatedAt());
        resp.put("checkpoint", toCheckpointMap(checkpoint));
        return resp;
    }

    private UserAccount resolveTaskDisplayUser(ScanTask task) {
        Long ownerUserId = resolveQueueOwnerUserId(task);
        if (ownerUserId == null) {
            return null;
        }
        return userAccountRepository.findById(ownerUserId).orElse(null);
    }

    private StorageProvider resolveStorageProvider(Long storageProviderId) {
        if (storageProviderId == null) {
            return null;
        }
        return storageProviderRepository.findById(storageProviderId).orElse(null);
    }

    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Map<String, Object> buildDefaultProviderCapability() {
        Map<String, Object> capability = new LinkedHashMap<>();
        capability.put("scanSupported", true);
        capability.put("supportMessage", null);
        return capability;
    }

    private String buildCheckpointJson(ScanTask task) {
        return buildCheckpointJson(task, inferPathType(task.getLastProcessedPath()), inferPathType(task.getLastProcessedPath()));
    }

    private String buildCheckpointJson(ScanTask task, String lastProcessedType, String resumeFromType) {
        ScanCheckpoint checkpoint = new ScanCheckpoint();
        checkpoint.processedItems = defaultInt(task.getProcessedItems());
        checkpoint.totalItems = defaultInt(task.getTotalItems());
        checkpoint.skippedItems = defaultInt(task.getSkippedItems());
        checkpoint.failedItems = defaultInt(task.getFailedItems());
        checkpoint.rootPath = task.getRootPath();
        checkpoint.lastProcessedPath = task.getLastProcessedPath();
        checkpoint.lastProcessedType = lastProcessedType;
        checkpoint.resumeFromPath = task.getLastProcessedPath();
        checkpoint.resumeFromType = resumeFromType;
        checkpoint.updatedAt = LocalDateTime.now().toString();
        try {
            return objectMapper.writeValueAsString(checkpoint);
        } catch (JsonProcessingException e) {
            log.debug("序列化扫描任务断点失败: {}", task.getId(), e);
            return "{}";
        }
    }

    private ScanCheckpoint readCheckpoint(ScanTask task) {
        if (task == null || task.getCheckpointJson() == null || task.getCheckpointJson().isBlank()) {
            return new ScanCheckpoint();
        }
        try {
            return objectMapper.readValue(task.getCheckpointJson(), ScanCheckpoint.class);
        } catch (Exception e) {
            log.debug("解析扫描任务断点失败: {}", task.getId(), e);
            return new ScanCheckpoint();
        }
    }

    private Map<String, Object> toCheckpointMap(ScanCheckpoint checkpoint) {
        String rootPathDisplay = toDisplayPath(checkpoint.rootPath);
        String lastProcessedPathDisplay = toDisplayPath(checkpoint.lastProcessedPath);
        String resumeFromPathDisplay = toDisplayPath(checkpoint.resumeFromPath);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("processedItems", checkpoint.processedItems);
        resp.put("totalItems", checkpoint.totalItems);
        resp.put("skippedItems", checkpoint.skippedItems);
        resp.put("failedItems", checkpoint.failedItems);
        resp.put("rootPath", rootPathDisplay);
        resp.put("rootPathDisplay", rootPathDisplay);
        resp.put("lastProcessedPath", lastProcessedPathDisplay);
        resp.put("lastProcessedPathDisplay", lastProcessedPathDisplay);
        resp.put("lastProcessedType", checkpoint.lastProcessedType);
        resp.put("resumeFromPath", resumeFromPathDisplay);
        resp.put("resumeFromPathDisplay", resumeFromPathDisplay);
        resp.put("resumeFromType", checkpoint.resumeFromType);
        resp.put("updatedAt", checkpoint.updatedAt);
        return resp;
    }

    private String toDisplayPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String tenantRelative = userPathService.toTenantRelativePhotoPath(rawPath);
        if (tenantRelative != null && !tenantRelative.isBlank() && !tenantRelative.equals(rawPath)) {
            return tenantRelative;
        }
        return rawPath;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String inferPathType(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        try {
            java.nio.file.Path path = Paths.get(rawPath);
            if (java.nio.file.Files.isDirectory(path)) {
                return "DIRECTORY";
            }
            if (java.nio.file.Files.isRegularFile(path)) {
                return "FILE";
            }
        } catch (Exception ignored) {
        }
        String name = Paths.get(rawPath).getFileName() != null ? Paths.get(rawPath).getFileName().toString() : rawPath;
        return name.contains(".") ? "FILE" : "DIRECTORY";
    }

    private final class TaskProgressTracker implements PhotoScanService.ScanProgressListener {
        private final Long taskId;
        private final String resumeFromPath;
        private final String resumeFromType;
        private final int initialProcessedItems;
        private int processedItems;
        private int totalItems;
        private int skippedItems;
        private int failedItems;
        private String lastProcessedPath;
        private String lastProcessedType;
        private long lastFlushAt = 0L;

        private TaskProgressTracker(Long taskId) {
            this.taskId = taskId;
            ScanTask task = scanTaskRepository.findById(taskId).orElse(null);
            ScanCheckpoint checkpoint = readCheckpoint(task);
            this.resumeFromPath = task != null && task.getTaskType() == ScanTaskType.RESUME_SCAN
                ? firstNonBlank(task.getLastProcessedPath(), checkpoint.resumeFromPath, checkpoint.lastProcessedPath)
                : null;
            this.resumeFromType = task != null && task.getTaskType() == ScanTaskType.RESUME_SCAN
                ? firstNonBlank(checkpoint.resumeFromType, checkpoint.lastProcessedType, inferPathType(this.resumeFromPath))
                : null;
            this.initialProcessedItems = task != null
                ? Math.max(defaultInt(task.getProcessedItems()), checkpoint.processedItems)
                : checkpoint.processedItems;
            this.skippedItems = checkpoint.skippedItems;
            this.failedItems = checkpoint.failedItems;
            this.lastProcessedPath = firstNonBlank(task != null ? task.getLastProcessedPath() : null,
                checkpoint.lastProcessedPath, checkpoint.resumeFromPath);
            this.lastProcessedType = firstNonBlank(checkpoint.lastProcessedType, checkpoint.resumeFromType, inferPathType(lastProcessedPath));
        }

        @Override
        public PhotoScanService.ScanControlAction getControlAction() {
            return taskControlActions.getOrDefault(taskId, PhotoScanService.ScanControlAction.CONTINUE);
        }

        @Override
        public String getResumeFromPath() {
            return resumeFromPath;
        }

        @Override
        public String getResumeFromType() {
            return resumeFromType;
        }

        @Override
        public int getInitialProcessedItems() {
            return initialProcessedItems;
        }

        @Override
        public void onScanPrepared(String rootPath, boolean force, int totalItems) {
            this.totalItems = totalItems;
            flush(true, task -> {
                task.setRootPath(rootPath);
                task.setTotalItems(totalItems);
                task.setTaskType(force ? ScanTaskType.FULL_SCAN : task.getTaskType());
            });
        }

        @Override
        public void onPathProcessed(String absolutePath, String pathType, int current, int total) {
            this.processedItems = current;
            this.totalItems = total;
            this.lastProcessedPath = absolutePath;
            this.lastProcessedType = firstNonBlank(pathType, inferPathType(absolutePath));
            flush(false, task -> {
                task.setProcessedItems(processedItems);
                task.setTotalItems(totalItems);
                task.setLastProcessedPath(lastProcessedPath);
            });
        }

        @Override
        public void onPathSkipped(String absolutePath, String pathType, String reason, String detail, int current, int total) {
            this.skippedItems++;
            onPathProcessed(absolutePath, pathType, current, total);
            flush(false, task -> task.setSkippedItems(skippedItems));
        }

        @Override
        public void onPathFailed(String absolutePath, String pathType, String errorMessage, int current, int total) {
            this.failedItems++;
            this.lastProcessedPath = absolutePath;
            this.lastProcessedType = firstNonBlank(pathType, inferPathType(absolutePath));
            this.processedItems = Math.max(this.processedItems, current);
            this.totalItems = Math.max(this.totalItems, total);
            flush(false, task -> {
                task.setProcessedItems(processedItems);
                task.setTotalItems(totalItems);
                task.setFailedItems(failedItems);
                task.setLastProcessedPath(lastProcessedPath);
                task.setErrorMessage(errorMessage);
            });
        }

        @Override
        public void onScanCompleted(int processed, int total, int skipped, int failed) {
            this.processedItems = processed;
            this.totalItems = total;
            this.skippedItems = Math.max(this.skippedItems, skipped);
            this.failedItems = Math.max(this.failedItems, failed);
            flush(true, task -> {
                task.setStatus(ScanTaskStatus.COMPLETED);
                task.setProcessedItems(processedItems);
                task.setTotalItems(totalItems);
                task.setSkippedItems(skippedItems);
                task.setFailedItems(failedItems);
                task.setFinishedAt(LocalDateTime.now());
                task.setErrorMessage(null);
            });
            clearTaskControlAction(taskId);
        }

        @Override
        public void onScanFailed(Exception exception, int processed, int total) {
            this.processedItems = Math.max(this.processedItems, processed);
            this.totalItems = Math.max(this.totalItems, total);
            flush(true, task -> {
                if (exception instanceof PhotoScanService.ScanInterruptedException) {
                    PhotoScanService.ScanInterruptedException interrupted = (PhotoScanService.ScanInterruptedException) exception;
                    String interruptedPath = interrupted.getPath() != null ? interrupted.getPath() : lastProcessedPath;
                    this.lastProcessedPath = interruptedPath;
                    this.lastProcessedType = inferPathType(interruptedPath);
                    task.setStatus(interrupted.getAction() == PhotoScanService.ScanControlAction.PAUSE
                        ? ScanTaskStatus.PAUSED
                        : ScanTaskStatus.CANCELED);
                    task.setTaskType(hasProgress(task) ? ScanTaskType.RESUME_SCAN : task.getTaskType());
                    task.setErrorMessage(interrupted.getAction() == PhotoScanService.ScanControlAction.PAUSE
                        ? "任务已暂停"
                        : "任务已取消");
                    task.setLastProcessedPath(interruptedPath);
                } else {
                    task.setStatus(ScanTaskStatus.FAILED);
                    task.setTaskType(hasProgress(task) ? ScanTaskType.RESUME_SCAN : task.getTaskType());
                    task.setErrorMessage(buildErrorMessage(exception));
                    task.setLastProcessedPath(lastProcessedPath);
                }
                task.setProcessedItems(processedItems);
                task.setTotalItems(totalItems);
                task.setSkippedItems(skippedItems);
                task.setFailedItems(Math.max(failedItems, exception instanceof PhotoScanService.ScanInterruptedException ? failedItems : 1));
                task.setFinishedAt(LocalDateTime.now());
            });
            clearTaskControlAction(taskId);
        }

        private void flush(boolean force, java.util.function.Consumer<ScanTask> mutator) {
            long now = System.currentTimeMillis();
            if (!force && now - lastFlushAt < 1000L) {
                return;
            }
            lastFlushAt = now;
            saveTask(mutator);
        }

        private void saveTask(java.util.function.Consumer<ScanTask> mutator) {
            scanTaskRepository.findById(taskId).ifPresent(task -> {
                mutator.accept(task);
                String resumeFromType = task.getTaskType() == ScanTaskType.RESUME_SCAN ? lastProcessedType : inferPathType(task.getLastProcessedPath());
                task.setCheckpointJson(buildCheckpointJson(task, lastProcessedType, resumeFromType));
                scanTaskRepository.save(task);
            });
        }

        private String buildErrorMessage(Exception exception) {
            if (exception == null) {
                return "扫描失败";
            }
            String message = exception.getMessage();
            if (message == null || message.isBlank()) {
                message = exception.getClass().getSimpleName();
            }
            return message.length() > 500 ? message.substring(0, 500) : message;
        }

        private String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            }
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
            return null;
        }
    }
}
