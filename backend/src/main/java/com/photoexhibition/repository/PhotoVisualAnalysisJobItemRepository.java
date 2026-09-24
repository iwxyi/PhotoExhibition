package com.photoexhibition.repository;

import com.photoexhibition.entity.PhotoVisualAnalysisJobItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhotoVisualAnalysisJobItemRepository extends JpaRepository<PhotoVisualAnalysisJobItem, Long> {
    List<PhotoVisualAnalysisJobItem> findByJobIdOrderByIdAsc(Long jobId);
    Optional<PhotoVisualAnalysisJobItem> findByJobIdAndPhotoId(Long jobId, Long photoId);
    long countByJobId(Long jobId);
}
