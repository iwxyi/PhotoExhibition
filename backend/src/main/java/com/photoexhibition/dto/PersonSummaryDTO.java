package com.photoexhibition.dto;

import lombok.Data;

@Data
public class PersonSummaryDTO {
    private Long id;
    private String name;
    private String description;
    private Long samplePhotoId;
    private Long sampleFaceId;
    private String sampleThumbnailPath;
    private String sampleOriginalPath;
    private Double sampleConfidence;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;
}

