package com.photoexhibition.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiSearchAnalysisScope {
    private String type;
    private List<String> cameraModels;
    private List<String> lensModels;
    private List<String> scopeKeywords;
}
