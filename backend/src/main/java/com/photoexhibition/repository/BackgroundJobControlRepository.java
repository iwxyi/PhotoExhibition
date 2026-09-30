package com.photoexhibition.repository;

import com.photoexhibition.entity.BackgroundJobControl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BackgroundJobControlRepository extends JpaRepository<BackgroundJobControl, Long> {
    Optional<BackgroundJobControl> findByScopeTypeAndScopeKey(String scopeType, String scopeKey);
}
