package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.dto.AiSearchIntent;
import com.photoexhibition.dto.AiSearchResponse;
import com.photoexhibition.dto.AiSearchSuggestionAction;
import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.PersonSummaryDTO;
import com.photoexhibition.dto.PhotoDTO;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AiSearchAnalysisResponseAssembler {

    public AiSearchResponse assembleResolved(String queryMode,
                                             AiSearchIntent intent,
                                             String explanation,
                                             List<PhotoDTO> photos,
                                             long totalElements,
                                             List<AlbumDTO> albums,
                                             List<PersonSummaryDTO> persons,
                                             boolean relaxed,
                                             String relaxedReason,
                                             String answer,
                                             Map<String, Object> executionPlan,
                                             List<AiSearchSuggestionAction> suggestionActions) {
        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(queryMode);
        response.setUsedAi(false);
        response.setNeedAnswer(true);
        response.setParsedIntent(intent);
        response.setExplanation(explanation);
        response.setPhotos(photos == null ? Collections.emptyList() : photos);
        response.setTotalElements(totalElements);
        response.setAlbums(albums == null ? Collections.emptyList() : albums);
        response.setPersons(persons == null ? Collections.emptyList() : persons);
        response.setRelaxed(relaxed);
        response.setRelaxedReason(relaxedReason);
        response.setAnswer(answer);
        response.setExecutionPlan(executionPlan);
        List<AiSearchSuggestionAction> safeSuggestionActions =
            suggestionActions == null ? Collections.emptyList() : suggestionActions;
        response.setSuggestionActions(safeSuggestionActions);
        response.setSuggestions(safeSuggestionActions.stream()
            .map(AiSearchSuggestionAction::getLabel)
            .collect(Collectors.toList()));
        return response;
    }

    public AiSearchResponse assembleEmpty(String queryMode,
                                          AiSearchIntent intent,
                                          String explanation,
                                          String answer) {
        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(queryMode);
        response.setUsedAi(false);
        response.setNeedAnswer(true);
        response.setParsedIntent(intent);
        response.setExplanation(explanation);
        response.setPhotos(Collections.emptyList());
        response.setTotalElements(0L);
        response.setAlbums(Collections.emptyList());
        response.setPersons(Collections.emptyList());
        response.setAnswer(answer);
        response.setSuggestionActions(Collections.emptyList());
        response.setSuggestions(Collections.emptyList());
        return response;
    }
}
