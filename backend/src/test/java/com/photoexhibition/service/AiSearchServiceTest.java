package com.photoexhibition.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.aisearch.compatibility.LegacyIntentAiSearchPlanner;
import com.photoexhibition.aisearch.executor.AiSearchExecutionContext;
import com.photoexhibition.aisearch.executor.AiSearchExecutionResult;
import com.photoexhibition.aisearch.executor.AiSearchPlanExecutor;
import com.photoexhibition.aisearch.model.AiSearchPersonAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonGrowthAggregate;
import com.photoexhibition.aisearch.model.AiSearchPersonPairAggregate;
import com.photoexhibition.aisearch.plan.AiSearchPlan;
import com.photoexhibition.aisearch.planner.AlbumOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.AiSearchAnalysisSpecMapper;
import com.photoexhibition.aisearch.planner.BodyChangeAiSearchPlanner;
import com.photoexhibition.aisearch.planner.CountOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.DayOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.LocationOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.MonthOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.PersonCooccurrenceAiSearchPlanner;
import com.photoexhibition.aisearch.planner.PersonOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.PersonPairCooccurrenceAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsBodyChangeAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsStillActiveAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsThenCooccurrenceMissingAgainAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsThenPairCooccurrenceAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsThenMultiCooccurrenceMissingAgainAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsThenMultiCooccurrenceAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsThenCooccurrenceAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsWithScopedPhotosAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsWithScopedPhotosThenActivityAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsWithScopedPhotosThenPairCooccurrenceAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsWithScopedPhotosStillActiveAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsWithTechnicalScopeThenActivityAiSearchPlanner;
import com.photoexhibition.aisearch.planner.RelativeNewPersonsWithTechnicalScopeAiSearchPlanner;
import com.photoexhibition.aisearch.planner.TagOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.TechnicalDisjunctionAiSearchPlanner;
import com.photoexhibition.aisearch.planner.TemporalPersonSetAiSearchPlanner;
import com.photoexhibition.aisearch.planner.ThemeOverviewAiSearchPlanner;
import com.photoexhibition.aisearch.planner.YearCompareAiSearchPlanner;
import com.photoexhibition.aisearch.reducer.DefaultAiSearchEvidenceReducer;
import com.photoexhibition.aisearch.resolver.DefaultAiSearchResolver;
import com.photoexhibition.aisearch.validation.AiSearchAnalysisFallbackIntentFactory;
import com.photoexhibition.aisearch.validation.AiSearchAnalysisFallbackSpecBuilder;
import com.photoexhibition.aisearch.validation.AiSearchAnalysisSpecNormalizer;
import com.photoexhibition.dto.AiSearchAnalysisOperation;
import com.photoexhibition.dto.AiSearchAnalysisScope;
import com.photoexhibition.dto.AiSearchAnalysisSpec;
import com.photoexhibition.dto.AiSearchAnalysisSubject;
import com.photoexhibition.dto.AiSearchCondition;
import com.photoexhibition.dto.AiSearchIntent;
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

import java.lang.reflect.Method;
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
    private PersonProfile xiaoWang;
    private Photo hangzhouPhotoA;
    private Photo hangzhouPhotoB;

    @BeforeEach
    void setUp() {
        RelativeNewPersonsAiSearchPlanner relativeNewPersonsAiSearchPlanner = new RelativeNewPersonsAiSearchPlanner();
        RelativeNewPersonsStillActiveAiSearchPlanner relativeNewPersonsStillActiveAiSearchPlanner = new RelativeNewPersonsStillActiveAiSearchPlanner();
        RelativeNewPersonsBodyChangeAiSearchPlanner relativeNewPersonsBodyChangeAiSearchPlanner = new RelativeNewPersonsBodyChangeAiSearchPlanner();
        RelativeNewPersonsThenCooccurrenceMissingAgainAiSearchPlanner relativeNewPersonsThenCooccurrenceMissingAgainAiSearchPlanner =
            new RelativeNewPersonsThenCooccurrenceMissingAgainAiSearchPlanner();
        RelativeNewPersonsThenPairCooccurrenceAiSearchPlanner relativeNewPersonsThenPairCooccurrenceAiSearchPlanner =
            new RelativeNewPersonsThenPairCooccurrenceAiSearchPlanner();
        RelativeNewPersonsThenMultiCooccurrenceMissingAgainAiSearchPlanner relativeNewPersonsThenMultiCooccurrenceMissingAgainAiSearchPlanner =
            new RelativeNewPersonsThenMultiCooccurrenceMissingAgainAiSearchPlanner();
        RelativeNewPersonsThenMultiCooccurrenceAiSearchPlanner relativeNewPersonsThenMultiCooccurrenceAiSearchPlanner =
            new RelativeNewPersonsThenMultiCooccurrenceAiSearchPlanner();
        RelativeNewPersonsThenCooccurrenceAiSearchPlanner relativeNewPersonsThenCooccurrenceAiSearchPlanner =
            new RelativeNewPersonsThenCooccurrenceAiSearchPlanner();
        RelativeNewPersonsWithScopedPhotosAiSearchPlanner relativeNewPersonsWithScopedPhotosAiSearchPlanner =
            new RelativeNewPersonsWithScopedPhotosAiSearchPlanner();
        RelativeNewPersonsWithScopedPhotosThenActivityAiSearchPlanner relativeNewPersonsWithScopedPhotosThenActivityAiSearchPlanner =
            new RelativeNewPersonsWithScopedPhotosThenActivityAiSearchPlanner(relativeNewPersonsWithScopedPhotosAiSearchPlanner);
        RelativeNewPersonsWithScopedPhotosThenPairCooccurrenceAiSearchPlanner relativeNewPersonsWithScopedPhotosThenPairCooccurrenceAiSearchPlanner =
            new RelativeNewPersonsWithScopedPhotosThenPairCooccurrenceAiSearchPlanner(relativeNewPersonsWithScopedPhotosAiSearchPlanner);
        RelativeNewPersonsWithScopedPhotosStillActiveAiSearchPlanner relativeNewPersonsWithScopedPhotosStillActiveAiSearchPlanner =
            new RelativeNewPersonsWithScopedPhotosStillActiveAiSearchPlanner(relativeNewPersonsWithScopedPhotosAiSearchPlanner);
        RelativeNewPersonsWithTechnicalScopeAiSearchPlanner relativeNewPersonsWithTechnicalScopeAiSearchPlanner =
            new RelativeNewPersonsWithTechnicalScopeAiSearchPlanner();
        RelativeNewPersonsWithTechnicalScopeThenActivityAiSearchPlanner relativeNewPersonsWithTechnicalScopeThenActivityAiSearchPlanner =
            new RelativeNewPersonsWithTechnicalScopeThenActivityAiSearchPlanner(relativeNewPersonsWithTechnicalScopeAiSearchPlanner);
        TemporalPersonSetAiSearchPlanner temporalPersonSetAiSearchPlanner = new TemporalPersonSetAiSearchPlanner();

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
            relativeNewPersonsAiSearchPlanner,
            relativeNewPersonsStillActiveAiSearchPlanner,
            relativeNewPersonsBodyChangeAiSearchPlanner,
            relativeNewPersonsThenCooccurrenceMissingAgainAiSearchPlanner,
            relativeNewPersonsThenPairCooccurrenceAiSearchPlanner,
            relativeNewPersonsThenMultiCooccurrenceMissingAgainAiSearchPlanner,
            relativeNewPersonsThenMultiCooccurrenceAiSearchPlanner,
            relativeNewPersonsThenCooccurrenceAiSearchPlanner,
            relativeNewPersonsWithScopedPhotosAiSearchPlanner,
            relativeNewPersonsWithScopedPhotosThenActivityAiSearchPlanner,
            relativeNewPersonsWithScopedPhotosThenPairCooccurrenceAiSearchPlanner,
            relativeNewPersonsWithScopedPhotosStillActiveAiSearchPlanner,
            relativeNewPersonsWithTechnicalScopeThenActivityAiSearchPlanner,
            relativeNewPersonsWithTechnicalScopeAiSearchPlanner,
            temporalPersonSetAiSearchPlanner,
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
            new AiSearchAnalysisSpecMapper(
                relativeNewPersonsAiSearchPlanner,
                relativeNewPersonsStillActiveAiSearchPlanner,
                relativeNewPersonsBodyChangeAiSearchPlanner,
                relativeNewPersonsThenCooccurrenceAiSearchPlanner,
                relativeNewPersonsThenCooccurrenceMissingAgainAiSearchPlanner,
                relativeNewPersonsThenMultiCooccurrenceAiSearchPlanner,
                relativeNewPersonsThenMultiCooccurrenceMissingAgainAiSearchPlanner,
                relativeNewPersonsThenPairCooccurrenceAiSearchPlanner,
                relativeNewPersonsWithScopedPhotosAiSearchPlanner,
                relativeNewPersonsWithScopedPhotosThenActivityAiSearchPlanner,
                relativeNewPersonsWithScopedPhotosStillActiveAiSearchPlanner,
                relativeNewPersonsWithTechnicalScopeAiSearchPlanner,
                relativeNewPersonsWithTechnicalScopeThenActivityAiSearchPlanner,
                temporalPersonSetAiSearchPlanner,
                new CountOverviewAiSearchPlanner(),
                new PersonOverviewAiSearchPlanner(),
                new PersonCooccurrenceAiSearchPlanner(),
                new PersonPairCooccurrenceAiSearchPlanner(),
                new AlbumOverviewAiSearchPlanner(),
                new MonthOverviewAiSearchPlanner(),
                new LocationOverviewAiSearchPlanner(),
                new DayOverviewAiSearchPlanner(),
                new TagOverviewAiSearchPlanner(),
                new ThemeOverviewAiSearchPlanner(),
                new YearCompareAiSearchPlanner()
            ),
            aiSearchPlanExecutor,
            new DefaultAiSearchEvidenceReducer(),
            new DefaultAiSearchResolver(),
            new AiSearchAnalysisFallbackIntentFactory(),
            new AiSearchAnalysisFallbackSpecBuilder(),
            new AiSearchAnalysisSpecNormalizer(personProfileRepository),
            new ObjectMapper()
        );

        xiaoMing = person(1L, "小明");
        xiaoHong = person(2L, "小红");
        xiaoLi = person(3L, "小李");
        xiaoWang = person(4L, "小王");

        hangzhouPhotoA = photo(101L, "hangzhou-a.jpg", LocalDateTime.of(2025, 3, 12, 10, 0));
        hangzhouPhotoB = photo(102L, "hangzhou-b.jpg", LocalDateTime.of(2025, 5, 9, 18, 30));

        lenient().when(systemConfigService.getAiSearchApiUrl()).thenReturn(null);
        lenient().when(systemConfigService.getAiSearchApiKey()).thenReturn(null);
        lenient().when(photoRepository.findDistinctCameraModels()).thenReturn(List.of("Canon EOS R6m2", "Canon EOS R8"));
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
            if (token != null && token.contains("小红")) {
                return List.of(xiaoHong);
            }
            if (token != null && token.contains("小李")) {
                return List.of(xiaoLi);
            }
            if (token != null && token.contains("小王")) {
                return List.of(xiaoWang);
            }
            return Collections.emptyList();
        });
        lenient().when(personProfileRepository.findById(1L)).thenReturn(Optional.of(xiaoMing));
        lenient().when(personProfileRepository.findById(2L)).thenReturn(Optional.of(xiaoHong));
        lenient().when(personProfileRepository.findById(3L)).thenReturn(Optional.of(xiaoLi));
        lenient().when(personProfileRepository.findById(4L)).thenReturn(Optional.of(xiaoWang));
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
        assertNotNull(response.getParsedIntent().getAnalysisSpec());
        assertEquals("person_overview", response.getParsedIntent().getAnalysisSpec().getOperation().getType());
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("共找到 2 位符合条件的人物"));
    }

    @Test
    void shouldRouteThemeOverviewQueriesThroughStructuredFallbackPlan() {
        AiSearchResponse response = aiSearchService.search("去年主要拍了什么主题", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals("theme_overview", response.getExecutionPlan().get("planType"));
        assertNotNull(response.getParsedIntent().getAnalysisSpec());
        assertEquals("theme_overview", response.getParsedIntent().getAnalysisSpec().getOperation().getType());
        assertFalse(response.getPhotos().isEmpty());
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
        assertNotNull(response.getParsedIntent().getAnalysisSpec());
        assertEquals("person_pair_cooccurrence", response.getParsedIntent().getAnalysisSpec().getOperation().getType());
        assertEquals("2025-01-01", response.getParsedIntent().getStartDate());
        assertEquals("2025-12-31", response.getParsedIntent().getEndDate());
        assertEquals(List.of("杭州"), response.getParsedIntent().getKeywords());
    }

    @Test
    void shouldSupportTemporalPersonSetQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("前年不存在但去年存在，今年又没再出现的人有谁", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("temporal_person_set", response.getExecutionPlan().get("planType"));
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("没再出现"));
    }

    @Test
    void shouldSupportBroaderRelativeYearTemporalPersonSetQueries() {
        AiSearchResponse response = aiSearchService.search("大前年不存在但前年存在，去年又没再出现的人有谁", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals("temporal_person_set", response.getExecutionPlan().get("planType"));
        assertEquals(2L, response.getTotalElements());
        assertTrue(response.getAnswer().contains("没再出现"));
    }

    @Test
    void shouldSupportRelativeNewPersonsWithTechnicalScopeQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人物里，有哪些是用佳能拍到的", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_with_technical_scope", response.getExecutionPlan().get("planType"));
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("Canon EOS R6m2"));
    }

    @Test
    void shouldSupportRelativeNewPersonsWithTechnicalScopeThenActivityQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人物里，用佳能拍到且后续出现次数最多的是谁", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_with_technical_scope_then_activity", response.getExecutionPlan().get("planType"));
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("后续持续出现较活跃"));
    }

    @Test
    void shouldSupportRelativeNewPersonsThenCooccurrenceQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人里，谁后来又经常和小明同框", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_then_cooccurrence", response.getExecutionPlan().get("planType"));
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("小明同框"));
    }

    @Test
    void shouldSupportRelativeNewPersonsStillActiveQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人里，今年还经常出现的有谁", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_still_active", response.getExecutionPlan().get("planType"));
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("仍持续出现"));
    }

    @Test
    void shouldSupportRelativeNewPersonsStillActiveQueriesWithBroaderRelativeYears() {
        AiSearchResponse response = aiSearchService.search("前年新认识的人里，去年还经常出现的有谁", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals("relative_new_persons_still_active", response.getExecutionPlan().get("planType"));
        assertEquals(2L, response.getTotalElements());
        assertTrue(response.getAnswer().contains("仍持续出现"));
    }

    @Test
    void shouldSupportRelativeNewPersonsBodyChangeQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人中，哪些人今年比去年更胖", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_body_change", response.getExecutionPlan().get("planType"));
        assertEquals("relative_new_persons_body_change", response.getAnalysisData().get("analysisType"));
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("明显变胖"));
    }

    @Test
    void shouldSupportRelativeNewPersonsBodyChangeQueriesWithBroaderRelativeYears() {
        AiSearchResponse response = aiSearchService.search("前年新认识的人中，哪些人去年比前年更胖", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals("relative_new_persons_body_change", response.getExecutionPlan().get("planType"));
        assertEquals(2L, response.getTotalElements());
        assertTrue(response.getAnswer().contains("明显变胖"));
    }

    @Test
    void shouldSupportRelativeNewPersonsWithScopedPhotosQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人里，有哪些是在杭州用佳能拍到的", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_with_scoped_photos", response.getExecutionPlan().get("planType"));
        assertEquals(List.of("杭州"), response.getParsedIntent().getKeywords());
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("杭州"));
    }

    @Test
    void shouldSupportRelativeNewPersonsWithScopedPhotosThenActivityQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人里，在杭州用佳能拍到且后续出现次数最多的是谁", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_with_scoped_photos_then_activity", response.getExecutionPlan().get("planType"));
        assertEquals(List.of("杭州"), response.getParsedIntent().getKeywords());
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("后续持续出现较活跃"));
        assertTrue(response.getAnswer().contains("杭州"));
    }

    @Test
    void shouldSupportRelativeNewPersonsWithScopedPhotosThenPairCooccurrenceQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人里，在杭州用佳能拍到的人中，谁和谁后来最常同框", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_with_scoped_photos_then_pair_cooccurrence", response.getExecutionPlan().get("planType"));
        assertEquals("relative_new_persons_with_scoped_photos_then_pair_cooccurrence", response.getAnalysisData().get("analysisType"));
        assertTrue(response.getAnswer().contains("杭州"));
        assertTrue(response.getAnswer().contains("共同出现的人物组合"));
    }

    @Test
    void shouldSupportRelativeNewPersonsWithScopedPhotosStillActiveQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人里，有哪些是在杭州用佳能拍到、且今年还经常出现的", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_with_scoped_photos_still_active", response.getExecutionPlan().get("planType"));
        assertEquals(List.of("杭州"), response.getParsedIntent().getKeywords());
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("仍持续出现"));
    }

    @Test
    void shouldSupportRelativeNewPersonsThenCooccurrenceMissingAgainQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人里，后来最常和小明一起出现、但今年没再出现的有谁", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_then_cooccurrence_missing_again", response.getExecutionPlan().get("planType"));
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("没再出现"));
    }

    @Test
    void shouldSupportRelativeNewPersonsThenMultiCooccurrenceQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人里，后来最常和小明、小红同框的是谁", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_then_multi_cooccurrence", response.getExecutionPlan().get("planType"));
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("小明、小红"));
    }

    @Test
    void shouldSupportRelativeNewPersonsThenMultiCooccurrenceMissingAgainQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人里，后来最常和小明、小红同框、但今年没再出现的有谁", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_then_multi_cooccurrence_missing_again", response.getExecutionPlan().get("planType"));
        assertEquals(2, response.getPersons().size());
        assertTrue(response.getAnswer().contains("小明、小红"));
        assertTrue(response.getAnswer().contains("没再出现"));
    }

    @Test
    void shouldSupportRelativeNewPersonsThenPairCooccurrenceQueriesThroughControlledPlan() {
        AiSearchResponse response = aiSearchService.search("去年新认识的人物里，谁和谁后来最常同框", 0, 10);

        assertEquals("analysis", response.getQueryMode());
        assertEquals(2L, response.getTotalElements());
        assertEquals("relative_new_persons_then_pair_cooccurrence", response.getExecutionPlan().get("planType"));
        assertEquals("relative_new_persons_then_pair_cooccurrence", response.getAnalysisData().get("analysisType"));
        assertTrue(response.getAnswer().contains("共同出现的人物组合"));
    }

    @Test
    void shouldNormalizeFilteredScopeCooccurrenceAnalysisSpecFromIntentContext() throws Exception {
        AiSearchIntent intent = new AiSearchIntent();
        intent.setPersonIds(List.of(1L, 1L));
        intent.setResultTypes(List.of("photos"));

        AiSearchAnalysisOperation operation = new AiSearchAnalysisOperation();
        operation.setType(" person_cooccurrence ");

        AiSearchAnalysisSpec spec = new AiSearchAnalysisSpec();
        spec.setOperation(operation);
        intent.setAnalysisSpec(spec);

        invokeNormalizeIntent("小明经常一起出现的是谁", intent, false);

        assertNotNull(intent.getAnalysisSpec().getSubject());
        assertNotNull(intent.getAnalysisSpec().getScope());
        assertEquals("persons", intent.getAnalysisSpec().getSubjectType());
        assertEquals("filtered_scope", intent.getAnalysisSpec().getSubject().getType());
        assertEquals("none", intent.getAnalysisSpec().getScope().getType());
        assertEquals("person_cooccurrence", intent.getAnalysisSpec().getOperation().getType());
        assertEquals(List.of(1L), intent.getAnalysisSpec().getOperation().getAnchorPersonIds());
        assertEquals(List.of("小明"), intent.getAnalysisSpec().getOperation().getAnchorPersonNames());
        assertTrue(intent.getResultTypes().contains("photos"));
        assertTrue(intent.getResultTypes().contains("albums"));
        assertTrue(intent.getResultTypes().contains("persons"));
        assertTrue(intent.getNeedAnswer());
    }

    @Test
    void shouldNormalizeFilteredScopeScopeListsAndYearCompareSubject() throws Exception {
        AiSearchIntent intent = new AiSearchIntent();
        intent.setKeywords(List.of(" 杭州 ", "杭州", " 春天 "));
        intent.setResultTypes(Collections.emptyList());

        AiSearchAnalysisOperation operation = new AiSearchAnalysisOperation();
        operation.setType(" YEAR_COMPARE ");
        operation.setSubject("   ");

        AiSearchAnalysisScope scope = new AiSearchAnalysisScope();
        scope.setType(" FILTERED_SCOPE ");
        scope.setCameraModels(List.of(" Canon EOS R6m2 ", "Canon EOS R6m2"));
        scope.setLensModels(List.of(" RF24-70 ", "RF24-70"));
        scope.setScopeKeywords(List.of(" 杭州 ", "杭州"));

        AiSearchAnalysisSubject subject = new AiSearchAnalysisSubject();

        AiSearchAnalysisSpec spec = new AiSearchAnalysisSpec();
        spec.setSubject(subject);
        spec.setScope(scope);
        spec.setOperation(operation);
        intent.setAnalysisSpec(spec);

        invokeNormalizeIntent("去年和前年在杭州春天拍了多少张", intent, false);

        assertEquals("persons", intent.getAnalysisSpec().getSubjectType());
        assertEquals("filtered_scope", intent.getAnalysisSpec().getSubject().getType());
        assertEquals("filtered_scope", intent.getAnalysisSpec().getScope().getType());
        assertEquals(List.of("Canon EOS R6m2"), intent.getAnalysisSpec().getScope().getCameraModels());
        assertEquals(List.of("RF24-70"), intent.getAnalysisSpec().getScope().getLensModels());
        assertEquals(List.of("杭州"), intent.getAnalysisSpec().getScope().getScopeKeywords());
        assertEquals("year_compare", intent.getAnalysisSpec().getOperation().getType());
        assertEquals("杭州 春天", intent.getAnalysisSpec().getOperation().getSubject());
        assertTrue(intent.getResultTypes().contains("photos"));
        assertTrue(intent.getResultTypes().contains("albums"));
        assertTrue(intent.getNeedAnswer());
    }

    private void invokeNormalizeIntent(String query, AiSearchIntent intent, boolean applyRelativeYearRange) throws Exception {
        Method method = AiSearchService.class.getDeclaredMethod(
            "normalizeIntent",
            String.class,
            AiSearchIntent.class,
            boolean.class
        );
        method.setAccessible(true);
        method.invoke(aiSearchService, query, intent, applyRelativeYearRange);
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
            return result;
        }
        if ("temporal_person_set".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_temporal_person_set", List.of(
                aggregate(1L, "小明", 12),
                aggregate(2L, "小红", 7)
            ));
            result.getFinalOutputs().put("limited_temporal_person_set", List.of(
                aggregate(1L, "小明", 12),
                aggregate(2L, "小红", 7)
            ));
            return result;
        }
        if ("relative_new_persons_with_technical_scope".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_filtered_new_persons", List.of(
                aggregate(1L, "小明", 12),
                aggregate(2L, "小红", 7)
            ));
            result.getFinalOutputs().put("limited_filtered_new_persons", List.of(
                aggregate(1L, "小明", 12),
                aggregate(2L, "小红", 7)
            ));
            return result;
        }
        if ("relative_new_persons_with_technical_scope_then_activity".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_followup_active_filtered_new_persons", List.of(
                aggregate(1L, "小明", 12),
                aggregate(2L, "小红", 7)
            ));
            result.getFinalOutputs().put("limited_followup_active_filtered_new_persons", List.of(
                aggregate(1L, "小明", 12),
                aggregate(2L, "小红", 7)
            ));
            return result;
        }
        if ("relative_new_persons_then_cooccurrence".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_relative_new_persons_cooccurrence", List.of(
                aggregate(2L, "小红", 9),
                aggregate(3L, "小李", 5)
            ));
            result.getFinalOutputs().put("limited_relative_new_persons_cooccurrence", List.of(
                aggregate(2L, "小红", 9),
                aggregate(3L, "小李", 5)
            ));
            return result;
        }
        if ("relative_new_persons_still_active".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_still_active_new_persons", List.of(
                aggregate(2L, "小红", 10),
                aggregate(3L, "小李", 4)
            ));
            result.getFinalOutputs().put("limited_still_active_new_persons", List.of(
                aggregate(2L, "小红", 10),
                aggregate(3L, "小李", 4)
            ));
            return result;
        }
        if ("relative_new_persons_body_change".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_body_change_new_persons", List.of(
                growthAggregate(2L, "小红", 8, 9.2D),
                growthAggregate(3L, "小李", 6, 5.4D)
            ));
            result.getFinalOutputs().put("limited_body_change_new_persons", List.of(
                growthAggregate(2L, "小红", 8, 9.2D),
                growthAggregate(3L, "小李", 6, 5.4D)
            ));
            return result;
        }
        if ("relative_new_persons_with_scoped_photos".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_scoped_new_persons", List.of(
                aggregate(2L, "小红", 6),
                aggregate(3L, "小李", 3)
            ));
            result.getFinalOutputs().put("limited_scoped_new_persons", List.of(
                aggregate(2L, "小红", 6),
                aggregate(3L, "小李", 3)
            ));
            return result;
        }
        if ("relative_new_persons_with_scoped_photos_then_activity".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_followup_active_scoped_new_persons", List.of(
                aggregate(2L, "小红", 8),
                aggregate(3L, "小李", 5)
            ));
            result.getFinalOutputs().put("limited_followup_active_scoped_new_persons", List.of(
                aggregate(2L, "小红", 8),
                aggregate(3L, "小李", 5)
            ));
            return result;
        }
        if ("relative_new_persons_with_scoped_photos_then_pair_cooccurrence".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_relative_scoped_new_person_pairs", List.of(
                pairAggregate(2L, "小红", 3L, "小李", 6),
                pairAggregate(3L, "小李", 4L, "小王", 4)
            ));
            result.getFinalOutputs().put("limited_relative_scoped_new_person_pairs", List.of(
                pairAggregate(2L, "小红", 3L, "小李", 6),
                pairAggregate(3L, "小李", 4L, "小王", 4)
            ));
            return result;
        }
        if ("relative_new_persons_with_scoped_photos_still_active".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_still_active_scoped_new_persons", List.of(
                aggregate(2L, "小红", 8),
                aggregate(3L, "小李", 4)
            ));
            result.getFinalOutputs().put("limited_still_active_scoped_new_persons", List.of(
                aggregate(2L, "小红", 8),
                aggregate(3L, "小李", 4)
            ));
            return result;
        }
        if ("relative_new_persons_then_cooccurrence_missing_again".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_cooccurring_new_persons_missing_again", List.of(
                aggregate(2L, "小红", 7),
                aggregate(3L, "小李", 4)
            ));
            result.getFinalOutputs().put("limited_cooccurring_new_persons_missing_again", List.of(
                aggregate(2L, "小红", 7),
                aggregate(3L, "小李", 4)
            ));
            return result;
        }
        if ("relative_new_persons_then_multi_cooccurrence".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_relative_new_persons_multi_cooccurrence", List.of(
                aggregate(3L, "小李", 6),
                aggregate(2L, "小红", 4)
            ));
            result.getFinalOutputs().put("limited_relative_new_persons_multi_cooccurrence", List.of(
                aggregate(3L, "小李", 6),
                aggregate(2L, "小红", 4)
            ));
            return result;
        }
        if ("relative_new_persons_then_multi_cooccurrence_missing_again".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_multi_cooccurring_new_persons_missing_again", List.of(
                aggregate(3L, "小李", 6),
                aggregate(4L, "小王", 3)
            ));
            result.getFinalOutputs().put("limited_multi_cooccurring_new_persons_missing_again", List.of(
                aggregate(3L, "小李", 6),
                aggregate(4L, "小王", 3)
            ));
            return result;
        }
        if ("relative_new_persons_then_pair_cooccurrence".equals(plan.getPlanType())) {
            result.getFinalOutputs().put("sorted_relative_new_person_pairs", List.of(
                pairAggregate(2L, "小红", 3L, "小李", 6),
                pairAggregate(3L, "小李", 4L, "小王", 4)
            ));
            result.getFinalOutputs().put("limited_relative_new_person_pairs", List.of(
                pairAggregate(2L, "小红", 3L, "小李", 6),
                pairAggregate(3L, "小李", 4L, "小王", 4)
            ));
            return result;
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

    private AiSearchPersonGrowthAggregate growthAggregate(Long personId,
                                                          String personName,
                                                          int matchedPhotoCount,
                                                          double changePercent) {
        AiSearchPersonGrowthAggregate aggregate = new AiSearchPersonGrowthAggregate();
        aggregate.setPersonId(personId);
        aggregate.setPersonName(personName);
        aggregate.setMatchedPhotoCount(matchedPhotoCount);
        aggregate.setMatchedLastSeen(LocalDateTime.of(2026, 6, 1, 0, 0));
        aggregate.setTrend("gained_weight");
        aggregate.setChangePercent(changePercent);
        aggregate.setFirstPeriod("2025-03");
        aggregate.setLastPeriod("2026-03");
        aggregate.setFirstRatio(1.200D);
        aggregate.setLastRatio(1.310D);
        return aggregate;
    }
}
