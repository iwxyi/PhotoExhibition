package com.photoexhibition.service;

import com.photoexhibition.entity.*;
import com.photoexhibition.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class BackgroundTaskRetentionServiceTest {
    @Mock BackgroundJobRepository jobs;
    @Mock BackgroundJobItemRepository items;
    @Mock ScanTaskRepository scans;
    @Mock ScanTaskIssueRepository files;
    @Mock PlatformTransactionManager transactionManager;
    @InjectMocks BackgroundTaskRetentionService service;

    @Test void deletesOnlyExpiredTerminalTasksAndProtectsRetrySources() {
        LocalDateTime old = LocalDateTime.now().minusDays(100);
        BackgroundJob completed = new BackgroundJob(); completed.setId(1L); completed.setStatus(BackgroundJobStatus.SUCCEEDED); completed.setUpdatedAt(old);
        BackgroundJob resumed = new BackgroundJob(); resumed.setId(2L); resumed.setStatus(BackgroundJobStatus.PAUSED); resumed.setUpdatedAt(old);
        BackgroundJob source = new BackgroundJob(); source.setId(3L); source.setStatus(BackgroundJobStatus.RETRIED); source.setUpdatedAt(old);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jobs.findExpired(any(),any(),any())).thenReturn(List.of(completed,resumed,source));
        when(jobs.findForUpdate(1L)).thenReturn(Optional.of(completed));
        when(jobs.findForUpdate(2L)).thenReturn(Optional.of(resumed));
        when(jobs.findForUpdate(3L)).thenReturn(Optional.of(source));
        when(jobs.findFirstBySourceJobIdOrderByIdAsc(1L)).thenReturn(Optional.empty());
        when(jobs.findFirstBySourceJobIdOrderByIdAsc(3L)).thenReturn(Optional.of(new BackgroundJob()));
        ScanTask scan = new ScanTask(); scan.setId(8L); scan.setStatus(ScanTaskStatus.FAILED); scan.setUpdatedAt(old);
        when(scans.findExpired(anyList(),any(),any())).thenReturn(List.of(scan));
        when(scans.findForUpdate(8L)).thenReturn(Optional.of(scan));
        service.cleanExpired();
        verify(items).deleteByJobId(1L); verify(jobs).delete(completed);
        verify(items,never()).deleteByJobId(2L); verify(items,never()).deleteByJobId(3L);
        verify(files).deleteByTaskId(8L); verify(scans).delete(scan);
        ArgumentCaptor<Collection<BackgroundJobStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(jobs).findExpired(statuses.capture(),any(),any());
        assertFalse(statuses.getValue().contains(BackgroundJobStatus.RUNNING));
        assertFalse(statuses.getValue().contains(BackgroundJobStatus.BLOCKED));
    }

    @Test void zeroDisablesCleanup() {
        ReflectionTestUtils.setField(service,"retentionDays",0);
        service.cleanExpired();
        verifyNoInteractions(jobs,items,scans,files,transactionManager);
    }
}
