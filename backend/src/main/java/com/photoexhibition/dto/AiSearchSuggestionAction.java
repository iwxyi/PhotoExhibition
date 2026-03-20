package com.photoexhibition.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiSearchSuggestionAction {
    private String label;
    private String actionType;
    private List<String> conditionTypes;
}
