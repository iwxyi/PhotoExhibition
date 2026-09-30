package com.photoexhibition.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.ScanTask;
import com.photoexhibition.entity.BackgroundJobControl;
import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.entity.StorageType;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.repository.ScanTaskRepository;
import com.photoexhibition.repository.BackgroundJobControlRepository;
import com.photoexhibition.repository.StorageProviderRepository;
import com.photoexhibition.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScanTaskServiceTest {

    @Mock
    private ScanTaskRepository scanTaskRepository;

    @Mock
    private BackgroundJobControlRepository backgroundJobControlRepository;

    @Mock
    private PhotoScanService photoScanService;

    @Mock
    private UserPathService userPathService;

    @Mock
    private SystemConfigService systemConfigService;

    @Mock
    private StorageProviderService storageProviderService;

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private StorageProviderRepository storageProviderRepository;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private ScanTaskService scanTaskService;

    @BeforeEach
    void setUp() {
        scanTaskService = new ScanTaskService(
            scanTaskRepository,
            backgroundJobControlRepository,
            photoScanService,
            userPathService,
            systemConfigService,
            storageProviderService,
            storageProviderRepository,
            userAccountRepository,
            new ObjectMapper(),
            transactionManager
        );
    }

    @Test
    void queuedScanPauseIsImmediateAndIdempotent() {
        ScanTask task = new ScanTask();
        task.setId(81L);
        task.setStatus(com.photoexhibition.entity.ScanTaskStatus.QUEUED);
        UserAccount admin = new UserAccount();
        admin.setRole(UserRole.SUPER_ADMIN);
        when(scanTaskRepository.findById(81L)).thenReturn(Optional.of(task));
        scanTaskService.pauseTask(admin, 81L);
        scanTaskService.pauseTask(admin, 81L);
        assertEquals(com.photoexhibition.entity.ScanTaskStatus.PAUSED, task.getStatus());
        assertEquals("MANUAL", task.getPauseSource());
        verify(scanTaskRepository, times(1)).save(task);
        task.setStatus(com.photoexhibition.entity.ScanTaskStatus.FAILED);
        assertThrows(IllegalStateException.class, () -> scanTaskService.pauseTask(admin, 81L));
    }

    @Test
    void workerFailureSettlesEvenWithoutProgressNotification() {
        ScanTask task = new ScanTask();
        task.setId(82L);
        task.setStatus(com.photoexhibition.entity.ScanTaskStatus.RUNNING);
        when(scanTaskRepository.findById(82L)).thenReturn(Optional.of(task));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("missing root"))
            .when(photoScanService).runWithStorageContext(
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Runnable.class));
        ReflectionTestUtils.invokeMethod(scanTaskService, "runTask", task);
        assertEquals(com.photoexhibition.entity.ScanTaskStatus.FAILED, task.getStatus());
        assertEquals("missing root", task.getErrorMessage());
        verify(scanTaskRepository).save(task);
    }

    @Test
    void ignorePreservesFailedScanAndChecksOwnership() {
        ScanTask task = new ScanTask();
        task.setId(80L);
        task.setRequestedByUserId(7L);
        task.setStatus(com.photoexhibition.entity.ScanTaskStatus.FAILED);
        task.setErrorMessage("missing file");
        UserAccount owner = new UserAccount();
        owner.setId(7L);
        owner.setRole(UserRole.USER_ADMIN);
        when(scanTaskRepository.findById(80L)).thenReturn(Optional.of(task));
        scanTaskService.ignoreTask(owner, 80L);
        assertEquals(com.photoexhibition.entity.ScanTaskStatus.IGNORED, task.getStatus());
        assertEquals("missing file", task.getErrorMessage());
        scanTaskService.ignoreTask(owner, 80L);
        verify(scanTaskRepository, times(1)).save(task);
        assertThrows(IllegalStateException.class, () -> scanTaskService.retryTask(owner, 80L));
        task.setStatus(com.photoexhibition.entity.ScanTaskStatus.RUNNING);
        assertThrows(IllegalStateException.class, () -> scanTaskService.ignoreTask(owner, 80L));
        owner.setId(8L);
        assertThrows(RuntimeException.class, () -> scanTaskService.ignoreTask(owner, 80L));
    }

    @Test
    void globalPausePreventsClaimsUntilResumed() {
        ScanTask pending = task(1L, 100, 10L, LocalDateTime.now());
        BackgroundJobControl control = new BackgroundJobControl();
        control.setPaused(true);
        when(backgroundJobControlRepository.findByScopeTypeAndScopeKey("GLOBAL", "ALL"))
            .thenAnswer(invocation -> Optional.of(control));
        when(scanTaskRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(
            List.of(com.photoexhibition.entity.ScanTaskStatus.PENDING, com.photoexhibition.entity.ScanTaskStatus.QUEUED)))
            .thenReturn(List.of(pending));

        assertEquals(Optional.empty(), scanTaskService.claimNextTask());
        control.setPaused(false);
        assertEquals(Optional.of(pending), scanTaskService.claimNextTask());
    }

    @Test
    void shouldRotateAcrossOwnersForSamePriorityTasks() {
        ScanTask task1 = task(1L, 100, 10L, LocalDateTime.of(2026, 3, 23, 10, 0));
        ScanTask task2 = task(2L, 100, 10L, LocalDateTime.of(2026, 3, 23, 10, 1));
        ScanTask task3 = task(3L, 100, 20L, LocalDateTime.of(2026, 3, 23, 10, 2));

        ScanTask selected = scanTaskService.chooseNextTaskForClaim(
            List.of(task1, task2, task3),
            "REQUESTER:10"
        );

        assertEquals(3L, selected.getId());
    }

    @Test
    void shouldKeepHighestPriorityEvenIfOwnerMatchesLastDispatch() {
        ScanTask task1 = task(1L, 200, 10L, LocalDateTime.of(2026, 3, 23, 10, 0));
        ScanTask task2 = task(2L, 100, 20L, LocalDateTime.of(2026, 3, 23, 10, 1));

        ScanTask selected = scanTaskService.chooseNextTaskForClaim(
            List.of(task1, task2),
            "REQUESTER:10"
        );

        assertEquals(1L, selected.getId());
    }

    @Test
    void pauseCheckpointDoesNotSkipTheUnfinishedPath() throws Exception {
        ScanTask task = task(9L, 100, 10L, LocalDateTime.now());
        task.setRootPath("/data/photos/10");
        when(scanTaskRepository.findById(9L)).thenReturn(Optional.of(task));
        java.lang.reflect.Constructor<?> constructor = Class.forName(
            "com.photoexhibition.service.ScanTaskService$TaskProgressTracker")
            .getDeclaredConstructor(ScanTaskService.class, Long.class);
        constructor.setAccessible(true);
        PhotoScanService.ScanProgressListener tracker = (PhotoScanService.ScanProgressListener)
            constructor.newInstance(scanTaskService, 9L);

        tracker.onPathProcessed("/data/photos/10/a.jpg", "FILE", 1, 2);
        tracker.onScanFailed(new PhotoScanService.ScanInterruptedException(
            PhotoScanService.ScanControlAction.PAUSE, "/data/photos/10/b.jpg"), 1, 2);

        assertEquals("/data/photos/10/a.jpg", task.getLastProcessedPath());
        assertEquals(com.photoexhibition.entity.ScanTaskType.RESUME_SCAN, task.getTaskType());
        assertEquals(com.photoexhibition.entity.ScanTaskStatus.PAUSED, task.getStatus());
    }

    @Test
    void scanYieldsToWaitingOwnerAndKeepsItsLastCompletedPath() throws Exception {
        ScanTask task = task(9L, 100, 10L, LocalDateTime.now());
        task.setRootPath("/data/photos/10");
        ScanTask waiting = task(10L, 100, 20L, LocalDateTime.now());
        when(scanTaskRepository.findById(9L)).thenReturn(Optional.of(task));
        when(scanTaskRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(
            List.of(com.photoexhibition.entity.ScanTaskStatus.PENDING, com.photoexhibition.entity.ScanTaskStatus.QUEUED)))
            .thenReturn(List.of(waiting));
        java.lang.reflect.Constructor<?> constructor = Class.forName(
            "com.photoexhibition.service.ScanTaskService$TaskProgressTracker")
            .getDeclaredConstructor(ScanTaskService.class, Long.class);
        constructor.setAccessible(true);
        PhotoScanService.ScanProgressListener tracker = (PhotoScanService.ScanProgressListener)
            constructor.newInstance(scanTaskService, 9L);

        assertEquals(0, ReflectionTestUtils.getField(tracker, "initialProcessedItems"));

        for (int i = 1; i <= 20; i++) {
            tracker.onPathProcessed("/data/photos/10/" + i + ".jpg", "FILE", i, 30);
        }
        verify(scanTaskRepository).findByStatusInOrderByPriorityDescCreatedAtAsc(
            List.of(com.photoexhibition.entity.ScanTaskStatus.PENDING, com.photoexhibition.entity.ScanTaskStatus.QUEUED));
        assertEquals(PhotoScanService.ScanControlAction.PAUSE, tracker.getControlAction());
        tracker.onScanFailed(new PhotoScanService.ScanInterruptedException(
            PhotoScanService.ScanControlAction.PAUSE, "/data/photos/10/21.jpg"), 20, 30);

        assertEquals(com.photoexhibition.entity.ScanTaskStatus.QUEUED, task.getStatus());
        assertEquals(com.photoexhibition.entity.ScanTaskType.RESUME_SCAN, task.getTaskType());
        assertEquals("/data/photos/10/20.jpg", task.getLastProcessedPath());
    }

    @Test
    void globalPauseDuringFairYieldRemainsResumable() throws Exception {
        ScanTask task = task(9L, 100, 10L, LocalDateTime.now());
        task.setRootPath("/data/photos/10");
        ScanTask waiting = task(10L, 100, 20L, LocalDateTime.now());
        when(scanTaskRepository.findById(9L)).thenReturn(Optional.of(task));
        when(scanTaskRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(
            List.of(com.photoexhibition.entity.ScanTaskStatus.PENDING, com.photoexhibition.entity.ScanTaskStatus.QUEUED)))
            .thenReturn(List.of(waiting));
        java.lang.reflect.Constructor<?> constructor = Class.forName(
            "com.photoexhibition.service.ScanTaskService$TaskProgressTracker")
            .getDeclaredConstructor(ScanTaskService.class, Long.class);
        constructor.setAccessible(true);
        PhotoScanService.ScanProgressListener tracker = (PhotoScanService.ScanProgressListener)
            constructor.newInstance(scanTaskService, 9L);
        for (int i = 1; i <= 20; i++) {
            tracker.onPathProcessed("/data/photos/10/" + i + ".jpg", "FILE", i, 30);
        }
        BackgroundJobControl control = new BackgroundJobControl();
        control.setPaused(true);
        when(backgroundJobControlRepository.findByScopeTypeAndScopeKey("GLOBAL", "ALL"))
            .thenReturn(Optional.of(control));
        tracker.onScanFailed(new PhotoScanService.ScanInterruptedException(
            PhotoScanService.ScanControlAction.PAUSE, "/data/photos/10/21.jpg"), 20, 30);

        assertEquals(com.photoexhibition.entity.ScanTaskStatus.PAUSED, task.getStatus());
        assertEquals("GLOBAL", task.getPauseSource());
        assertEquals("/data/photos/10/20.jpg", task.getLastProcessedPath());
    }

    @Test
    void shouldPersistCheckpointPathTypes() throws Exception {
        ScanTask task = task(9L, 100, 10L, LocalDateTime.of(2026, 3, 24, 11, 0));
        task.setRootPath("/data/photos/10");
        task.setProcessedItems(12);
        task.setTotalItems(30);
        task.setSkippedItems(2);
        task.setFailedItems(1);
        task.setLastProcessedPath("/data/photos/10/人像/样片/IMG_0001.jpg");

        String checkpointJson = ReflectionTestUtils.invokeMethod(scanTaskService, "buildCheckpointJson", task);
        JsonNode checkpoint = new ObjectMapper().readTree(checkpointJson);

        assertEquals("/data/photos/10", checkpoint.path("rootPath").asText());
        assertEquals("/data/photos/10/人像/样片/IMG_0001.jpg", checkpoint.path("lastProcessedPath").asText());
        assertEquals("FILE", checkpoint.path("lastProcessedType").asText());
        assertEquals("FILE", checkpoint.path("resumeFromType").asText());
    }

    @Test
    void shouldPersistDirectoryCheckpointTypes() throws Exception {
        ScanTask task = task(10L, 100, 10L, LocalDateTime.of(2026, 3, 24, 11, 5));
        task.setRootPath("/data/photos/10");
        task.setProcessedItems(7);
        task.setTotalItems(30);
        task.setLastProcessedPath("/data/photos/10/人像/样片");

        String checkpointJson = ReflectionTestUtils.invokeMethod(
            scanTaskService,
            "buildCheckpointJson",
            task,
            "DIRECTORY",
            "DIRECTORY"
        );
        JsonNode checkpoint = new ObjectMapper().readTree(checkpointJson);

        assertEquals("/data/photos/10/人像/样片", checkpoint.path("lastProcessedPath").asText());
        assertEquals("DIRECTORY", checkpoint.path("lastProcessedType").asText());
        assertEquals("DIRECTORY", checkpoint.path("resumeFromType").asText());
    }

    @Test
    void shouldResolveRemoteRequestedRootWithinProviderScope() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        StorageProvider provider = new StorageProvider();
        provider.setId(5L);
        provider.setType(StorageType.COS);

        when(storageProviderRepository.findById(5L)).thenReturn(java.util.Optional.of(provider));
        when(storageProviderService.resolveBrowserStorage(user, 5L)).thenReturn(
            new StorageProviderService.BrowserStorageContext(
                provider,
                java.nio.file.Path.of("/albums"),
                java.nio.file.Path.of("/albums/7"),
                java.util.List.of()
            )
        );

        java.nio.file.Path resolved = ReflectionTestUtils.invokeMethod(
            scanTaskService,
            "resolveRequestedRoot",
            user,
            "/albums/7/旅行/东京",
            5L
        );

        assertEquals(java.nio.file.Path.of("/albums/7/旅行/东京"), resolved);
    }

    @Test
    void shouldResolveRemoteRelativeRootWithoutDuplicatingUserPrefix() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        StorageProvider provider = new StorageProvider();
        provider.setId(5L);
        provider.setType(StorageType.COS);

        when(storageProviderRepository.findById(5L)).thenReturn(java.util.Optional.of(provider));
        when(storageProviderService.resolveBrowserStorage(user, 5L)).thenReturn(
            new StorageProviderService.BrowserStorageContext(
                provider,
                java.nio.file.Path.of("/albums"),
                java.nio.file.Path.of("/albums/7"),
                java.util.List.of()
            )
        );
        when(userPathService.stripLeadingUserSegment(java.nio.file.Path.of("7/旅行/东京"), 7L))
            .thenReturn(java.nio.file.Path.of("旅行/东京"));

        java.nio.file.Path resolved = ReflectionTestUtils.invokeMethod(
            scanTaskService,
            "resolveRequestedRoot",
            user,
            "7/旅行/东京",
            5L
        );

        assertEquals(java.nio.file.Path.of("/albums/7/旅行/东京"), resolved);
    }

    @Test
    void shouldResolveAdminRelativeRootWithSingleUserPrefix() {
        when(userPathService.resolvePhotoBasePath()).thenReturn(Path.of("/data/photos"));
        when(userPathService.extractUserIdFromPath("/data/photos/7/旅行/东京")).thenReturn(7L);
        when(userPathService.stripLeadingUserSegment(Path.of("7/旅行/东京"), 7L))
            .thenReturn(Path.of("旅行/东京"));

        Path resolved = ReflectionTestUtils.invokeMethod(
            scanTaskService,
            "resolveRequestedRoot",
            null,
            "7/旅行/东京",
            null
        );

        assertEquals(Path.of("/data/photos/7/旅行/东京"), resolved);
    }

    @Test
    void shouldRejectManualScanForProviderWithoutScanCapability() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setRole(UserRole.USER_ADMIN);

        StorageProvider provider = new StorageProvider();
        provider.setId(8L);
        provider.setType(StorageType.LOCAL);
        provider.setBaseDirectory("/mnt/photos");
        LinkedHashMap<String, Object> capability = new LinkedHashMap<>();
        capability.put("scanSupported", false);
        capability.put("supportMessage", "本地浏览/管理/上传/预览已接通，自动扫描仅支持扫描根目录内的路径");

        when(storageProviderRepository.findById(8L)).thenReturn(Optional.of(provider));
        when(storageProviderService.describeProviderCapabilities(provider, user)).thenReturn(capability);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> scanTaskService.enqueueScan(user, "旅行", false, 8L));

        assertEquals("扫描任务不可用：本地浏览/管理/上传/预览已接通，自动扫描仅支持扫描根目录内的路径", error.getMessage());
    }

    @Test
    void shouldRejectUploadScanForProviderWithoutScanCapability() {
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setRole(UserRole.USER_ADMIN);

        StorageProvider provider = new StorageProvider();
        provider.setId(8L);
        provider.setType(StorageType.LOCAL);
        provider.setBaseDirectory("/mnt/photos");
        LinkedHashMap<String, Object> capability = new LinkedHashMap<>();
        capability.put("scanSupported", false);
        capability.put("supportMessage", "本地浏览/管理/上传/预览已接通，自动扫描仅支持扫描根目录内的路径");

        when(storageProviderRepository.findById(8L)).thenReturn(Optional.of(provider));
        when(storageProviderService.describeProviderCapabilities(provider, user)).thenReturn(capability);

        RuntimeException error = assertThrows(RuntimeException.class,
            () -> scanTaskService.enqueueUploadScan(user, "旅行", 8L));

        assertEquals("上传后自动扫描不可用：本地浏览/管理/上传/预览已接通，自动扫描仅支持扫描根目录内的路径", error.getMessage());
    }

    @Test
    void shouldExposeRunningTaskOwnerSummaryFields() {
        UserAccount superAdmin = new UserAccount();
        superAdmin.setId(1L);
        superAdmin.setRole(UserRole.SUPER_ADMIN);

        UserAccount requester = new UserAccount();
        requester.setId(10L);
        requester.setUsername("demo-user");
        requester.setNickname("演示用户");

        ScanTask runningTask = task(11L, 100, 10L, LocalDateTime.of(2026, 3, 24, 12, 0));
        runningTask.setStatus(com.photoexhibition.entity.ScanTaskStatus.RUNNING);
        runningTask.setTaskType(com.photoexhibition.entity.ScanTaskType.INCREMENTAL_SCAN);
        runningTask.setRootPath("/data/photos/10/旅行");
        runningTask.setStartedAt(LocalDateTime.of(2026, 3, 24, 12, 5));

        @SuppressWarnings("unchecked")
        Set<Long> activeTaskIds = (Set<Long>) ReflectionTestUtils.getField(scanTaskService, "activeTaskIds");
        activeTaskIds.add(11L);

        when(scanTaskRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(runningTask));
        when(userAccountRepository.findById(10L)).thenReturn(Optional.of(requester));

        Map<String, Object> summary = scanTaskService.getStatusSummary(superAdmin);

        assertEquals(1, summary.get("runningTaskCount"));
        Object firstTask = assertInstanceOf(List.class, summary.get("runningTasks")).get(0);
        Map<?, ?> taskMap = assertInstanceOf(Map.class, firstTask);
        assertEquals("demo-user", taskMap.get("requestedByUsername"));
        assertEquals("演示用户", taskMap.get("requestedByUserNickname"));
        assertEquals("演示用户", taskMap.get("ownerLabel"));
    }

    @Test
    void shouldExposeDisplayPathsInsteadOfAbsoluteTaskPaths() {
        ScanTask task = task(12L, 100, 10L, LocalDateTime.of(2026, 3, 24, 12, 10));
        task.setRootPath("/data/photos/10/旅行");
        task.setLastProcessedPath("/data/photos/10/旅行/东京/IMG_0001.jpg");
        task.setCheckpointJson("{\"rootPath\":\"/data/photos/10/旅行\",\"lastProcessedPath\":\"/data/photos/10/旅行/东京/IMG_0001.jpg\",\"resumeFromPath\":\"/data/photos/10/旅行/东京/IMG_0001.jpg\"}");

        when(userPathService.toTenantRelativePhotoPath("/data/photos/10/旅行")).thenReturn("旅行");
        when(userPathService.toTenantRelativePhotoPath("/data/photos/10/旅行/东京/IMG_0001.jpg")).thenReturn("旅行/东京/IMG_0001.jpg");

        @SuppressWarnings("unchecked")
        Map<String, Object> response = ReflectionTestUtils.invokeMethod(scanTaskService, "toTaskMap", task);

        assertEquals("旅行", response.get("rootPath"));
        assertEquals("旅行", response.get("rootPathDisplay"));
        assertEquals("旅行/东京/IMG_0001.jpg", response.get("lastProcessedPath"));
        assertEquals("旅行/东京/IMG_0001.jpg", response.get("lastProcessedPathDisplay"));
        @SuppressWarnings("unchecked")
        Map<String, Object> checkpoint = (Map<String, Object>) response.get("checkpoint");
        assertEquals("旅行", checkpoint.get("rootPath"));
        assertEquals("旅行/东京/IMG_0001.jpg", checkpoint.get("lastProcessedPath"));
        assertEquals("旅行/东京/IMG_0001.jpg", checkpoint.get("resumeFromPath"));
    }

    @Test
    void shouldPauseAllActiveTasksOnShutdown() {
        ScanTask task1 = task(21L, 100, 10L, LocalDateTime.of(2026, 3, 24, 13, 0));
        task1.setStatus(com.photoexhibition.entity.ScanTaskStatus.RUNNING);
        ScanTask task2 = task(22L, 100, 20L, LocalDateTime.of(2026, 3, 24, 13, 1));
        task2.setStatus(com.photoexhibition.entity.ScanTaskStatus.RUNNING);

        @SuppressWarnings("unchecked")
        Set<Long> activeTaskIds = (Set<Long>) ReflectionTestUtils.getField(scanTaskService, "activeTaskIds");
        activeTaskIds.add(21L);
        activeTaskIds.add(22L);

        when(scanTaskRepository.findById(21L)).thenReturn(Optional.of(task1));
        when(scanTaskRepository.findById(22L)).thenReturn(Optional.of(task2));

        scanTaskService.shutdown();

        verify(scanTaskRepository).save(argThat(task ->
            task.getId().equals(21L) && task.getStatus() == com.photoexhibition.entity.ScanTaskStatus.PAUSED));
        verify(scanTaskRepository).save(argThat(task ->
            task.getId().equals(22L) && task.getStatus() == com.photoexhibition.entity.ScanTaskStatus.PAUSED));
    }

    @Test
    void shouldRecoverStaleRunningTasksNotHeldByWorkers() {
        ScanTask stale = task(31L, 100, 10L, LocalDateTime.of(2026, 3, 24, 14, 0));
        stale.setStatus(com.photoexhibition.entity.ScanTaskStatus.RUNNING);
        stale.setTaskType(com.photoexhibition.entity.ScanTaskType.INCREMENTAL_SCAN);
        stale.setRootPath("/data/photos/10/旅行");
        stale.setProcessedItems(5);
        stale.setLastProcessedPath("/data/photos/10/旅行/东京");

        when(scanTaskRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(stale));

        ReflectionTestUtils.invokeMethod(scanTaskService, "recoverStaleRunningTasks");

        verify(scanTaskRepository).saveAll(argThat(tasks -> {
            Object first = ((List<?>) tasks).get(0);
            if (!(first instanceof ScanTask)) {
                return false;
            }
            ScanTask task = (ScanTask) first;
            return task.getId().equals(31L)
                && task.getStatus() == com.photoexhibition.entity.ScanTaskStatus.QUEUED
                && task.getTaskType() == com.photoexhibition.entity.ScanTaskType.RESUME_SCAN
                && "检测到任务未被工作线程持有，已自动恢复排队".equals(task.getErrorMessage());
        }));
    }

    @Test
    void shouldRecoverStaleRunningTasksBeforeListing() {
        UserAccount requester = new UserAccount();
        requester.setId(10L);
        requester.setRole(UserRole.USER_ADMIN);

        ScanTask stale = task(41L, 100, 10L, LocalDateTime.of(2026, 3, 24, 15, 0));
        stale.setStatus(com.photoexhibition.entity.ScanTaskStatus.RUNNING);
        stale.setTaskType(com.photoexhibition.entity.ScanTaskType.INCREMENTAL_SCAN);
        stale.setRootPath("/data/photos/10/旅行");
        stale.setProcessedItems(3);
        stale.setLastProcessedPath("/data/photos/10/旅行/东京");

        when(scanTaskRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(stale));
        when(scanTaskRepository.findTop50ByRequestedByUserIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(stale));
        when(userPathService.toTenantRelativePhotoPath("/data/photos/10/旅行")).thenReturn("旅行");
        when(userPathService.toTenantRelativePhotoPath("/data/photos/10/旅行/东京")).thenReturn("旅行/东京");

        List<Map<String, Object>> result = scanTaskService.listTasks(requester);

        assertEquals("QUEUED", result.get(0).get("status"));
        assertEquals("RESUME_SCAN", result.get(0).get("taskType"));
        verify(scanTaskRepository, times(1)).saveAll(argThat(tasks -> {
            ScanTask task = (ScanTask) ((List<?>) tasks).get(0);
            return task.getId().equals(41L)
                && task.getStatus() == com.photoexhibition.entity.ScanTaskStatus.QUEUED;
        }));
    }

    @Test
    void shouldRecoverStaleRunningTasksBeforeStatusSummary() {
        UserAccount superAdmin = new UserAccount();
        superAdmin.setId(1L);
        superAdmin.setRole(UserRole.SUPER_ADMIN);

        ScanTask stale = task(42L, 100, 10L, LocalDateTime.of(2026, 3, 24, 15, 10));
        stale.setStatus(com.photoexhibition.entity.ScanTaskStatus.RUNNING);
        stale.setTaskType(com.photoexhibition.entity.ScanTaskType.INCREMENTAL_SCAN);
        stale.setRootPath("/data/photos/10/旅行");
        stale.setProcessedItems(1);
        stale.setLastProcessedPath("/data/photos/10/旅行/东京");

        when(scanTaskRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(stale));
        when(userPathService.toTenantRelativePhotoPath("/data/photos/10/旅行")).thenReturn("旅行");
        when(userPathService.toTenantRelativePhotoPath("/data/photos/10/旅行/东京")).thenReturn("旅行/东京");

        Map<String, Object> summary = scanTaskService.getStatusSummary(superAdmin);

        assertEquals(0, summary.get("runningTaskCount"));
        assertEquals(1L, summary.get("queuedTaskCount"));
    }

    private ScanTask task(Long id, int priority, Long requestedByUserId, LocalDateTime createdAt) {
        ScanTask task = new ScanTask();
        task.setId(id);
        task.setPriority(priority);
        task.setRequestedByUserId(requestedByUserId);
        task.setCreatedAt(createdAt);
        return task;
    }
}
