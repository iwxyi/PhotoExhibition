package com.photoexhibition.service;

import com.photoexhibition.entity.*;
import com.photoexhibition.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackgroundTaskRetentionService {
    private static final List<BackgroundJobStatus> JOB_TERMINAL = List.of(BackgroundJobStatus.SUCCEEDED,
        BackgroundJobStatus.PARTIAL_SUCCESS, BackgroundJobStatus.FAILED, BackgroundJobStatus.SKIPPED,
        BackgroundJobStatus.CANCELED, BackgroundJobStatus.IGNORED, BackgroundJobStatus.RETRIED);
    private static final List<ScanTaskStatus> SCAN_TERMINAL = List.of(ScanTaskStatus.COMPLETED,
        ScanTaskStatus.FAILED, ScanTaskStatus.CANCELED, ScanTaskStatus.IGNORED);
    private final BackgroundJobRepository jobs;
    private final BackgroundJobItemRepository items;
    private final ScanTaskRepository scans;
    private final ScanTaskIssueRepository files;
    private final PlatformTransactionManager transactionManager;
    @Value("${app.background-tasks.retention-days:90}")
    private int retentionDays = 90;

    @Scheduled(cron = "${app.background-tasks.cleanup-cron:0 30 3 * * *}")
    public void cleanExpired() {
        if (retentionDays <= 0) return;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        // Bound each run and each transaction; never hold locks across the entire history.
        for (BackgroundJob candidate : jobs.findExpired(JOB_TERMINAL, cutoff, PageRequest.of(0, 200))) {
            transaction.executeWithoutResult(status -> {
                BackgroundJob job = jobs.findForUpdate(candidate.getId()).orElse(null);
                if (job == null || !JOB_TERMINAL.contains(job.getStatus()) || !job.getUpdatedAt().isBefore(cutoff)
                    || jobs.findFirstBySourceJobIdOrderByIdAsc(job.getId()).isPresent()) return;
                items.deleteByJobId(job.getId());
                jobs.delete(job);
            });
        }
        for (ScanTask candidate : scans.findExpired(SCAN_TERMINAL, cutoff, PageRequest.of(0, 200))) {
            transaction.executeWithoutResult(status -> {
                ScanTask task = scans.findForUpdate(candidate.getId()).orElse(null);
                if (task == null || !SCAN_TERMINAL.contains(task.getStatus()) || !task.getUpdatedAt().isBefore(cutoff)) return;
                files.deleteByTaskId(task.getId());
                scans.delete(task);
            });
        }
    }
}
