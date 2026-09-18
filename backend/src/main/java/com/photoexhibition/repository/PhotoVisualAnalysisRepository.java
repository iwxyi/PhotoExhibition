package com.photoexhibition.repository;

import com.photoexhibition.entity.PhotoVisualAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PhotoVisualAnalysisRepository extends JpaRepository<PhotoVisualAnalysis, Long> {
    Optional<PhotoVisualAnalysis> findByPhotoId(Long photoId);
}
