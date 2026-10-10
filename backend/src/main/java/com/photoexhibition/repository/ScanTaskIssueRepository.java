package com.photoexhibition.repository;

import com.photoexhibition.entity.ScanTaskIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScanTaskIssueRepository extends JpaRepository<ScanTaskIssue, Long> {
    java.util.Optional<ScanTaskIssue> findFirstByTaskIdAndFileKey(Long taskId, String fileKey);
    @org.springframework.data.jpa.repository.Query("select i from ScanTaskIssue i where i.taskId = :taskId and (i.status is null or i.status <> 'SUCCEEDED') order by i.id")
    Page<ScanTaskIssue> findIssues(@org.springframework.data.repository.query.Param("taskId") Long taskId, Pageable pageable);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from ScanTaskIssue i where i.taskId = :id")
    void deleteByTaskId(@org.springframework.data.repository.query.Param("id") Long taskId);
    Page<ScanTaskIssue> findByTaskIdOrderByIdAsc(Long taskId, Pageable pageable);
}
