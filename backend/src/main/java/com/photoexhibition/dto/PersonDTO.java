package com.photoexhibition.dto;

import lombok.Data;

@Data
public class PersonDTO {
    private Long id;
    private String name;
    private String description;
    private Long samplePhotoId;
    private Long sampleFaceId;
    private String sampleThumbnailPath;
    private String sampleOriginalPath;
    private Double sampleConfidence;
}

