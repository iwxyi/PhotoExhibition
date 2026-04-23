package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.aisearch.compatibility.LegacyIntentAiSearchPlanner;
import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.executor.AiSearchPlanExecutor;
import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonPairAggregate;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.planner.AlbumOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.BodyChangeAiSearchPlanner;
import com.photoexhibition.aisearch.planner.CountOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.DayOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.LocationOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.MonthOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.PersonCooccurrenceAiSearchPlanner;
import com.photoexhibition.aisearch.planner.PersonOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.PersonPairCooccurrenceAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsAiSearchPlanner;
import com.photoexhibition.aisearch.planner.TagOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.TechnicalDisjunctionAiSearchPlanner;
import com.photoexhibition.aisearch.planner.ThemeOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.YearCompareAiSearchPlanner;
import com.photoexhibition.aisearch.reducer.DefaultAiSearchEvidenceReducer;
import com.photoexhibition.aisearch.resolver.DefaultAiSearchResolver;
import com.photoexhibition.dto.AiSearchCondition;
import com.photoexhibition.dto.AiSearchResponse;
import com.photoexhibition.dto.PersonSummaryDTO;
import com.photoexhibition.dto.PhotoDTO;
import com.photoexhibition.entity.PersonProfile;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.FaceRepository;
import com.photoexhibition.repository.PersonProfileRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSearchServiceTest {

    @Mock private SystemConfigService systemConfigService;
    @Mock private PersonProfileRepository personProfileRepository;
    @Mock private TagRepository tagRepository;
    @Mock private AlbumRepository albumRepository;
    @Mock private FaceRepository faceRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private PhotoService photoService;
    @Mock private AlbumService albumService;
    @Mock private FaceService faceService;
    @Mock private UserPathService userPathService;
    @Mock private AiSearchPlanExecutor aiSearchPlanExecutor;

    private AiSearchService aiSearchService;

    private PersonProfile xiaoMing;
    private PersonProfile xiaoHong;
    private PersonProfile xiaoLi;
    private Photo hangzhouPhotoA;
    private Photo hangzhouPhotoB;

    @BeforeEach
    void setUp() {
        aiSearchService = new AiSearchService(
            systemConfigService,
            personProfileRepository,
            tagRepository,
            albumRepository,
            faceRepository,
            photoRepository,
            photoService,
            albumService,
            faceService,
            userPathService,
            new LegacyIntentAiSearchPlanner(),
            new RelativeNewPersonsAiSearchPlanner(),
            new TechnicalDisjunctionAiSearchPlanner(),
            new CountOverviewAiSearchPlanner(),
            new DayOverviewAiSearchPlanner(),
            new MonthOverviewAiSearchPlanner(),
            new PersonCooccurrenceAiSearchPlanner(),
            new PersonOverviewAiSearchPlanner(),
            new PersonPairCooccurrenceAiSearchPlanner(),
            new AlbumOverviewAiSearchPlanner(),
            new TagOverviewAiSearchPlanner(),
            new LocationOverviewAiSearchPlanner(),
            new ThemeOverviewAiSearchPlanner(),
            new YearCompareAiSearchPlanner(),
            new BodyChangeAiSearchPlanner(),
            aiSearchPlanExecutor,
            new DefaultAiSearchEvidenceReducer(),
            new DefaultAiSearchResolver(),
            new ObjectMapper()
        );

        xiaoMing = person(1L, "小明");
        xiaoHong = person(2L, "小红");
        xiaoLi = person(3L, "小李");

        hangzhouPhotoA = photo(101L, "hangzhou-a.jpg", LocalDateTime.of(2025, 3, 12, 10, 0));
        hangzhouPhotoB = photo(102L, "hangzhou-b.jpg", LocalDateTime.of(2025, 5, 9, 18, 30));

        lenient().when(systemConfigService.getAiSearchApiUrl()).thenReturn(null);
        lenient().when(systemConfigService.getAiSearchApiKey()).thenReturn(null);
        lenient().when(photoRepository.findDistinctCameraModels()).thenReturn(Collections.emptyList());
        lenient().when(photoRepository.findDistinctLensModels()).thenReturn(Collections.emptyList());
        lenient().when(tagRepository.searchByNameContaining(anyString())).thenReturn(Collections.emptyList());
        lenient().when(albumRepository.searchByName(anyString())).thenReturn(Collections.emptyList());
        lenient().when(albumRepository.searchByPath(anyString())).thenReturn(Collections.emptyList());
        lenient().when(photoRepository.findByExifFilters(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(PageRequest.class)
        )).thenReturn(new PageImpl<>(List.of(hangzhouPhotoA, hangzhouPhotoB)));
        lenient().when(photoRepository.findByPersonId(anyLong(), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(hangzhouPhotoA, hangzhouPhotoB)));
        lenient().when(photoRepository.searchByFilename("杭州")).thenReturn(List.of(hangzhouPhotoA, hangzhouPhotoB));
        lenient().when(photoRepository.findAllIdsNotHidden()).thenReturn(List.of(101L, 102L));
        lenient().when(photoRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(hangzhouPhotoA, hangzhouPhotoB));
        lenient().when(personProfileRepository.searchByNameList(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0, String.class);
            if (token != null && token.contains("小明")) {
                return List.of(xiaoMing);
            }
            return Collections.emptyList();
        });
        lenient().when(personProfileRepository.findById(1L)).thenReturn(Optional.of(xiaoMing));
        lenient().when(personProfileRepository.findById(2L)).thenReturn(Optional.of(xiaoHong));
        lenient().when(personProfileRepository.findById(3L)).thenReturn(Optional.of(xiaoLi));
        lenient().when(faceService.toSummaryDTO(any(PersonProfile.class))).thenAnswer(invocation -> {
            PersonProfile person = invocation.getArgument(0, PersonProfile.class);
            PersonSummaryDTO dto = new PersonSummaryDTO();
            dto.setId(person.getId());
            dto.setName(person.getName());
            return dto;
        });
        lenient().when(photoService.convertToDTO(any(Photo.class))).thenAnswer(invocation -> {
            Photo photo = invocation.getArgument(0, Photo.class);
            PhotoDTO dto = new PhotoDTO();
            dto.setId(photo.getId());
            dto.setFilename(photo.getFilename());
            dto.setTakenAt(photo.getTakenAt());
            return dto;
        });
        lenient().when(aiSearchPlanExecutor.execute(any(AiSearchPlan.class), any(AiSearchExecutionContext.class)))
            .thenAnswer(invocation -> buildExecutionResult(invocation.getArgument(0, AiSearchPlan.class)));
    }

    @Test
    void shouldRoutePersonOverviewQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年有谁", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("person_overview", response.getExecutionPlan().get("planType"));
        assertEquals("person_overview", response.getAnalysisData().get("analysisType"));
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("共找到 2 位符合条件的人物"));
    }

    @Test
    void shouldRouteAnchoredCooccurrenceQueriesToPersonCooccurrencePlan() {
        AiSearchResponse response = aiSearchService.search("小明经常一起出现的是谁", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("person_cooccurrence", response.getExecutionPlan().get("planType"));
        assertEquals("person_cooccurrence", response.getAnalysisData().get("analysisType"));
        assertEquals("小明", response.getAnalysisData().get("anchorPersonName"));
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("与小明共同出现频率较高的人物"));
    }

    @Test
    void shouldSupportGlobalPairCooccurrenceWithoutExplicitFilters() {
        AiSearchResponse response = aiSearchService.search("谁和谁最常同框", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("person_pair_cooccurrence", response.getExecutionPlan().get("planType"));
        assertEquals("person_pair_cooccurrence", response.getAnalysisData().get("analysisType"));
        assertFalse(response.getPhotos().isEmpty());
        assertTrue(response.getAnswer().contains("共同出现频率较高的人物组合"));

        List<AiSearchCondition> must = response.getParsedIntent().getMust();
        assertNotNull(must);
        assertTrue(must.stream().anyMatch(condition -> "match_all".equals(condition.getType())));
    }

    @Test
    void shouldKeepPairCooccurrenceRoutingForTimeAndLocationScopedQueries() {
        AiSearchResponse response = aiSearchService.search("去年在杭州谁和谁最常同框", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("person_pair_cooccurrence", response.getExecutionPlan().get("planType"));
        assertEquals("person_pair_cooccurrence", response.getAnalysisData().get("analysisType"));
        assertEquals("2025-01-01", response.getParsedIntent().getStartDate());
        assertEquals("2025-12-31", response.getParsedIntent().getEndDate());
        assertEquals(List.of("杭州"), response.getParsedIntent().getKeywords());
    }

    private AiSearchExecutionResult buildExecutionResult(AiSearchPlan plan) {
        AiSearchExecutionResult result = new AiSearchExecutionResult();
        if ("person_overview".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_persons", List.of(
                aggregate(1L, "小明", 12),
                aggregate(2L, "小红", 7)
            ));
            result.getFinalOutputs().put("limited_persons", List.of(
                aggregate(1L, "小明", 12),
                aggregate(2L, "小红", 7)
            ));
            return result;
        }
        if ("person_cooccurrence".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_cooccurring_persons", List.of(
                aggregate(2L, "小红", 11),
                aggregate(3L, "小李", 6)
            ));
            result.getFinalOutputs().put("limited_cooccurring_persons", List.of(
                aggregate(2L, "小红", 11),
                aggregate(3L, "小李", 6)
            ));
            return result;
        }
        if ("person_pair_cooccurrence".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_cooccurring_pairs", List.of(
                pairAggregate(1L, "小明", 2L, "小红", 14),
                pairAggregate(2L, "小红", 3L, "小李", 8)
            ));
            result.getFinalOutputs().put("limited_cooccurring_pairs", List.of(
                pairAggregate(1L, "小明", 2L, "小红", 14),
                pairAggregate(2L, "小红", 3L, "小李", 8)
            ));
        }
        return result;
    }

    private PersonProfile person(Long id, String name) {
        PersonProfile person = new PersonProfile();
        person.setId(id);
        person.setName(name);
        person.setHidden(false);
        return person;
    }

    private Photo photo(Long id, String filename, LocalDateTime takenAt) {
        Photo photo = new Photo();
        photo.setId(id);
        photo.setFilename(filename);
        photo.setTakenAt(takenAt);
        photo.setIsHidden(false);
        return photo;
    }

    private AiSearchPersonAggregate aggregate(Long personId, String personName, int matchedPhotoCount) {
        AiSearchPersonAggregate aggregate = new AiSearchPersonAggregate();
        aggregate.setPersonId(personId);
        aggregate.setPersonName(personName);
        aggregate.setMatchedPhotoCount(matchedPhotoCount);
        aggregate.setMatchedLastSeen(LocalDateTime.of(2025, 6, 1, 0, 0));
        return aggregate;
    }

    private AiSearchPersonPairAggregate pairAggregate(Long personAId,
                                                      String personAName,
                                                      Long personBId,
                                                      String personBName,
                                                      int matchedPhotoCount) {
        AiSearchPersonPairAggregate aggregate = new AiSearchPersonPairAggregate();
        aggregate.setPersonAId(personAId);
        aggregate.setPersonAName(personAName);
        aggregate.setPersonBId(personBId);
        aggregate.setPersonBName(personBName);
        aggregate.setMatchedPhotoCount(matchedPhotoCount);
        aggregate.setMatchedLastSeen(LocalDateTime.of(2025, 6, 1, 0, 0));
        return aggregate;
    }
}
