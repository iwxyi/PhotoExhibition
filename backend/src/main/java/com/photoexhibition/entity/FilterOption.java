package com.photoexhibition.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;
import java.time.LocalDateTime;

import java.time.LocalDateTime;

@Entity
@Table(name = "filter_option")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "option_type", nullable = false)
    private String optionType; // camera_models, lens_models, focal_length_range, etc.

    @Column(name = "option_key", nullable = false)
    private String optionKey; // 具体的值，如相机型号名称，或 range_min/range_max

    @Column(name = "option_value")
    private String optionValue; // 对应的值

    @Column(name = "numeric_value")
    private Double numericValue; // 数值类型的值，用于范围查询

    @Column(name = "photo_count")
    private Integer photoCount; // 该选项对应的照片数量

    @Column(name = "created_at", nullable = false)
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
