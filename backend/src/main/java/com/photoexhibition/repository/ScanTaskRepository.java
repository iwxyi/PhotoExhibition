package com.photoexhibition.repository;

import com.photoexhibition.entity.ScanTask;
import com.photoexhibition.entity.ScanTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScanTaskRepository extends JpaRepository<ScanTask, Long> {
    List<ScanTask> findByStatusOrderByPriorityDescCreatedAtAsc(ScanTaskStatus status);

    List<ScanTask> findByStatusInOrderByPriorityDescCreatedAtAsc(List<ScanTaskStatus> statuses);

    Optional<ScanTask> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ScanTaskStatus status);

    List<ScanTask> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    List<ScanTask> findTop50ByRequestedByUserIdOrderByCreatedAtDesc(Long requestedByUserId);

    List<ScanTask> findAllByOrderByCreatedAtDesc();

    List<ScanTask> findByRequestedByUserIdOrderByCreatedAtDesc(Long requestedByUserId);

    @Query("SELECT t FROM ScanTask t WHERE t.status IN :statuses AND t.userId = :userId ORDER BY t.createdAt DESC")
    List<ScanTask> findByUserIdAndStatusInOrderByCreatedAtDesc(@Param("userId") Long userId,
                                                               @Param("statuses") List<ScanTaskStatus> statuses);

    @Query("SELECT t FROM ScanTask t WHERE t.status IN :statuses AND t.requestedByUserId = :requestedByUserId ORDER BY t.createdAt DESC")
    List<ScanTask> findByRequestedByUserIdAndStatusInOrderByCreatedAtDesc(@Param("requestedByUserId") Long requestedByUserId,
                                                                          @Param("statuses") List<ScanTaskStatus> statuses);

    @Query("SELECT t FROM ScanTask t WHERE t.status = :status AND t.scheduledTask = true")
    List<ScanTask> findByStatusAndScheduledTaskTrue(@Param("status") ScanTaskStatus status);
}
