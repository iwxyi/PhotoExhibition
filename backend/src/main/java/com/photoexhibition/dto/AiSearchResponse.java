package com.photoexhibition.dto;

import lombok.Data;
import java.util.List;

@Data
public class AiSearchResponse {
    private String explanation;
    private AiSearchIntent parsedIntent;
    private List<PhotoDTO> photos;
    private long totalElements;
    private String matchedPersonName;
    private List<String> matchedTagNames;
    private List<String> matchedAlbumNames;
    private boolean aiSearchEnabled;

    // 新增：混合结果类型
    private List<AlbumDTO> albums;
    private List<PersonSummaryDTO> persons;
}
