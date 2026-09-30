package com.photoexhibition.repository;

import com.photoexhibition.entity.BackgroundJobItem;
import com.photoexhibition.entity.BackgroundJobItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BackgroundJobItemRepository extends JpaRepository<BackgroundJobItem, Long> {
    List<BackgroundJobItem> findByJobIdOrderByIdAsc(Long jobId);
    List<BackgroundJobItem> findTop50ByJobIdAndStatusInOrderByIdAsc(Long jobId, Collection<BackgroundJobItemStatus> statuses);
    Optional<BackgroundJobItem> findFirstByOwnerUserIdAndTargetKeyAndStageAndPipelineVersionAndParametersHashAndStatusInOrderByUpdatedAtDesc(
        Long ownerUserId, String targetKey, String stage, String pipelineVersion, String parametersHash,
        Collection<BackgroundJobItemStatus> statuses);
    long countByJobIdAndStatus(Long jobId, BackgroundJobItemStatus status);
    long countByJobId(Long jobId);
}
