package com.photoexhibition.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiSearchCondition {
    private String type;
    private List<Long> ids;
    private List<String> values;
    private String value;
    private Double minValue;
    private Double maxValue;
    private String startDate;
    private String endDate;
}
