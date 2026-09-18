package com.photoexhibition.repository;

import com.photoexhibition.entity.PhotoVisualAnalysisJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PhotoVisualAnalysisJobRepository extends JpaRepository<PhotoVisualAnalysisJob, Long> {
    List<PhotoVisualAnalysisJob> findByStatusOrderByCreatedAtAsc(String status);
    List<PhotoVisualAnalysisJob> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    List<PhotoVisualAnalysisJob> findTop50ByRequestedByUserIdOrderByCreatedAtDesc(Long requestedByUserId);
}
