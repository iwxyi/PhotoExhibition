package com.photoexhibition.aisearch.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiSearchPersonAggregate {
    private Long personId;
    private String personName;
    private Integer matchedPhotoCount;
    private LocalDateTime matchedFirstSeen;
    private LocalDateTime matchedLastSeen;
    private LocalDateTime globalFirstSeen;
    private LocalDateTime globalLastSeen;
}
