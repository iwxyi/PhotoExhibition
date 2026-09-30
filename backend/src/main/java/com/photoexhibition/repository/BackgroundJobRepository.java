package com.photoexhibition.repository;

import com.photoexhibition.entity.BackgroundJob;
import com.photoexhibition.entity.BackgroundJobResourceLane;
import com.photoexhibition.entity.BackgroundJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import javax.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from BackgroundJob j where j.id = :id")
    Optional<BackgroundJob> findForUpdate(@Param("id") Long id);

    Optional<BackgroundJob> findFirstBySourceJobIdOrderByIdAsc(Long sourceJobId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE background_job source JOIN background_job retry ON retry.source_job_id = source.id "
        + "SET source.status = 'RETRIED' WHERE source.status IN ('FAILED','PARTIAL_SUCCESS','BLOCKED')", nativeQuery = true)
    int markExistingRetrySources();
    List<BackgroundJob> findTop8ByJobTypeOrderByCreatedAtDesc(String jobType);
    long countByJobTypeStartingWithAndStatusIn(String jobTypePrefix, Collection<BackgroundJobStatus> statuses);
    long countByJobTypeAndStatusIn(String jobType, Collection<BackgroundJobStatus> statuses);
    long countByOwnerUserIdAndJobTypeAndStatusIn(Long ownerUserId, String jobType,
                                                Collection<BackgroundJobStatus> statuses);
    List<BackgroundJob> findTop100ByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);
    List<BackgroundJob> findTop200ByOrderByCreatedAtDesc();
    List<BackgroundJob> findByStatusInOrderByPriorityDescCreatedAtAsc(Collection<BackgroundJobStatus> statuses);
    List<BackgroundJob> findByStatusInOrderByCreatedAtDesc(Collection<BackgroundJobStatus> statuses);
    List<BackgroundJob> findByResourceLaneAndStatusInOrderByPriorityDescCreatedAtAsc(
        BackgroundJobResourceLane lane, Collection<BackgroundJobStatus> statuses);
    List<BackgroundJob> findByOwnerUserIdAndStatusIn(Long ownerUserId, Collection<BackgroundJobStatus> statuses);
}
