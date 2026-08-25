package com.photoexhibition.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class AiSearchResponse {
    private String queryMode;
    private boolean usedAi;
    private String explanation;
    private String answer;
    private boolean needAnswer;
    private boolean relaxed;
    private String relaxedReason;
    private List<String> suggestions;
    private List<AiSearchSuggestionAction> suggestionActions;
    private AiSearchIntent parsedIntent;
    private List<PhotoDTO> photos;
    private long totalElements;
    private String matchedPersonName;
    private List<String> matchedTagNames;
    private List<String> matchedAlbumNames;
    private boolean aiSearchEnabled;
    private List<AlbumDTO> albums;
    private List<PersonSummaryDTO> persons;
    private boolean cached; // 是否来自缓存
    private Map<String, Object> analysisData; // 包含体型变化等分析数据
    private Map<String, Object> executionPlan; // V2 计划摘要，供渐进迁移与调试使用
}
