package com.photoexhibition.entity;

import lombok.Data;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scan_task_issue", indexes = {
    @Index(name = "idx_scan_issue_task", columnList = "task_id,id"),
    @Index(name = "idx_scan_file_key", columnList = "task_id,file_key")})
@Data
public class ScanTaskIssue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "task_id", nullable = false)
    private Long taskId;
    private Long ownerUserId;
    @Column(name = "file_key", length = 64)
    private String fileKey;
    @Column(length = 30)
    private String status;
    @Column(length = 30)
    private String pathType;
    @Column(columnDefinition = "TEXT")
    private String path;
    @Column(length = 100)
    private String reason;
    @Column(columnDefinition = "TEXT")
    private String detail;
    private LocalDateTime recordedAt = LocalDateTime.now();
}
