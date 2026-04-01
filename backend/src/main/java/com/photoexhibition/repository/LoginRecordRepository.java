package com.photoexhibition.repository;

import com.photoexhibition.entity.LoginRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoginRecordRepository extends JpaRepository<LoginRecord, Long> {
    List<LoginRecord> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    List<LoginRecord> findTop200ByOrderByCreatedAtDesc();

    List<LoginRecord> findTop200ByUserIdOrderByCreatedAtDesc(Long userId);

    Page<LoginRecord> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<LoginRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
