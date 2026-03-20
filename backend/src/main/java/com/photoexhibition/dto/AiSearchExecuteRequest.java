package com.photoexhibition.dto;

import lombok.Data;

@Data
public class AiSearchExecuteRequest {
    private String query;
    private AiSearchIntent intent;
    private AiSearchSuggestionAction suggestionAction;
    private Integer page;
    private Integer size;
}
