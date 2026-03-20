package com.photoexhibition.dto;

import lombok.Data;

import java.util.List;

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
}
