package com.photoexhibition.aisearch.orchestration;

import com.photoexhibition.dto.AiSearchIntent;
import com.photoexhibition.dto.AiSearchResponse;
import com.photoexhibition.dto.AiSearchSuggestionAction;
import com.photoexhibition.dto.AlbumDTO;
import com.photoexhibition.dto.PersonSummaryDTO;
import com.photoexhibition.dto.PhotoDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSearchAnalysisResponseAssemblerTest {

    private final AiSearchAnalysisResponseAssembler assembler = new AiSearchAnalysisResponseAssembler();

    @Test
    void shouldAssembleResolvedAnalysisResponse() {
        AiSearchSuggestionAction action = new AiSearchSuggestionAction();
        action.setLabel("继续搜索");
        PhotoDTO photo = new PhotoDTO();
        photo.setId(1L);
        AlbumDTO album = new AlbumDTO();
        album.setId(2L);
        PersonSummaryDTO person = new PersonSummaryDTO();
        person.setId(3L);

        AiSearchResponse response = assembler.assembleResolved(
            "analysis",
            new AiSearchIntent(),
            "说明",
            List.of(photo),
            9L,
            List.of(album),
            List.of(person),
            true,
            "relaxed",
            "结论",
            Map.of("planType", "person_overview"),
            List.of(action)
        );

        assertEquals("analysis", response.getQueryMode());
        assertEquals("说明", response.getExplanation());
        assertEquals(9L, response.getTotalElements());
        assertEquals(1, response.getPhotos().size());
        assertEquals(1, response.getAlbums().size());
        assertEquals(1, response.getPersons().size());
        assertEquals("结论", response.getAnswer());
        assertEquals("继续搜索", response.getSuggestions().get(0));
    }

    @Test
    void shouldAssembleEmptyAnalysisResponse() {
        AiSearchResponse response = assembler.assembleEmpty("analysis", null, "未识别", null);

        assertEquals("analysis", response.getQueryMode());
        assertTrue(response.getPhotos().isEmpty());
        assertTrue(response.getAlbums().isEmpty());
        assertTrue(response.getPersons().isEmpty());
        assertTrue(response.getSuggestions().isEmpty());
    }
}
