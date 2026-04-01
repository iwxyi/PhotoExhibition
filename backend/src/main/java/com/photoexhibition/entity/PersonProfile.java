package com.photoexhibition.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "person_profile")
@Data
@ToString(exclude = "faces")
@EqualsAndHashCode(exclude = "faces")
public class PersonProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "user_id")
    private Long userId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sample_photo_id")
    private Long samplePhotoId;

    @Column(name = "sample_face_id")
    private Long sampleFaceId;

    @Column(name = "sample_thumbnail_path", length = 1000)
    private String sampleThumbnailPath;

    @Column(name = "sample_original_path", length = 1000)
    private String sampleOriginalPath;

    @Column(name = "sample_confidence")
    private Double sampleConfidence;

    @Column(nullable = false)
    private Boolean hidden = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "person", fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Face> faces;

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
