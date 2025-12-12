package com.photoexhibition.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "photo_face")
@Data
@ToString(exclude = {"photo", "person"})
@EqualsAndHashCode(exclude = {"photo", "person"})
public class Face {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", nullable = false)
    private Photo photo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private PersonProfile person;

    @Column(name = "x", precision = 6, scale = 3)
    private Double x; // 左上角X（0-1）

    @Column(name = "y", precision = 6, scale = 3)
    private Double y; // 左上角Y（0-1）

    @Column(name = "width", precision = 6, scale = 3)
    private Double width; // 宽度（0-1）

    @Column(name = "height", precision = 6, scale = 3)
    private Double height; // 高度（0-1）

    @Column(name = "confidence", precision = 5, scale = 2)
    private Double confidence; // 置信度0-1

    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embedding; // 人脸特征向量(JSON存储)

    @Column(name = "is_confirmed", nullable = false)
    private Boolean isConfirmed = false; // 是否已确认（用户手动确认的）

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

