package com.photoexhibition.repository;

import com.photoexhibition.entity.StorageProvider;
import com.photoexhibition.entity.StorageType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StorageProviderRepository extends JpaRepository<StorageProvider, Long> {
    Optional<StorageProvider> findByName(String name);

    boolean existsByName(String name);

    Optional<StorageProvider> findFirstByIsDefaultTrue();

    List<StorageProvider> findAllByOrderByPriorityAscIdAsc();

    List<StorageProvider> findByEnabledTrueOrderByPriorityAscIdAsc();

    List<StorageProvider> findByTypeOrderByPriorityAscIdAsc(StorageType type);
}
