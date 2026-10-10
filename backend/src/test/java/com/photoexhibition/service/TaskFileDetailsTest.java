package com.photoexhibition.service;

import com.photoexhibition.entity.*;
import com.photoexhibition.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskFileDetailsTest {
    @Mock BackgroundJobRepository jobRepository;
    @Mock BackgroundJobItemRepository itemRepository;
    @Mock BackgroundJobControlRepository controlRepository;
    @Mock PhotoRepository photoRepository;
    @Mock AlbumRepository albumRepository;
    @Mock UserAccountRepository userAccountRepository;
    @Mock PhotoVisualAnalysisService visualAnalysisService;
    @Mock PhotoScanService photoScanService;
    @Mock PhotoAIScoringService photoAIScoringService;
    @Mock UserPathService userPathService;
    @InjectMocks BackgroundJobService service;

    @Test void taskSummaryExposesActivityAndFixedTerminalDuration() {
        UserAccount viewer = new UserAccount(); viewer.setId(4L); viewer.setRole(UserRole.USER_ADMIN);
        BackgroundJob job = new BackgroundJob(); job.setId(9L); job.setOwnerUserId(4L);
        job.setJobType("AI_SCORING"); job.setStatus(BackgroundJobStatus.BLOCKED);
        job.setBlockingReason("QUOTA_EXHAUSTED");
        java.time.LocalDateTime start = java.time.LocalDateTime.of(2026, 10, 10, 10, 0);
        job.setStartedAt(start); job.setFinishedAt(start.plusSeconds(90)); job.setUpdatedAt(start.plusSeconds(90));
        when(jobRepository.findById(9L)).thenReturn(Optional.of(job));
        Map<String, Object> summary = service.getSummary(viewer, 9L);
        assertEquals("AI_SCORING", summary.get("currentStage"));
        assertEquals("QUOTA_EXHAUSTED", summary.get("waitingReason"));
        assertEquals(start.plusSeconds(90), summary.get("lastActivityAt"));
        assertEquals(90L, summary.get("durationSeconds"));
        job.setStartedAt(null);
        assertNull(service.getSummary(viewer, 9L).get("durationSeconds"));
    }

    @Test void deletedPhotoRetainsSnapshotAndPageIsBounded() {
        UserAccount viewer = new UserAccount(); viewer.setId(4L); viewer.setRole(UserRole.USER_ADMIN);
        BackgroundJob job = new BackgroundJob(); job.setId(9L); job.setOwnerUserId(4L);
        BackgroundJobItem item = new BackgroundJobItem(); item.setId(7L); item.setPhotoId(3L);
        item.setTargetKey("PHOTO:3"); item.setTargetName("deleted.jpg"); item.setTargetPath("album/deleted.jpg");
        when(jobRepository.findById(9L)).thenReturn(Optional.of(job));
        when(itemRepository.findByJobIdOrderByIdAsc(9L, PageRequest.of(0,100)))
            .thenReturn(new PageImpl<>(List.of(item)));
        when(photoRepository.findAllById(Set.of(3L))).thenReturn(List.of());
        when(albumRepository.findAllById(Set.of())).thenReturn(List.of());
        when(userAccountRepository.findById(4L)).thenReturn(Optional.of(viewer));
        Map<String,Object> row = service.getFiles(viewer,9L,-2,1000).getContent().get(0);
        assertEquals("deleted.jpg",row.get("name"));
        assertEquals("album/deleted.jpg",row.get("path"));
        assertEquals("目标已删除",row.get("message"));
        verify(photoRepository,never()).findById(anyLong());
        viewer.setId(5L);
        assertThrows(SecurityException.class, () -> service.getFiles(viewer,9L,0,20));
        verify(itemRepository,times(1)).findByJobIdOrderByIdAsc(anyLong(),any(Pageable.class));
    }

    @Test void unknownAbsoluteRootNeverLeaks() {
        assertEquals("a.jpg", TaskFileService.relativePath(userPathService,"/private/server/root/a.jpg"));
        assertEquals("a.jpg", TaskFileService.relativePath(userPathService,"C:\\private\\a.jpg"));
        when(userPathService.extractTenantRelativePhotoPath("/photos/4/album/a.jpg")).thenReturn("album/a.jpg");
        assertEquals("album/a.jpg",TaskFileService.relativePath(userPathService,"/photos/4/album/a.jpg"));
        assertEquals("/album/a.jpg", TaskFileService.relativePath(userPathService, "/data/photos/4/album/a.jpg", 4L));
    }
}
