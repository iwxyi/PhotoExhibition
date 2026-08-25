package com.photoexhibition.aisearch.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiSearchPersonGrowthAggregate {
    private Long personId;
    private String personName;
    private Integer matchedPhotoCount;
    private LocalDateTime matchedLastSeen;
    private String trend;
    private Double changePercent;
    private String firstPeriod;
    private String lastPeriod;
    private Double firstRatio;
    private Double lastRatio;
}
