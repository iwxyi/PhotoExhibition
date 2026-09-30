package com.photoexhibition.service;

import com.photoexhibition.entity.BackgroundJobResourceLane;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Creates optional downstream work without coupling the scan worker to remote AI. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PhotoScanCompletionListener {
    private final SystemConfigService systemConfigService;
    private final PhotoRepository photoRepository;
    private final UserAccountRepository userAccountRepository;
    private final BackgroundJobService backgroundJobService;
    private final PhotoVisualAnalysisService visualAnalysisService;

    @EventListener
    public void onPhotoScanned(PhotoScanCompletedEvent event) {
        if (event == null || event.getPhotoId() == null || event.getOwnerUserId() == null
            || !systemConfigService.isAiVisualAnalysisEnabled()
            || isBlank(systemConfigService.getAiSearchApiUrl())
            || isBlank(systemConfigService.getAiSearchApiKey())) {
            return;
        }
        Photo photo = photoRepository.findById(event.getPhotoId()).orElse(null);
        if (photo == null || !event.getOwnerUserId().equals(photo.getUserId())
            || !com.photoexhibition.entity.ProcessingStatus.COMPLETED.equals(photo.getProcessingStatus())
            || visualAnalysisService.isCompleted(photo.getId())) {
            return;
        }
        UserAccount owner = userAccountRepository.findById(event.getOwnerUserId()).orElse(null);
        if (owner == null) return;
        try {
            backgroundJobService.enqueuePhotoJob(owner, owner.getId(), "VISUAL_ANALYSIS",
                BackgroundJobResourceLane.REMOTE_AI, java.util.List.of(photo.getId()), false, true,
                80, "1", null, Map.of("forceReanalyze", false));
        } catch (Exception e) {
            // Scan success must not be rolled back because an optional paid downstream task failed to enqueue.
            log.warn("扫描完成后创建 AI 分析任务失败 photoId={}: {}", photo.getId(), e.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
