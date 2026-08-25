package com.photoexhibition.aisearch.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiSearchPersonPairAggregate {
    private Long personAId;
    private String personAName;
    private Long personBId;
    private String personBName;
    private Integer matchedPhotoCount;
    private LocalDateTime matchedFirstSeen;
    private LocalDateTime matchedLastSeen;
}
