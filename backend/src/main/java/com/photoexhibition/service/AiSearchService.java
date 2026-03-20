package com.photoexhibition.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.photoexhibition.dto.*;
import com.photoexhibition.entity.Album;
import com.photoexhibition.entity.PersonProfile;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.Tag;
import com.photoexhibition.repository.AlbumRepository;
import com.photoexhibition.repository.PersonProfileRepository;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiSearchService {

    private final SystemConfigService systemConfigService;
    private final PersonProfileRepository personProfileRepository;
    private final TagRepository tagRepository;
    private final AlbumRepository albumRepository;
    private final PhotoRepository photoRepository;
    private final PhotoService photoService;
    private final AlbumService albumService;
    private final FaceService faceService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_QUERY_FETCH = 10000;
    private static final Set<String> STOP_WORDS = Set.of(
        "的", "在", "了", "是", "和", "与", "或", "我", "要", "找", "有",
        "把", "从", "到", "都", "也", "就", "而", "但", "又", "为",
        "被", "给", "让", "向", "用", "对", "这", "那", "中", "上",
        "下", "里", "个", "些", "吗", "呢", "吧", "啊", "哦",
        "一", "不", "人", "大", "小", "多", "少", "很", "最",
        "所", "以", "可", "能", "会", "时", "候", "地", "得",
        "着", "过", "去", "来", "看", "想", "搜", "搜索", "查找",
        "照片", "图片", "相片", "拍", "拍摄", "拍照"
    );
    private static final Set<String> ANALYSIS_CUES = Set.of(
        "经常一起", "一起出现", "同框", "关系", "最常", "最多", "排名", "排行",
        "统计", "分析", "占比", "比例", "对比", "比较", "谁最", "哪里拍过",
        "在哪里拍过", "多少张", "多少次", "哪个相册", "哪些相册", "哪个月", "几月",
        "哪几天", "哪些天", "哪个标签", "哪些标签", "上一年", "上年", "地方", "地点",
        "更多还是更少", "相比"
    );
    private static final Set<String> QUESTION_CUES = Set.of(
        "怎么样", "如何", "好不好", "是否", "有没有", "吗", "呢", "多少", "几张", "怎么看"
    );
    private static final Set<String> THEME_ANALYSIS_CUES = Set.of(
        "主题", "题材", "拍的什么", "拍了什么", "什么比较多", "什么最多"
    );
    private static final Set<String> LOCATION_ANALYSIS_CUES = Set.of(
        "哪里拍过", "在哪里拍过", "哪里拍的", "在哪里拍的", "哪儿拍过", "在哪拍过"
    );
    private static final Set<String> THEME_STOP_WORDS = Set.of(
        "照片", "图片", "相片", "相册", "合集", "记录", "主题", "题材", "拍摄", "拍的", "拍了",
        "去年", "前年", "今年", "什么", "比较多", "最多", "很多", "一下", "一下子", "内容",
        "人像", "风景", "宠物", "城市风光", "自然风光", "建筑", "夜景", "节日活动"
    );
    private static final Set<String> TECHNICAL_THEME_STOP_WORDS = Set.of(
        "高分辨率", "低分辨率", "竖图", "横图", "方图", "大光圈", "小光圈",
        "高iso", "低iso", "高ISO", "低ISO", "广角", "长焦", "虚化", "夜景",
        "明亮", "通透", "清新", "氛围", "质感"
    );
    private static final Set<String> LOCATION_STOP_WORDS = Set.of(
        "人像", "风景", "宠物", "写真", "团片", "日常", "生活", "合集", "记录",
        "主题", "题材", "樱花", "夜樱", "花海", "语嫣", "小明", "小红"
    );
    private static final Set<String> LOCATION_HINT_SUFFIXES = Set.of(
        "园", "山", "湖", "江", "河", "海", "湾", "岛", "桥", "街", "路", "村",
        "镇", "城", "馆", "寺", "塔", "站", "场", "公园", "植物园", "景区", "校园"
    );
    private static final Set<String> COUNT_ANALYSIS_CUES = Set.of(
        "多少张", "多少次", "几张", "几次"
    );
    private static final Set<String> ALBUM_ANALYSIS_CUES = Set.of(
        "哪个相册", "哪些相册"
    );
    private static final Set<String> MONTH_ANALYSIS_CUES = Set.of(
        "哪个月", "几月", "什么时候"
    );
    private static final Set<String> LOCATION_GENERAL_CUES = Set.of(
        "哪里", "哪儿", "地点", "地方", "在哪"
    );
    private static final Set<String> ANALYSIS_RANK_CUES = Set.of(
        "最多", "比较多", "最常", "最频繁", "高频", "主要", "集中", "排行", "排名"
    );
    private static final Set<String> DAY_ANALYSIS_CUES = Set.of(
        "哪几天", "哪些天", "哪天", "哪一天", "日期", "几号", "哪几日", "集中在哪几天", "集中在哪天"
    );
    private static final Set<String> TAG_ANALYSIS_CUES = Set.of(
        "标签", "tag", "tags"
    );
    private static final Set<String> YEAR_COMPARE_ANALYSIS_CUES = Set.of(
        "相比", "对比", "更多还是更少", "更少还是更多", "更多还是少", "同比"
    );

    public AiSearchResponse search(String query, int page, int size) {
        log.info("AI搜索开始, query={}, page={}, size={}", query, page, size);

        try {
            String normalizedQuery = normalizeSemanticQuery(query);
            String queryMode = classifyQueryMode(normalizedQuery);
            Set<String> tokens = generateTokens(normalizedQuery);
            log.info("分词结果: {}", tokens);

            CandidateContext candidates = preRetrieve(tokens);
            log.info("预检索候选: persons={}, tags={}, albums={}",
                candidates.persons.size(), candidates.tags.size(), candidates.albums.size());

            AnalysisRouting analysisRouting = resolveAnalysisRouting(normalizedQuery, candidates, queryMode);
            if ("analysis".equals(queryMode) && analysisRouting.isResolved()) {
                return buildAnalysisResponse(analysisRouting, candidates, page, size, queryMode);
            }

            boolean useDirectIntent = shouldUseDirectIntent(normalizedQuery, queryMode);
            AiSearchIntent intent = useDirectIntent
                ? buildDirectIntent(normalizedQuery, candidates, tokens)
                : callGpt(normalizedQuery, candidates, queryMode);
            normalizeIntent(normalizedQuery, intent, true);
            log.info("AI解析结果: mode={}, intent={}", queryMode, intent);
            return buildSearchResponse(query, intent, candidates, page, size, queryMode, !useDirectIntent);
        } catch (Exception e) {
            log.error("AI搜索失败: {}", e.getMessage(), e);
            AiSearchResponse response = new AiSearchResponse();
            response.setAiSearchEnabled(true);
            response.setQueryMode(classifyQueryMode(normalizeSemanticQuery(query)));
            response.setUsedAi(false);
            response.setNeedAnswer(false);
            response.setExplanation("AI搜索出错: " + e.getMessage());
            response.setPhotos(Collections.emptyList());
            response.setAlbums(Collections.emptyList());
            response.setPersons(Collections.emptyList());
            response.setSuggestions(Collections.emptyList());
            response.setSuggestionActions(Collections.emptyList());
            return response;
        }
    }

    public AiSearchResponse searchWithSuggestion(String query,
                                                 AiSearchIntent intent,
                                                 AiSearchSuggestionAction suggestionAction,
                                                 int page,
                                                 int size) {
        log.info("执行AI搜索建议, query={}, action={}", query, suggestionAction == null ? null : suggestionAction.getLabel());
        String normalizedQuery = normalizeSemanticQuery(query);
        String queryMode = classifyQueryMode(normalizedQuery);

        if (intent == null) {
            return search(query, page, size);
        }

        try {
            Set<String> tokens = generateTokens(normalizedQuery);
            CandidateContext candidates = preRetrieve(tokens);
            AiSearchIntent adjustedIntent = suggestionAction == null
                ? cloneIntent(intent)
                : applySuggestionAction(intent, suggestionAction);
            normalizeIntent(normalizedQuery, adjustedIntent, false);
            return buildSearchResponse(query, adjustedIntent, candidates, page, size, queryMode, false);
        } catch (Exception e) {
            log.error("执行AI搜索建议失败: {}", e.getMessage(), e);
            AiSearchResponse response = new AiSearchResponse();
            response.setAiSearchEnabled(true);
            response.setQueryMode(queryMode);
            response.setUsedAi(false);
            response.setNeedAnswer(false);
            response.setExplanation("AI搜索出错: " + e.getMessage());
            response.setPhotos(Collections.emptyList());
            response.setAlbums(Collections.emptyList());
            response.setPersons(Collections.emptyList());
            response.setSuggestions(Collections.emptyList());
            response.setSuggestionActions(Collections.emptyList());
            return response;
        }
    }

    private AiSearchResponse buildSearchResponse(String query,
                                                 AiSearchIntent intent,
                                                 CandidateContext candidates,
                                                 int page,
                                                 int size,
                                                 String queryMode,
                                                 boolean usedAi) {
        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(queryMode);
        response.setUsedAi(usedAi);
        response.setNeedAnswer(false);
        response.setParsedIntent(intent);
        response.setExplanation(intent.getExplanation());
        fillMatchedNames(response, intent, candidates);

        List<String> resultTypes = normalizeResultTypes(intent);
        PhotoSearchExecution photoSearch = resultTypes.contains("photos")
            ? executePhotoQuery(intent, page, size)
            : PhotoSearchExecution.empty();
        if (resultTypes.contains("photos") && photoSearch.totalMatched == 0) {
            photoSearch = tryRelaxedPhotoQuery(intent, page, size);
        }

        response.setPhotos(photoSearch.pagedPhotoDtos);
        response.setTotalElements(photoSearch.totalMatched);
        response.setRelaxed(photoSearch.relaxed);
        response.setRelaxedReason(photoSearch.relaxedReason);

        response.setAlbums(resultTypes.contains("albums")
            ? fetchAlbumResults(intent)
            : Collections.emptyList());
        response.setPersons(resultTypes.contains("persons")
            ? fetchPersonResults(intent)
            : Collections.emptyList());

        List<AiSearchSuggestionAction> suggestionActions =
            buildSuggestionActions(intent, photoSearch, response.getAlbums(), response.getPersons());
        response.setSuggestionActions(suggestionActions);
        response.setSuggestions(suggestionActions.stream()
            .map(AiSearchSuggestionAction::getLabel)
            .collect(Collectors.toList()));

        if (Boolean.TRUE.equals(intent.getNeedAnswer())) {
            String answer = generateAnswer(query, intent, photoSearch, response.getAlbums(), response.getPersons());
            if (answer != null && !answer.isBlank()) {
                response.setNeedAnswer(true);
                response.setAnswer(answer.trim());
            }
        }

        return response;
    }

    private List<AlbumDTO> fetchAlbumResults(AiSearchIntent intent) {
        LinkedHashSet<Long> albumIds = filterAlbumIdsForDisplay(intent, collectPositiveConditionIds(intent, "album"));
        if (albumIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<AlbumDTO> results = new ArrayList<>();
        for (Long albumId : albumIds) {
            try {
                AlbumDTO dto = albumService.getAlbumById(albumId);
                if (dto != null) {
                    results.add(dto);
                }
            } catch (Exception e) {
                log.debug("获取相册失败, albumId={}: {}", albumId, e.getMessage());
            }
        }
        return results;
    }

    private List<PersonSummaryDTO> fetchPersonResults(AiSearchIntent intent) {
        LinkedHashSet<Long> personIds = collectPositiveConditionIds(intent, "person");
        if (personIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<PersonSummaryDTO> results = new ArrayList<>();
        for (Long personId : personIds) {
            try {
                Optional<PersonProfile> opt = personProfileRepository.findById(personId);
                if (opt.isPresent()) {
                    PersonProfile person = opt.get();
                    if (!intent.isIncludeHidden() && Boolean.TRUE.equals(person.getHidden())) {
                        continue;
                    }
                    results.add(faceService.toSummaryDTO(person));
                }
            } catch (Exception e) {
                log.debug("获取人物失败, personId={}: {}", personId, e.getMessage());
            }
        }
        return results;
    }

    Set<String> generateTokens(String query) {
        StringBuilder cleaned = new StringBuilder();
        for (char c : query.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c > 127) {
                cleaned.append(c);
            } else {
                cleaned.append(' ');
            }
        }

        String[] rawWords = cleaned.toString().split("\\s+");
        Set<String> tokens = new LinkedHashSet<>();

        for (String word : rawWords) {
            if (word.isEmpty()) {
                continue;
            }
            if (STOP_WORDS.contains(word)) {
                continue;
            }
            if (word.length() >= 2) {
                tokens.add(word);
            }
            if (word.length() >= 3) {
                for (int i = 0; i <= word.length() - 2; i++) {
                    String bi = word.substring(i, i + 2);
                    if (!STOP_WORDS.contains(bi)) {
                        tokens.add(bi);
                    }
                    if (i + 3 <= word.length()) {
                        String tri = word.substring(i, i + 3);
                        if (!STOP_WORDS.contains(tri)) {
                            tokens.add(tri);
                        }
                    }
                }
            }
        }

        return tokens;
    }

    private CandidateContext preRetrieve(Set<String> tokens) {
        Map<Long, PersonProfile> personMap = new LinkedHashMap<>();
        Map<Long, Tag> tagMap = new LinkedHashMap<>();
        Map<Long, Album> albumMap = new LinkedHashMap<>();

        for (String token : tokens) {
            if (token.length() < 2) {
                continue;
            }

            try {
                List<PersonProfile> persons = personProfileRepository.searchByNameList(token);
                for (PersonProfile p : persons) {
                    personMap.putIfAbsent(p.getId(), p);
                }
            } catch (Exception e) {
                log.debug("搜索人物失败, token={}: {}", token, e.getMessage());
            }

            try {
                List<Tag> tags = tagRepository.searchByNameContaining(token);
                for (Tag t : tags) {
                    tagMap.putIfAbsent(t.getId(), t);
                }
            } catch (Exception e) {
                log.debug("搜索标签失败, token={}: {}", token, e.getMessage());
            }

            try {
                List<Album> albums = albumRepository.searchByName(token);
                for (Album a : albums) {
                    albumMap.putIfAbsent(a.getId(), a);
                }
                List<Album> pathAlbums = albumRepository.searchByPath(token);
                for (Album a : pathAlbums) {
                    albumMap.putIfAbsent(a.getId(), a);
                }
            } catch (Exception e) {
                log.debug("搜索相册失败, token={}: {}", token, e.getMessage());
            }
        }

        List<String> cameraModels = photoRepository.findDistinctCameraModels();
        List<String> lensModels = photoRepository.findDistinctLensModels();

        return new CandidateContext(
            new ArrayList<>(personMap.values()),
            new ArrayList<>(tagMap.values()),
            new ArrayList<>(albumMap.values()),
            cameraModels,
            lensModels
        );
    }

    private String classifyQueryMode(String query) {
        String normalized = normalizeLooseText(normalizeSemanticQuery(query));
        if (normalized.isBlank()) {
            return "simple_search";
        }
        int localBestAnalysisScore = scoreAnalysisTypes(normalizeSemanticQuery(query)).values().stream()
            .max(Integer::compareTo)
            .orElse(0);
        if (containsCue(normalized, ANALYSIS_CUES) || localBestAnalysisScore >= 4) {
            return "analysis";
        }
        if (containsCue(normalized, QUESTION_CUES) || (query != null && (query.contains("?") || query.contains("？")))) {
            return "simple_answer";
        }
        return "simple_search";
    }

    private boolean shouldUseDirectIntent(String query, String queryMode) {
        if (!"simple_search".equals(queryMode)) {
            return false;
        }
        String subject = stripDirectQueryNoise(query);
        if (subject.isBlank() || subject.contains(" ")) {
            return false;
        }
        if (subject.length() > 12) {
            return false;
        }
        return !containsCue(subject, ANALYSIS_CUES) && !containsCue(subject, QUESTION_CUES);
    }

    private AiSearchIntent buildDirectIntent(String query, CandidateContext candidates, Set<String> tokens) {
        String subject = stripDirectQueryNoise(query);
        AiSearchIntent intent = new AiSearchIntent();
        intent.setPersonIds(new ArrayList<>());
        intent.setTagIds(new ArrayList<>());
        intent.setAlbumIds(new ArrayList<>());
        intent.setKeywords(new ArrayList<>());
        intent.setFilenameKeywords(new ArrayList<>());
        intent.setMust(new ArrayList<>());
        intent.setShould(new ArrayList<>());
        intent.setMustNot(new ArrayList<>());
        intent.setResultTypes(new ArrayList<>(List.of("photos")));
        intent.setIncludeHidden(false);
        intent.setNeedAnswer(false);
        intent.setExplanation("快速检索：" + subject);

        if (subject.isBlank()) {
            return intent;
        }

        List<Long> personIds = rankPersonMatches(candidates, subject, tokens, 5);
        if (!personIds.isEmpty()) {
            intent.setPersonIds(personIds);
            intent.getResultTypes().add("persons");
        }

        List<Long> tagIds = rankTagMatches(candidates, subject, tokens, 8);
        if (!tagIds.isEmpty()) {
            intent.setTagIds(tagIds);
        }

        List<Long> albumIds = rankAlbumMatches(candidates, subject, tokens, 5);
        if (!albumIds.isEmpty()) {
            intent.setAlbumIds(albumIds);
            intent.getResultTypes().add("albums");
        }

        List<String> directKeywords = extractDirectKeywords(subject, personIds, tagIds, albumIds);
        if (!directKeywords.isEmpty()) {
            intent.setKeywords(new ArrayList<>(directKeywords));
        }

        intent.setResultTypes(intent.getResultTypes().stream()
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new)));
        return intent;
    }

    private AiSearchResponse buildAnalysisResponse(AnalysisRouting routing,
                                                   CandidateContext candidates,
                                                   int page,
                                                   int size,
                                                   String queryMode) {
        String query = routing.resolvedQuery;
        AiSearchResponse response;
        switch (routing.type) {
            case "theme":
                response = buildThemeOverviewResponse(query, candidates, page, size, queryMode);
                break;
            case "location":
                response = buildLocationOverviewResponse(query, candidates, page, size, queryMode);
                break;
            case "album":
                response = buildAlbumOverviewResponse(query, candidates, page, size, queryMode);
                break;
            case "month":
                response = buildMonthOverviewResponse(query, candidates, page, size, queryMode);
                break;
            case "count":
                response = buildCountOverviewResponse(query, candidates, page, size, queryMode);
                break;
            case "day":
                response = buildDayOverviewResponse(query, candidates, page, size, queryMode);
                break;
            case "tag":
                response = buildTagOverviewResponse(query, candidates, page, size, queryMode);
                break;
            case "year_compare":
                response = buildYearCompareResponse(query, candidates, page, size, queryMode);
                break;
            default:
                throw new IllegalArgumentException("不支持的分析类型: " + routing.type);
        }
        response.setUsedAi(routing.usedAi);
        return response;
    }

    private AnalysisRouting resolveAnalysisRouting(String query, CandidateContext candidates, String queryMode) {
        if (!"analysis".equals(queryMode) || query == null || query.isBlank()) {
            return AnalysisRouting.none();
        }

        Map<String, Integer> scores = scoreAnalysisTypes(query);
        int bestLocalScore = scores.values().stream().max(Integer::compareTo).orElse(0);

        AnalysisRouting aiRouting = tryResolveAnalysisRoutingWithAi(query, candidates, bestLocalScore);
        if (aiRouting.isResolved()) {
            return aiRouting;
        }

        String bestType = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestType = entry.getKey();
            }
        }

        if (bestType == null || bestScore < 4) {
            return AnalysisRouting.none();
        }
        return new AnalysisRouting(bestType, query, aiRouting.usedAi);
    }

    private Map<String, Integer> scoreAnalysisTypes(String query) {
        String normalized = normalizeLooseText(query);
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("theme", 0);
        scores.put("location", 0);
        scores.put("album", 0);
        scores.put("month", 0);
        scores.put("count", 0);
        scores.put("day", 0);
        scores.put("tag", 0);
        scores.put("year_compare", 0);

        boolean hasDayCue = containsCue(normalized, DAY_ANALYSIS_CUES);
        boolean hasMonthCue = containsCue(normalized, MONTH_ANALYSIS_CUES) || normalized.contains("月份");

        if (containsCue(normalized, THEME_ANALYSIS_CUES)) {
            scores.computeIfPresent("theme", (key, value) -> value + 5);
        }
        if (containsCue(normalized, LOCATION_ANALYSIS_CUES)
            || (containsCue(normalized, LOCATION_GENERAL_CUES) && !hasDayCue && !hasMonthCue)) {
            scores.computeIfPresent("location", (key, value) -> value + 5);
        }
        if (containsCue(normalized, ALBUM_ANALYSIS_CUES) || normalized.contains("相册")) {
            scores.computeIfPresent("album", (key, value) -> value + 4);
        }
        if (hasMonthCue) {
            scores.computeIfPresent("month", (key, value) -> value + 4);
        }
        if (containsCue(normalized, COUNT_ANALYSIS_CUES) || normalized.contains("数量")) {
            scores.computeIfPresent("count", (key, value) -> value + 5);
        }
        if (hasDayCue) {
            scores.computeIfPresent("day", (key, value) -> value + 8);
        }
        if (containsCue(normalized, TAG_ANALYSIS_CUES)) {
            scores.computeIfPresent("tag", (key, value) -> value + 6);
        }
        if (containsCue(normalized, YEAR_COMPARE_ANALYSIS_CUES)) {
            scores.computeIfPresent("year_compare", (key, value) -> value + 6);
        }

        if (containsCue(normalized, ANALYSIS_RANK_CUES)) {
            if (containsCue(normalized, LOCATION_GENERAL_CUES) && !hasDayCue && !hasMonthCue) {
                scores.computeIfPresent("location", (key, value) -> value + 2);
            }
            if (normalized.contains("主题") || normalized.contains("题材") || normalized.contains("拍什么")) {
                scores.computeIfPresent("theme", (key, value) -> value + 2);
            }
            if (normalized.contains("相册")) {
                scores.computeIfPresent("album", (key, value) -> value + 2);
            }
            if (normalized.contains("月")) {
                scores.computeIfPresent("month", (key, value) -> value + 2);
            }
            if (normalized.contains("标签")) {
                scores.computeIfPresent("tag", (key, value) -> value + 2);
            }
        }

        if (normalized.contains("去年") && normalized.contains("前年")) {
            scores.computeIfPresent("year_compare", (key, value) -> value + 3);
        }
        if (normalized.contains("今年") && normalized.contains("去年")) {
            scores.computeIfPresent("year_compare", (key, value) -> value + 3);
        }

        return scores;
    }

    private AnalysisRouting tryResolveAnalysisRoutingWithAi(String query, CandidateContext candidates, int localBestScore) {
        String apiUrl = systemConfigService.getAiSearchApiUrl();
        String apiKey = systemConfigService.getAiSearchApiKey();
        String model = systemConfigService.getAiSearchModel();
        if (isBlank(apiUrl) || isBlank(apiKey)) {
            return AnalysisRouting.none();
        }

        try {
            String endpoint = getChatEndpoint(apiUrl);
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.1);
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("messages", List.of(
                Map.of(
                    "role", "system",
                    "content", buildAnalysisRoutingPrompt(candidates, localBestScore)
                ),
                Map.of("role", "user", "content", query)
            ));

            String responseBody = invokeChatCompletion(endpoint, apiKey, requestBody);
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return AnalysisRouting.none();
            }
            String content = choices.get(0).get("message").get("content").asText();
            JsonNode result = objectMapper.readTree(extractJsonBlock(content));
            String type = normalizeLooseText(result.path("analysisType").asText(""));
            if (type.isBlank() || "unknown".equals(type)) {
                return AnalysisRouting.none();
            }

            String mappedType = mapAiAnalysisType(type);
            if (mappedType.isBlank()) {
                return AnalysisRouting.aiAttempted();
            }

            double confidence = result.path("confidence").asDouble(0D);
            if (confidence < 0.55D) {
                return AnalysisRouting.aiAttempted();
            }

            LinkedHashSet<String> extraKeywords = new LinkedHashSet<>();
            JsonNode topicKeywords = result.get("topicKeywords");
            if (topicKeywords != null && topicKeywords.isArray()) {
                for (JsonNode item : topicKeywords) {
                    String keyword = normalizeSemanticQuery(item.asText(""));
                    if (!keyword.isBlank()) {
                        extraKeywords.add(keyword);
                    }
                }
            }

            String resolvedQuery = query;
            if (!extraKeywords.isEmpty()) {
                resolvedQuery = (query + " " + String.join(" ", extraKeywords)).trim();
            }
            return new AnalysisRouting(mappedType, resolvedQuery, true);
        } catch (Exception e) {
            log.warn("AI分析路由兜底失败: {}", e.getMessage());
            return AnalysisRouting.aiAttempted();
        }
    }

    private String buildAnalysisRoutingPrompt(CandidateContext candidates, int localBestScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是图库搜索的分析问题分类器。");
        sb.append("请把用户问题分类到以下类型之一：theme, location, album, month, count, day, tag, year_compare, unknown。\n");
        sb.append("规则：\n");
        sb.append("1. 只做类型分类和主题词抽取，不生成答案。\n");
        sb.append("2. theme=主题/题材分布；location=地点分布；album=相册排行；month=月份分布；count=数量统计；day=日期分布；tag=标签排行；year_compare=两个年份对比。\n");
        sb.append("3. 如果问题像“去年哪些地方最常拍夜樱”，应返回 location，topicKeywords 可返回 [\"夜樱\"]。\n");
        sb.append("4. 如果问题像“去年和前年相比樱花拍得更多还是更少”，应返回 year_compare，topicKeywords 可返回 [\"樱花\"]。\n");
        sb.append("5. 不确定时返回 unknown。\n");
        sb.append("6. 只返回 JSON：{\"analysisType\":\"...\",\"topicKeywords\":[],\"confidence\":0.0}\n");
        sb.append("当前本地路由最高分: ").append(localBestScore).append("\n");
        if (!candidates.tags.isEmpty()) {
            sb.append("候选标签示例: ");
            sb.append(candidates.tags.stream().limit(12).map(Tag::getName).collect(Collectors.joining("、")));
            sb.append("\n");
        }
        return sb.toString();
    }

    private String mapAiAnalysisType(String type) {
        switch (type) {
            case "theme":
            case "location":
            case "album":
            case "month":
            case "count":
            case "day":
            case "tag":
            case "yearcompare":
            case "year_compare":
                return "yearcompare".equals(type) ? "year_compare" : type;
            default:
                return "";
        }
    }

    private boolean shouldUseThemeOverviewAnalysis(String query, String queryMode) {
        if (!"analysis".equals(queryMode)) {
            return false;
        }
        String normalized = normalizeLooseText(query);
        return containsCue(normalized, THEME_ANALYSIS_CUES)
            && (normalized.contains("多") || normalized.contains("最多"));
    }

    private AiSearchResponse buildThemeOverviewResponse(String query,
                                                        CandidateContext candidates,
                                                        int page,
                                                        int size,
                                                        String queryMode) {
        AiSearchIntent intent = buildThemeOverviewIntent(query, candidates);
        normalizeIntent(query, intent, true);

        PhotoSearchExecution photoSearch = executePhotoQuery(intent, page, size);
        List<AlbumDTO> albums = fetchTopAlbumsForMatchedPhotos(photoSearch.allMatchedPhotos, 6);
        Map<String, Long> themeCounts = summarizeThemes(photoSearch.allMatchedPhotos, albums);

        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(queryMode);
        response.setUsedAi(false);
        response.setNeedAnswer(true);
        response.setParsedIntent(intent);
        response.setExplanation(intent.getExplanation());
        response.setPhotos(photoSearch.pagedPhotoDtos);
        response.setTotalElements(photoSearch.totalMatched);
        response.setAlbums(albums);
        response.setPersons(Collections.emptyList());
        response.setRelaxed(photoSearch.relaxed);
        response.setRelaxedReason(photoSearch.relaxedReason);
        response.setAnswer(buildThemeOverviewAnswer(intent, photoSearch, themeCounts));

        List<AiSearchSuggestionAction> suggestionActions =
            buildSuggestionActions(intent, photoSearch, albums, Collections.emptyList());
        response.setSuggestionActions(suggestionActions);
        response.setSuggestions(suggestionActions.stream()
            .map(AiSearchSuggestionAction::getLabel)
            .collect(Collectors.toList()));
        return response;
    }

    private AiSearchIntent buildThemeOverviewIntent(String query, CandidateContext candidates) {
        List<String> topicKeywords = extractAnalysisKeywords(query);
        AiSearchIntent intent = new AiSearchIntent();
        intent.setPersonIds(new ArrayList<>());
        intent.setTagIds(new ArrayList<>());
        intent.setAlbumIds(new ArrayList<>());
        intent.setKeywords(new ArrayList<>());
        intent.setFilenameKeywords(new ArrayList<>());
        intent.setMust(new ArrayList<>());
        intent.setShould(new ArrayList<>());
        intent.setMustNot(new ArrayList<>());
        intent.setResultTypes(new ArrayList<>(List.of("photos", "albums")));
        intent.setIncludeHidden(false);
        intent.setNeedAnswer(true);
        intent.setAnswerPrompt("概括拍摄主题的高频分布");
        intent.setExplanation("统计指定时间范围内拍摄较多的主题（基于相册名与标签）");
        applyAnalysisTopicKeywords(intent, candidates, topicKeywords);
        if (intent.getKeywords().isEmpty() && intent.getTagIds().isEmpty() && intent.getAlbumIds().isEmpty()) {
            intent.setExplanation("统计指定时间范围内拍摄较多的主题");
        }
        return intent;
    }

    private boolean shouldUseLocationOverviewAnalysis(String query, String queryMode) {
        if (!"analysis".equals(queryMode)) {
            return false;
        }
        String normalized = normalizeLooseText(query);
        return containsCue(normalized, LOCATION_ANALYSIS_CUES);
    }

    private AiSearchResponse buildLocationOverviewResponse(String query,
                                                           CandidateContext candidates,
                                                           int page,
                                                           int size,
                                                           String queryMode) {
        AiSearchIntent intent = buildLocationOverviewIntent(query, candidates);
        normalizeIntent(query, intent, true);

        PhotoSearchExecution photoSearch = executePhotoQuery(intent, page, size);
        List<AlbumDTO> albums = fetchTopAlbumsForMatchedPhotos(photoSearch.allMatchedPhotos, 6);
        Map<String, Long> locationCounts = summarizeLocations(photoSearch.allMatchedPhotos, albums);

        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(queryMode);
        response.setUsedAi(false);
        response.setNeedAnswer(true);
        response.setParsedIntent(intent);
        response.setExplanation(intent.getExplanation());
        response.setPhotos(photoSearch.pagedPhotoDtos);
        response.setTotalElements(photoSearch.totalMatched);
        response.setAlbums(albums);
        response.setPersons(Collections.emptyList());
        response.setRelaxed(photoSearch.relaxed);
        response.setRelaxedReason(photoSearch.relaxedReason);
        response.setAnswer(buildLocationOverviewAnswer(intent, photoSearch, locationCounts));

        List<AiSearchSuggestionAction> suggestionActions =
            buildSuggestionActions(intent, photoSearch, albums, Collections.emptyList());
        response.setSuggestionActions(suggestionActions);
        response.setSuggestions(suggestionActions.stream()
            .map(AiSearchSuggestionAction::getLabel)
            .collect(Collectors.toList()));
        return response;
    }

    private AiSearchIntent buildLocationOverviewIntent(String query, CandidateContext candidates) {
        List<String> topicKeywords = extractAnalysisKeywords(query);
        AiSearchIntent intent = new AiSearchIntent();
        intent.setPersonIds(new ArrayList<>());
        intent.setTagIds(new ArrayList<>());
        intent.setAlbumIds(new ArrayList<>());
        intent.setKeywords(new ArrayList<>());
        intent.setFilenameKeywords(new ArrayList<>());
        intent.setMust(new ArrayList<>());
        intent.setShould(new ArrayList<>());
        intent.setMustNot(new ArrayList<>());
        intent.setResultTypes(new ArrayList<>(List.of("photos", "albums")));
        intent.setIncludeHidden(false);
        intent.setNeedAnswer(true);
        intent.setAnswerPrompt("概括主要拍摄地点");
        intent.setExplanation("统计指定时间范围内相关主题主要拍摄于哪些地点（基于相册名与路径）");
        applyAnalysisTopicKeywords(intent, candidates, topicKeywords);
        return intent;
    }

    private boolean shouldUseAlbumOverviewAnalysis(String query, String queryMode) {
        if (!"analysis".equals(queryMode)) {
            return false;
        }
        String normalized = normalizeLooseText(query);
        return containsCue(normalized, ALBUM_ANALYSIS_CUES) && (normalized.contains("最多") || normalized.contains("比较多"));
    }

    private AiSearchResponse buildAlbumOverviewResponse(String query,
                                                        CandidateContext candidates,
                                                        int page,
                                                        int size,
                                                        String queryMode) {
        AiSearchIntent intent = buildAlbumOverviewIntent(query, candidates);
        normalizeIntent(query, intent, true);

        PhotoSearchExecution photoSearch = executePhotoQuery(intent, page, size);
        Map<Long, Long> albumCounts = summarizeAlbumCounts(photoSearch.allMatchedPhotos);
        List<AlbumDTO> albums = fetchAlbumsByCount(albumCounts, 6);

        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(queryMode);
        response.setUsedAi(false);
        response.setNeedAnswer(true);
        response.setParsedIntent(intent);
        response.setExplanation(intent.getExplanation());
        response.setPhotos(photoSearch.pagedPhotoDtos);
        response.setTotalElements(photoSearch.totalMatched);
        response.setAlbums(albums);
        response.setPersons(Collections.emptyList());
        response.setRelaxed(photoSearch.relaxed);
        response.setRelaxedReason(photoSearch.relaxedReason);
        response.setAnswer(buildAlbumOverviewAnswer(intent, photoSearch, albumCounts, albums));

        List<AiSearchSuggestionAction> suggestionActions =
            buildSuggestionActions(intent, photoSearch, albums, Collections.emptyList());
        response.setSuggestionActions(suggestionActions);
        response.setSuggestions(suggestionActions.stream()
            .map(AiSearchSuggestionAction::getLabel)
            .collect(Collectors.toList()));
        return response;
    }

    private AiSearchIntent buildAlbumOverviewIntent(String query, CandidateContext candidates) {
        List<String> topicKeywords = extractAnalysisKeywords(query);
        AiSearchIntent intent = new AiSearchIntent();
        intent.setPersonIds(new ArrayList<>());
        intent.setTagIds(new ArrayList<>());
        intent.setAlbumIds(new ArrayList<>());
        intent.setKeywords(new ArrayList<>());
        intent.setFilenameKeywords(new ArrayList<>());
        intent.setMust(new ArrayList<>());
        intent.setShould(new ArrayList<>());
        intent.setMustNot(new ArrayList<>());
        intent.setResultTypes(new ArrayList<>(List.of("photos", "albums")));
        intent.setIncludeHidden(false);
        intent.setNeedAnswer(true);
        intent.setAnswerPrompt("概括照片最多的相册");
        intent.setExplanation("统计指定时间范围内照片较多的相册");
        applyAnalysisTopicKeywords(intent, candidates, topicKeywords);
        return intent;
    }

    private boolean shouldUseMonthOverviewAnalysis(String query, String queryMode) {
        if (!"analysis".equals(queryMode)) {
            return false;
        }
        String normalized = normalizeLooseText(query);
        return containsCue(normalized, MONTH_ANALYSIS_CUES) && (normalized.contains("最多") || normalized.contains("比较多"));
    }

    private AiSearchResponse buildMonthOverviewResponse(String query,
                                                        CandidateContext candidates,
                                                        int page,
                                                        int size,
                                                        String queryMode) {
        AiSearchIntent intent = buildMonthOverviewIntent(query, candidates);
        normalizeIntent(query, intent, true);

        PhotoSearchExecution photoSearch = executePhotoQuery(intent, page, size);
        Map<String, Long> monthCounts = summarizeMonthCounts(photoSearch.allMatchedPhotos);
        List<AlbumDTO> albums = fetchTopAlbumsForMatchedPhotos(photoSearch.allMatchedPhotos, 6);

        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(queryMode);
        response.setUsedAi(false);
        response.setNeedAnswer(true);
        response.setParsedIntent(intent);
        response.setExplanation(intent.getExplanation());
        response.setPhotos(photoSearch.pagedPhotoDtos);
        response.setTotalElements(photoSearch.totalMatched);
        response.setAlbums(albums);
        response.setPersons(Collections.emptyList());
        response.setRelaxed(photoSearch.relaxed);
        response.setRelaxedReason(photoSearch.relaxedReason);
        response.setAnswer(buildMonthOverviewAnswer(intent, photoSearch, monthCounts));

        List<AiSearchSuggestionAction> suggestionActions =
            buildSuggestionActions(intent, photoSearch, albums, Collections.emptyList());
        response.setSuggestionActions(suggestionActions);
        response.setSuggestions(suggestionActions.stream()
            .map(AiSearchSuggestionAction::getLabel)
            .collect(Collectors.toList()));
        return response;
    }

    private AiSearchIntent buildMonthOverviewIntent(String query, CandidateContext candidates) {
        List<String> topicKeywords = extractAnalysisKeywords(query);
        AiSearchIntent intent = new AiSearchIntent();
        intent.setPersonIds(new ArrayList<>());
        intent.setTagIds(new ArrayList<>());
        intent.setAlbumIds(new ArrayList<>());
        intent.setKeywords(new ArrayList<>());
        intent.setFilenameKeywords(new ArrayList<>());
        intent.setMust(new ArrayList<>());
        intent.setShould(new ArrayList<>());
        intent.setMustNot(new ArrayList<>());
        intent.setResultTypes(new ArrayList<>(List.of("photos", "albums")));
        intent.setIncludeHidden(false);
        intent.setNeedAnswer(true);
        intent.setAnswerPrompt("概括拍摄高峰月份");
        intent.setExplanation("统计指定时间范围内拍摄较多的月份");
        applyAnalysisTopicKeywords(intent, candidates, topicKeywords);
        return intent;
    }

    private boolean shouldUseCountOverviewAnalysis(String query, String queryMode) {
        if (!"analysis".equals(queryMode)) {
            return false;
        }
        String normalized = normalizeLooseText(query);
        return containsCue(normalized, COUNT_ANALYSIS_CUES);
    }

    private AiSearchResponse buildCountOverviewResponse(String query,
                                                        CandidateContext candidates,
                                                        int page,
                                                        int size,
                                                        String queryMode) {
        AiSearchIntent intent = buildCountOverviewIntent(query, candidates);
        normalizeIntent(query, intent, true);

        PhotoSearchExecution photoSearch = executePhotoQuery(intent, page, size);
        Map<Long, Long> albumCounts = summarizeAlbumCounts(photoSearch.allMatchedPhotos);
        List<AlbumDTO> albums = fetchAlbumsByCount(albumCounts, 4);

        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(queryMode);
        response.setUsedAi(false);
        response.setNeedAnswer(true);
        response.setParsedIntent(intent);
        response.setExplanation(intent.getExplanation());
        response.setPhotos(photoSearch.pagedPhotoDtos);
        response.setTotalElements(photoSearch.totalMatched);
        response.setAlbums(albums);
        response.setPersons(Collections.emptyList());
        response.setRelaxed(photoSearch.relaxed);
        response.setRelaxedReason(photoSearch.relaxedReason);
        response.setAnswer(buildCountOverviewAnswer(intent, photoSearch, albumCounts));

        List<AiSearchSuggestionAction> suggestionActions =
            buildSuggestionActions(intent, photoSearch, albums, Collections.emptyList());
        response.setSuggestionActions(suggestionActions);
        response.setSuggestions(suggestionActions.stream()
            .map(AiSearchSuggestionAction::getLabel)
            .collect(Collectors.toList()));
        return response;
    }

    private AiSearchIntent buildCountOverviewIntent(String query, CandidateContext candidates) {
        List<String> topicKeywords = extractAnalysisKeywords(query);
        AiSearchIntent intent = new AiSearchIntent();
        intent.setPersonIds(new ArrayList<>());
        intent.setTagIds(new ArrayList<>());
        intent.setAlbumIds(new ArrayList<>());
        intent.setKeywords(new ArrayList<>());
        intent.setFilenameKeywords(new ArrayList<>());
        intent.setMust(new ArrayList<>());
        intent.setShould(new ArrayList<>());
        intent.setMustNot(new ArrayList<>());
        intent.setResultTypes(new ArrayList<>(List.of("photos", "albums")));
        intent.setIncludeHidden(false);
        intent.setNeedAnswer(true);
        intent.setAnswerPrompt("给出公开照片数量");
        intent.setExplanation("统计指定条件下公开照片数量");
        applyAnalysisTopicKeywords(intent, candidates, topicKeywords);
        return intent;
    }

    private AiSearchResponse buildDayOverviewResponse(String query,
                                                      CandidateContext candidates,
                                                      int page,
                                                      int size,
                                                      String queryMode) {
        AiSearchIntent intent = buildDayOverviewIntent(query, candidates);
        normalizeIntent(query, intent, true);

        PhotoSearchExecution photoSearch = executePhotoQuery(intent, page, size);
        Map<String, Long> dayCounts = summarizeDayCounts(photoSearch.allMatchedPhotos);
        List<AlbumDTO> albums = fetchTopAlbumsForMatchedPhotos(photoSearch.allMatchedPhotos, 6);

        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(queryMode);
        response.setUsedAi(false);
        response.setNeedAnswer(true);
        response.setParsedIntent(intent);
        response.setExplanation(intent.getExplanation());
        response.setPhotos(photoSearch.pagedPhotoDtos);
        response.setTotalElements(photoSearch.totalMatched);
        response.setAlbums(albums);
        response.setPersons(Collections.emptyList());
        response.setRelaxed(photoSearch.relaxed);
        response.setRelaxedReason(photoSearch.relaxedReason);
        response.setAnswer(buildDayOverviewAnswer(intent, photoSearch, dayCounts));

        List<AiSearchSuggestionAction> suggestionActions =
            buildSuggestionActions(intent, photoSearch, albums, Collections.emptyList());
        response.setSuggestionActions(suggestionActions);
        response.setSuggestions(suggestionActions.stream()
            .map(AiSearchSuggestionAction::getLabel)
            .collect(Collectors.toList()));
        return response;
    }

    private AiSearchIntent buildDayOverviewIntent(String query, CandidateContext candidates) {
        List<String> topicKeywords = extractAnalysisKeywords(query);
        AiSearchIntent intent = new AiSearchIntent();
        intent.setPersonIds(new ArrayList<>());
        intent.setTagIds(new ArrayList<>());
        intent.setAlbumIds(new ArrayList<>());
        intent.setKeywords(new ArrayList<>());
        intent.setFilenameKeywords(new ArrayList<>());
        intent.setMust(new ArrayList<>());
        intent.setShould(new ArrayList<>());
        intent.setMustNot(new ArrayList<>());
        intent.setResultTypes(new ArrayList<>(List.of("photos", "albums")));
        intent.setIncludeHidden(false);
        intent.setNeedAnswer(true);
        intent.setAnswerPrompt("概括拍摄较集中的日期");
        intent.setExplanation("统计指定时间范围内拍摄较集中的日期");
        applyAnalysisTopicKeywords(intent, candidates, topicKeywords);
        return intent;
    }

    private AiSearchResponse buildTagOverviewResponse(String query,
                                                      CandidateContext candidates,
                                                      int page,
                                                      int size,
                                                      String queryMode) {
        AiSearchIntent intent = buildTagOverviewIntent(query, candidates);
        normalizeIntent(query, intent, true);

        PhotoSearchExecution photoSearch = executePhotoQuery(intent, page, size);
        Map<String, Long> tagCounts = summarizeTagCounts(photoSearch.allMatchedPhotos);
        List<AlbumDTO> albums = fetchTopAlbumsForMatchedPhotos(photoSearch.allMatchedPhotos, 6);

        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(queryMode);
        response.setUsedAi(false);
        response.setNeedAnswer(true);
        response.setParsedIntent(intent);
        response.setExplanation(intent.getExplanation());
        response.setPhotos(photoSearch.pagedPhotoDtos);
        response.setTotalElements(photoSearch.totalMatched);
        response.setAlbums(albums);
        response.setPersons(Collections.emptyList());
        response.setRelaxed(photoSearch.relaxed);
        response.setRelaxedReason(photoSearch.relaxedReason);
        response.setAnswer(buildTagOverviewAnswer(intent, photoSearch, tagCounts));

        List<AiSearchSuggestionAction> suggestionActions =
            buildSuggestionActions(intent, photoSearch, albums, Collections.emptyList());
        response.setSuggestionActions(suggestionActions);
        response.setSuggestions(suggestionActions.stream()
            .map(AiSearchSuggestionAction::getLabel)
            .collect(Collectors.toList()));
        return response;
    }

    private AiSearchIntent buildTagOverviewIntent(String query, CandidateContext candidates) {
        List<String> topicKeywords = extractAnalysisKeywords(query);
        AiSearchIntent intent = new AiSearchIntent();
        intent.setPersonIds(new ArrayList<>());
        intent.setTagIds(new ArrayList<>());
        intent.setAlbumIds(new ArrayList<>());
        intent.setKeywords(new ArrayList<>());
        intent.setFilenameKeywords(new ArrayList<>());
        intent.setMust(new ArrayList<>());
        intent.setShould(new ArrayList<>());
        intent.setMustNot(new ArrayList<>());
        intent.setResultTypes(new ArrayList<>(List.of("photos", "albums")));
        intent.setIncludeHidden(false);
        intent.setNeedAnswer(true);
        intent.setAnswerPrompt("概括相关照片中出现较多的标签");
        intent.setExplanation("统计指定时间范围内相关照片的高频标签");
        applyAnalysisTopicKeywords(intent, candidates, topicKeywords);
        return intent;
    }

    private AiSearchResponse buildYearCompareResponse(String query,
                                                      CandidateContext candidates,
                                                      int page,
                                                      int size,
                                                      String queryMode) {
        YearComparison comparison = resolveYearComparison(query);
        AiSearchIntent baseIntent = buildCountOverviewIntent(stripComparisonNoise(query), candidates);
        normalizeIntent(stripComparisonNoise(query), baseIntent, false);

        AiSearchIntent leftIntent = cloneIntent(baseIntent);
        setYearRange(leftIntent, comparison.leftYear);
        AiSearchIntent rightIntent = cloneIntent(baseIntent);
        setYearRange(rightIntent, comparison.rightYear);

        PhotoSearchExecution leftSearch = executePhotoQuery(leftIntent, page, size);
        PhotoSearchExecution rightSearch = executePhotoQuery(rightIntent, page, size);

        boolean leftDominant = leftSearch.totalMatched >= rightSearch.totalMatched;
        AiSearchIntent dominantIntent = leftDominant ? leftIntent : rightIntent;
        PhotoSearchExecution dominantSearch = leftDominant ? leftSearch : rightSearch;
        Map<Long, Long> dominantAlbumCounts = summarizeAlbumCounts(dominantSearch.allMatchedPhotos);
        List<AlbumDTO> albums = fetchAlbumsByCount(dominantAlbumCounts, 4);

        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
        response.setQueryMode(queryMode);
        response.setUsedAi(false);
        response.setNeedAnswer(true);
        response.setParsedIntent(dominantIntent);
        response.setExplanation("对比 " + comparison.leftYear + " 年与 " + comparison.rightYear + " 年相关照片数量");
        response.setPhotos(dominantSearch.pagedPhotoDtos);
        response.setTotalElements(dominantSearch.totalMatched);
        response.setAlbums(albums);
        response.setPersons(Collections.emptyList());
        response.setRelaxed(false);
        response.setRelaxedReason(null);
        response.setAnswer(buildYearCompareAnswer(baseIntent, comparison, leftSearch, rightSearch));

        List<AiSearchSuggestionAction> suggestionActions =
            buildSuggestionActions(dominantIntent, dominantSearch, albums, Collections.emptyList());
        response.setSuggestionActions(suggestionActions);
        response.setSuggestions(suggestionActions.stream()
            .map(AiSearchSuggestionAction::getLabel)
            .collect(Collectors.toList()));
        return response;
    }

    private AiSearchIntent callGpt(String query, CandidateContext candidates, String queryMode) {
        String apiUrl = systemConfigService.getAiSearchApiUrl();
        String apiKey = systemConfigService.getAiSearchApiKey();
        String model = systemConfigService.getAiSearchModel();

        if (apiUrl == null || apiUrl.isEmpty()) {
            throw new RuntimeException("AI搜索API地址未配置");
        }
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("AI搜索API密钥未配置");
        }

        String endpoint = getChatEndpoint(apiUrl);
        String systemPrompt = buildSystemPrompt(candidates, queryMode);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.1);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", query)
        ));

        String responseBody = invokeChatCompletion(endpoint, apiKey, requestBody);
        return parseGptResponse(responseBody);
    }

    private String buildSystemPrompt(CandidateContext candidates, String queryMode) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是照片搜索规划助手。根据用户的单轮自然语言输入和数据库候选项，输出结构化JSON，供后端执行检索。\n\n");
        sb.append("## 当前查询模式\n");
        if ("analysis".equals(queryMode)) {
            sb.append("- 这是分析型问题的第一步，请先提取尽可能稳定的检索条件，不要虚构统计结论；必要时可设置 needAnswer=true 做一句保守总结。\n\n");
        } else if ("simple_answer".equals(queryMode)) {
            sb.append("- 这是轻问答检索，请先完成检索条件抽取；只有在用户明显需要一句结论时才设置 needAnswer=true。\n");
            sb.append("- 如果时间范围与候选相册可能冲突，优先保留稳定的关键词、地点词、时间词，不要为了勉强命中而填明显不一致的相册 id。\n");
            sb.append("- 对“开得怎么样/情况如何”这类问题，只需规划检索，不要假装看到了像素内容。\n\n");
        } else {
            sb.append("- 这是简单检索，请优先返回直接、精简的筛选条件，不要升级成复杂分析，needAnswer 默认 false。\n\n");
        }

        if (!candidates.persons.isEmpty()) {
            sb.append("## 匹配到的人物\n");
            for (PersonProfile p : candidates.persons) {
                sb.append("- id:").append(p.getId()).append(" \"").append(p.getName()).append("\"\n");
            }
            sb.append("\n");
        }

        if (!candidates.tags.isEmpty()) {
            sb.append("## 匹配到的标签\n");
            for (Tag t : candidates.tags) {
                sb.append("- id:").append(t.getId()).append(" \"").append(t.getName()).append("\"\n");
            }
            sb.append("\n");
        }

        if (!candidates.albums.isEmpty()) {
            sb.append("## 匹配到的相册\n");
            for (Album a : candidates.albums) {
                sb.append("- id:").append(a.getId()).append(" \"").append(a.getName()).append("\"");
                if (a.getPath() != null) {
                    sb.append(" (").append(a.getPath()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (!candidates.cameraModels.isEmpty()) {
            sb.append("## 可用相机型号\n");
            sb.append(String.join(", ", candidates.cameraModels)).append("\n\n");
        }
        if (!candidates.lensModels.isEmpty()) {
            sb.append("## 可用镜头型号\n");
            sb.append(String.join(", ", candidates.lensModels)).append("\n\n");
        }

        int currentYear = LocalDate.now().getYear();
        sb.append("## 输出JSON格式\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"personId\": null,\n");
        sb.append("  \"personIds\": [],\n");
        sb.append("  \"tagIds\": [],\n");
        sb.append("  \"albumIds\": [],\n");
        sb.append("  \"startDate\": null,\n");
        sb.append("  \"endDate\": null,\n");
        sb.append("  \"cameraModel\": null,\n");
        sb.append("  \"lensModel\": null,\n");
        sb.append("  \"minFocalLength\": null,\n");
        sb.append("  \"maxFocalLength\": null,\n");
        sb.append("  \"minAperture\": null,\n");
        sb.append("  \"maxAperture\": null,\n");
        sb.append("  \"minShutterSpeed\": null,\n");
        sb.append("  \"maxShutterSpeed\": null,\n");
        sb.append("  \"minIso\": null,\n");
        sb.append("  \"maxIso\": null,\n");
        sb.append("  \"colorCategory\": null,\n");
        sb.append("  \"minQualityScore\": null,\n");
        sb.append("  \"keywords\": [],\n");
        sb.append("  \"filenameKeywords\": [],\n");
        sb.append("  \"must\": [],\n");
        sb.append("  \"should\": [],\n");
        sb.append("  \"mustNot\": [],\n");
        sb.append("  \"resultTypes\": [\"photos\"],\n");
        sb.append("  \"includeHidden\": false,\n");
        sb.append("  \"needAnswer\": false,\n");
        sb.append("  \"answerPrompt\": null,\n");
        sb.append("  \"answerStyle\": null,\n");
        sb.append("  \"explanation\": \"对本次搜索条件的简短中文说明\"\n");
        sb.append("}\n");
        sb.append("```\n\n");

        sb.append("## 条件节点说明\n");
        sb.append("condition.type 只允许使用这些值：person, tag, album, keyword, filename_keyword, camera_model, lens_model, focal_length, aperture, shutter_speed, iso, color, quality, date_range\n");
        sb.append("- person/tag/album 使用 ids\n");
        sb.append("- keyword/filename_keyword 可使用 value 或 values\n");
        sb.append("- camera_model/lens_model/color 使用 value\n");
        sb.append("- focal_length/aperture/shutter_speed/iso/quality 使用 minValue/maxValue\n");
        sb.append("- date_range 使用 startDate/endDate，格式 yyyy-MM-dd\n\n");

        sb.append("## 规则\n");
        sb.append("1. must 中的条件全部满足；should 中的条件满足任意一个；mustNot 中的条件全部排除。\n");
        sb.append("2. 当用户表达\"或者/或/任意一个\"时优先放入 should。\n");
        sb.append("3. 当用户表达\"不要/排除/除了\"时放入 mustNot。\n");
        sb.append("4. 如果没有明显的布尔关系，默认放入 must。\n");
        sb.append("5. 只使用候选列表中存在的 id，不要编造人物、标签、相册 id。\n");
        sb.append("6. 无法映射到候选项的地点词、主题词等，放入 keywords。地点词优先考虑相册 path 和相册名中的地名。\n");
        sb.append("7. \"去年\"表示 ").append(currentYear - 1).append("-01-01 到 ").append(currentYear - 1).append("-12-31，\"前年\"表示 ")
            .append(currentYear - 2).append("-01-01 到 ").append(currentYear - 2).append("-12-31，\"今年\"表示 ")
            .append(currentYear).append("-01-01 到 ").append(currentYear).append("-12-31；只有在用户明确提到月份或具体日期时，才缩小到更小时间范围。\n");
        sb.append("8. \"长焦\"→ focal_length.minValue=85，\"广角\"→ focal_length.maxValue=35，\"大光圈/虚化\"→ aperture.maxValue=2.8。\n");
        sb.append("9. \"暖色\"→ color.value=ORANGE，\"冷色\"→ color.value=BLUE。\n");
        sb.append("10. 文件名模式（IMG_、DSC_、_MG_、带扩展名）放入 filenameKeywords，并加入对应的 filename_keyword 条件。\n");
        sb.append("11. resultTypes 用于控制返回分区，可包含 persons、albums、photos。默认 photos。提到人物时通常加入 persons；提到相册/地点时通常加入 albums。\n");
        sb.append("12. AI 搜索只面向前台公开内容，includeHidden 固定为 false，不要尝试返回隐藏内容。\n");
        sb.append("13. needAnswer 仅在用户明显在发问、需要一句判断或总结时设为 true。普通检索不要回答。\n");
        sb.append("14. answerPrompt 用一句中文描述希望回答什么，例如\"用一句话概括花况\"。\n");
        sb.append("15. explanation 要简短，偏检索解释，不要长篇回复。\n");
        sb.append("16. 兼容旧字段：personIds/tagIds/albumIds/startDate/endDate 等尽量同步填写，方便前端展示。\n");
        sb.append("17. 用户问\"怎么样/如何/好不好/是否\"这类问题时，needAnswer 设为 true，并用 answerPrompt 说明回答重点。\n");
        sb.append("18. 只返回 JSON，不要额外解释。\n");

        return sb.toString();
    }

    private AiSearchIntent parseGptResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("GPT响应中没有choices");
            }

            String content = choices.get(0).get("message").get("content").asText();
            log.info("GPT返回内容: {}", content);

            String json = extractJsonBlock(content);
            JsonNode intentNode = objectMapper.readTree(json);
            sanitizeIntentJson(intentNode);
            return objectMapper.treeToValue(intentNode, AiSearchIntent.class);
        } catch (Exception e) {
            log.error("解析GPT响应失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析AI搜索结果失败: " + e.getMessage());
        }
    }

    private void sanitizeIntentJson(JsonNode intentNode) {
        if (!(intentNode instanceof ObjectNode)) {
            return;
        }
        ObjectNode objectNode = (ObjectNode) intentNode;
        sanitizeIdField(objectNode, "personId");
        sanitizeIdArrayField(objectNode, "personIds");
        sanitizeIdArrayField(objectNode, "tagIds");
        sanitizeIdArrayField(objectNode, "albumIds");

        sanitizeConditionList(objectNode.get("must"));
        sanitizeConditionList(objectNode.get("should"));
        sanitizeConditionList(objectNode.get("mustNot"));
    }

    private void sanitizeIdField(ObjectNode objectNode, String fieldName) {
        JsonNode field = objectNode.get(fieldName);
        if (field == null || field.isNull() || field.isNumber()) {
            return;
        }
        objectNode.putNull(fieldName);
    }

    private void sanitizeIdArrayField(ObjectNode objectNode, String fieldName) {
        JsonNode field = objectNode.get(fieldName);
        if (!(field instanceof ArrayNode)) {
            return;
        }
        ArrayNode arrayNode = (ArrayNode) field;
        ArrayNode sanitized = objectMapper.createArrayNode();
        for (JsonNode item : arrayNode) {
            if (item != null && item.isNumber()) {
                sanitized.add(item.longValue());
            }
        }
        objectNode.set(fieldName, sanitized);
    }

    private void sanitizeConditionList(JsonNode node) {
        if (!(node instanceof ArrayNode)) {
            return;
        }
        ArrayNode conditions = (ArrayNode) node;
        for (JsonNode item : conditions) {
            if (!(item instanceof ObjectNode)) {
                continue;
            }
            ObjectNode condition = (ObjectNode) item;
            sanitizeIdArrayField(condition, "ids");
        }
    }

    private void normalizeIntent(String query, AiSearchIntent intent, boolean applyRelativeYearRange) {
        if (intent.getMust() == null) {
            intent.setMust(new ArrayList<>());
        }
        if (intent.getShould() == null) {
            intent.setShould(new ArrayList<>());
        }
        if (intent.getMustNot() == null) {
            intent.setMustNot(new ArrayList<>());
        }
        if (intent.getKeywords() == null) {
            intent.setKeywords(new ArrayList<>());
        }
        if (intent.getFilenameKeywords() == null) {
            intent.setFilenameKeywords(new ArrayList<>());
        }

        intent.setKeywords(normalizeKeywordList(intent.getKeywords(), false));
        intent.setFilenameKeywords(normalizeKeywordList(intent.getFilenameKeywords(), true));
        normalizeConditionTextValues(intent.getMust());
        normalizeConditionTextValues(intent.getShould());
        normalizeConditionTextValues(intent.getMustNot());
        if (intent.getCameraModel() != null) {
            intent.setCameraModel(intent.getCameraModel().trim());
        }
        if (intent.getLensModel() != null) {
            intent.setLensModel(intent.getLensModel().trim());
        }
        if (intent.getAnswerPrompt() != null) {
            intent.setAnswerPrompt(intent.getAnswerPrompt().trim());
        }
        if (intent.getAnswerStyle() != null) {
            intent.setAnswerStyle(intent.getAnswerStyle().trim());
        }

        if (intent.getMust().isEmpty() && intent.getShould().isEmpty() && intent.getMustNot().isEmpty()) {
            populateGroupedConditionsFromFlatIntent(intent);
        }
        populateLegacyFieldsFromConditions(intent);
        mergeFlatFieldsIntoConditions(intent);
        if (applyRelativeYearRange) {
            normalizeRelativeYearRange(query, intent);
        }
        reconcileAlbumConditions(intent);

        intent.setIncludeHidden(false);
        if (intent.getNeedAnswer() == null) {
            intent.setNeedAnswer(false);
        }
        if (intent.getResultTypes() == null) {
            intent.setResultTypes(new ArrayList<>());
        }
    }

    private void reconcileAlbumConditions(AiSearchIntent intent) {
        LinkedHashSet<Long> albumIds = collectPositiveConditionIds(intent, "album");
        if (albumIds.isEmpty()) {
            return;
        }

        LinkedHashSet<Long> filteredAlbumIds = filterAlbumIdsForDisplay(intent, new LinkedHashSet<>(albumIds));
        if (filteredAlbumIds.size() == albumIds.size()) {
            return;
        }

        if (!filteredAlbumIds.isEmpty()) {
            replacePositiveAlbumConditions(intent, filteredAlbumIds);
            return;
        }

        if (hasNonAlbumPositiveConditions(intent)) {
            removeConditionsByTypes(intent.getMust(), Set.of("album"));
            removeConditionsByTypes(intent.getShould(), Set.of("album"));
            resetLegacyFields(intent);
            populateLegacyFieldsFromConditions(intent);
        }
    }

    private void replacePositiveAlbumConditions(AiSearchIntent intent, LinkedHashSet<Long> albumIds) {
        if (albumIds.isEmpty()) {
            return;
        }

        for (AiSearchCondition condition : safeList(intent.getMust())) {
            if ("album".equals(normalizeType(condition.getType()))) {
                condition.setIds(new ArrayList<>(albumIds));
            }
        }
        for (AiSearchCondition condition : safeList(intent.getShould())) {
            if ("album".equals(normalizeType(condition.getType()))) {
                condition.setIds(new ArrayList<>(albumIds));
            }
        }
        resetLegacyFields(intent);
        populateLegacyFieldsFromConditions(intent);
    }

    private boolean hasNonAlbumPositiveConditions(AiSearchIntent intent) {
        for (AiSearchCondition condition : safeList(intent.getMust())) {
            String type = normalizeType(condition.getType());
            if (!type.isBlank() && !"album".equals(type)) {
                return true;
            }
        }
        for (AiSearchCondition condition : safeList(intent.getShould())) {
            String type = normalizeType(condition.getType());
            if (!type.isBlank() && !"album".equals(type)) {
                return true;
            }
        }
        return false;
    }

    private List<String> normalizeResultTypes(AiSearchIntent intent) {
        LinkedHashSet<String> resultTypes = new LinkedHashSet<>();
        if (intent.getResultTypes() != null) {
            for (String resultType : intent.getResultTypes()) {
                String normalized = normalizeType(resultType);
                if ("persons".equals(normalized) || "albums".equals(normalized) || "photos".equals(normalized)) {
                    resultTypes.add(normalized);
                }
            }
        }

        if (!collectPositiveConditionIds(intent, "person").isEmpty()) {
            resultTypes.add("persons");
        }
        if (!collectPositiveConditionIds(intent, "album").isEmpty()) {
            resultTypes.add("albums");
        }
        if (resultTypes.isEmpty()) {
            resultTypes.add("photos");
        } else if (!resultTypes.contains("photos") && (hasPhotoRelatedConditions(intent) || resultTypes.size() == 1)) {
            resultTypes.add("photos");
        }

        intent.setResultTypes(new ArrayList<>(resultTypes));
        return intent.getResultTypes();
    }

    private boolean hasPhotoRelatedConditions(AiSearchIntent intent) {
        return !(safeList(intent.getMust()).isEmpty() && safeList(intent.getShould()).isEmpty());
    }

    private void populateGroupedConditionsFromFlatIntent(AiSearchIntent intent) {
        List<AiSearchCondition> must = new ArrayList<>();

        List<Long> personIds = getEffectivePersonIds(intent);
        if (!personIds.isEmpty()) {
            must.add(idsCondition("person", personIds));
        }
        if (intent.getTagIds() != null && !intent.getTagIds().isEmpty()) {
            must.add(idsCondition("tag", intent.getTagIds()));
        }
        if (intent.getAlbumIds() != null && !intent.getAlbumIds().isEmpty()) {
            must.add(idsCondition("album", intent.getAlbumIds()));
        }
        if (intent.getStartDate() != null || intent.getEndDate() != null) {
            AiSearchCondition condition = new AiSearchCondition();
            condition.setType("date_range");
            condition.setStartDate(intent.getStartDate());
            condition.setEndDate(intent.getEndDate());
            must.add(condition);
        }
        if (intent.getCameraModel() != null && !intent.getCameraModel().isBlank()) {
            must.add(valueCondition("camera_model", intent.getCameraModel()));
        }
        if (intent.getLensModel() != null && !intent.getLensModel().isBlank()) {
            must.add(valueCondition("lens_model", intent.getLensModel()));
        }
        if (intent.getMinFocalLength() != null || intent.getMaxFocalLength() != null) {
            must.add(rangeCondition("focal_length", intent.getMinFocalLength(), intent.getMaxFocalLength()));
        }
        if (intent.getMinAperture() != null || intent.getMaxAperture() != null) {
            must.add(rangeCondition("aperture", intent.getMinAperture(), intent.getMaxAperture()));
        }
        if (intent.getMinShutterSpeed() != null || intent.getMaxShutterSpeed() != null) {
            must.add(rangeCondition("shutter_speed", intent.getMinShutterSpeed(), intent.getMaxShutterSpeed()));
        }
        if (intent.getMinIso() != null || intent.getMaxIso() != null) {
            must.add(rangeCondition("iso", toDouble(intent.getMinIso()), toDouble(intent.getMaxIso())));
        }
        if (intent.getColorCategory() != null && !intent.getColorCategory().isBlank()) {
            must.add(valueCondition("color", intent.getColorCategory()));
        }
        if (intent.getMinQualityScore() != null) {
            must.add(rangeCondition("quality", intent.getMinQualityScore(), null));
        }
        if (intent.getKeywords() != null && !intent.getKeywords().isEmpty()) {
            AiSearchCondition condition = new AiSearchCondition();
            condition.setType("keyword");
            condition.setValues(intent.getKeywords());
            must.add(condition);
        }
        if (intent.getFilenameKeywords() != null && !intent.getFilenameKeywords().isEmpty()) {
            AiSearchCondition condition = new AiSearchCondition();
            condition.setType("filename_keyword");
            condition.setValues(intent.getFilenameKeywords());
            must.add(condition);
        }

        intent.setMust(must);
    }

    private void populateLegacyFieldsFromConditions(AiSearchIntent intent) {
        LinkedHashSet<Long> personIds = collectPositiveConditionIds(intent, "person");
        if (intent.getPersonIds() == null || intent.getPersonIds().isEmpty()) {
            intent.setPersonIds(new ArrayList<>(personIds));
        }
        if (intent.getPersonId() == null && !personIds.isEmpty()) {
            intent.setPersonId(personIds.iterator().next());
        }

        LinkedHashSet<Long> tagIds = collectPositiveConditionIds(intent, "tag");
        if (intent.getTagIds() == null || intent.getTagIds().isEmpty()) {
            intent.setTagIds(new ArrayList<>(tagIds));
        }

        LinkedHashSet<Long> albumIds = collectPositiveConditionIds(intent, "album");
        if (intent.getAlbumIds() == null || intent.getAlbumIds().isEmpty()) {
            intent.setAlbumIds(new ArrayList<>(albumIds));
        }

        if ((intent.getStartDate() == null || intent.getStartDate().isBlank()) && (intent.getEndDate() == null || intent.getEndDate().isBlank())) {
            AiSearchCondition dateCondition = findFirstPositiveCondition(intent, "date_range");
            if (dateCondition != null) {
                intent.setStartDate(dateCondition.getStartDate());
                intent.setEndDate(dateCondition.getEndDate());
            }
        }

        if (isBlank(intent.getCameraModel())) {
            AiSearchCondition condition = findFirstPositiveCondition(intent, "camera_model");
            if (condition != null) {
                intent.setCameraModel(firstTextValue(condition));
            }
        }
        if (isBlank(intent.getLensModel())) {
            AiSearchCondition condition = findFirstPositiveCondition(intent, "lens_model");
            if (condition != null) {
                intent.setLensModel(firstTextValue(condition));
            }
        }
        if (intent.getMinFocalLength() == null && intent.getMaxFocalLength() == null) {
            AiSearchCondition condition = findFirstPositiveCondition(intent, "focal_length");
            if (condition != null) {
                intent.setMinFocalLength(condition.getMinValue());
                intent.setMaxFocalLength(condition.getMaxValue());
            }
        }
        if (intent.getMinAperture() == null && intent.getMaxAperture() == null) {
            AiSearchCondition condition = findFirstPositiveCondition(intent, "aperture");
            if (condition != null) {
                intent.setMinAperture(condition.getMinValue());
                intent.setMaxAperture(condition.getMaxValue());
            }
        }
        if (intent.getMinShutterSpeed() == null && intent.getMaxShutterSpeed() == null) {
            AiSearchCondition condition = findFirstPositiveCondition(intent, "shutter_speed");
            if (condition != null) {
                intent.setMinShutterSpeed(condition.getMinValue());
                intent.setMaxShutterSpeed(condition.getMaxValue());
            }
        }
        if (intent.getMinIso() == null && intent.getMaxIso() == null) {
            AiSearchCondition condition = findFirstPositiveCondition(intent, "iso");
            if (condition != null) {
                intent.setMinIso(toInteger(condition.getMinValue()));
                intent.setMaxIso(toInteger(condition.getMaxValue()));
            }
        }
        if (isBlank(intent.getColorCategory())) {
            AiSearchCondition condition = findFirstPositiveCondition(intent, "color");
            if (condition != null) {
                intent.setColorCategory(firstTextValue(condition));
            }
        }
        if (intent.getMinQualityScore() == null) {
            AiSearchCondition condition = findFirstPositiveCondition(intent, "quality");
            if (condition != null) {
                intent.setMinQualityScore(condition.getMinValue());
            }
        }
        if (intent.getKeywords() == null || intent.getKeywords().isEmpty()) {
            intent.setKeywords(collectTextValues(intent, "keyword"));
        }
        if (intent.getFilenameKeywords() == null || intent.getFilenameKeywords().isEmpty()) {
            intent.setFilenameKeywords(collectTextValues(intent, "filename_keyword"));
        }
    }

    private void mergeFlatFieldsIntoConditions(AiSearchIntent intent) {
        List<AiSearchCondition> must = intent.getMust();

        List<Long> personIds = getEffectivePersonIds(intent);
        if (!personIds.isEmpty() && !hasPositiveConditionType(intent, "person")) {
            must.add(idsCondition("person", personIds));
        }
        if (intent.getTagIds() != null && !intent.getTagIds().isEmpty() && !hasPositiveConditionType(intent, "tag")) {
            must.add(idsCondition("tag", intent.getTagIds()));
        }
        if (intent.getAlbumIds() != null && !intent.getAlbumIds().isEmpty() && !hasPositiveConditionType(intent, "album")) {
            must.add(idsCondition("album", intent.getAlbumIds()));
        }
        if ((intent.getStartDate() != null || intent.getEndDate() != null) && !hasPositiveConditionType(intent, "date_range")) {
            AiSearchCondition condition = new AiSearchCondition();
            condition.setType("date_range");
            condition.setStartDate(intent.getStartDate());
            condition.setEndDate(intent.getEndDate());
            must.add(condition);
        }
        if (!isBlank(intent.getCameraModel()) && !hasPositiveConditionType(intent, "camera_model")) {
            must.add(valueCondition("camera_model", intent.getCameraModel()));
        }
        if (!isBlank(intent.getLensModel()) && !hasPositiveConditionType(intent, "lens_model")) {
            must.add(valueCondition("lens_model", intent.getLensModel()));
        }
        if ((intent.getMinFocalLength() != null || intent.getMaxFocalLength() != null) && !hasPositiveConditionType(intent, "focal_length")) {
            must.add(rangeCondition("focal_length", intent.getMinFocalLength(), intent.getMaxFocalLength()));
        }
        if ((intent.getMinAperture() != null || intent.getMaxAperture() != null) && !hasPositiveConditionType(intent, "aperture")) {
            must.add(rangeCondition("aperture", intent.getMinAperture(), intent.getMaxAperture()));
        }
        if ((intent.getMinShutterSpeed() != null || intent.getMaxShutterSpeed() != null) && !hasPositiveConditionType(intent, "shutter_speed")) {
            must.add(rangeCondition("shutter_speed", intent.getMinShutterSpeed(), intent.getMaxShutterSpeed()));
        }
        if ((intent.getMinIso() != null || intent.getMaxIso() != null) && !hasPositiveConditionType(intent, "iso")) {
            must.add(rangeCondition("iso", toDouble(intent.getMinIso()), toDouble(intent.getMaxIso())));
        }
        if (!isBlank(intent.getColorCategory()) && !hasPositiveConditionType(intent, "color")) {
            must.add(valueCondition("color", intent.getColorCategory()));
        }
        if (intent.getMinQualityScore() != null && !hasPositiveConditionType(intent, "quality")) {
            must.add(rangeCondition("quality", intent.getMinQualityScore(), null));
        }
        if (intent.getKeywords() != null && !intent.getKeywords().isEmpty() && !hasPositiveConditionType(intent, "keyword")) {
            AiSearchCondition condition = new AiSearchCondition();
            condition.setType("keyword");
            condition.setValues(intent.getKeywords());
            must.add(condition);
        }
        if (intent.getFilenameKeywords() != null && !intent.getFilenameKeywords().isEmpty() && !hasPositiveConditionType(intent, "filename_keyword")) {
            AiSearchCondition condition = new AiSearchCondition();
            condition.setType("filename_keyword");
            condition.setValues(intent.getFilenameKeywords());
            must.add(condition);
        }
    }

    private void normalizeRelativeYearRange(String query, AiSearchIntent intent) {
        if (query == null || query.isBlank() || hasExplicitMonthOrDay(query)) {
            return;
        }

        Integer targetYear = null;
        int currentYear = LocalDate.now().getYear();
        if (query.contains("前年")) {
            targetYear = currentYear - 2;
        } else if (query.contains("去年")) {
            targetYear = currentYear - 1;
        } else if (query.contains("今年")) {
            targetYear = currentYear;
        }

        if (targetYear == null) {
            return;
        }

        String startDate = targetYear + "-01-01";
        String endDate = targetYear + "-12-31";
        intent.setStartDate(startDate);
        intent.setEndDate(endDate);

        AiSearchCondition dateCondition = findFirstPositiveCondition(intent, "date_range");
        if (dateCondition == null) {
            dateCondition = new AiSearchCondition();
            dateCondition.setType("date_range");
            intent.getMust().add(dateCondition);
        }
        dateCondition.setStartDate(startDate);
        dateCondition.setEndDate(endDate);
    }

    private boolean hasExplicitMonthOrDay(String query) {
        return query.matches(".*\\d+月.*")
            || query.matches(".*\\d+日.*")
            || query.matches(".*\\d+号.*")
            || query.matches(".*\\d{4}[-./]\\d{1,2}([-. /]\\d{1,2})?.*")
            || query.matches(".*\\d{1,2}[-./]\\d{1,2}.*");
    }

    private PhotoSearchExecution executePhotoQuery(AiSearchIntent intent, int page, int size) {
        List<AiSearchCondition> must = safeList(intent.getMust());
        List<AiSearchCondition> should = safeList(intent.getShould());
        List<AiSearchCondition> mustNot = safeList(intent.getMustNot());
        boolean includeHidden = intent.isIncludeHidden();

        if (must.isEmpty() && should.isEmpty() && mustNot.isEmpty()) {
            return PhotoSearchExecution.empty();
        }

        Set<Long> candidateIds = null;
        for (AiSearchCondition condition : must) {
            Set<Long> ids = evaluateCondition(condition, includeHidden);
            candidateIds = intersect(candidateIds, ids);
            if (candidateIds != null && candidateIds.isEmpty()) {
                return PhotoSearchExecution.empty();
            }
        }

        if (!should.isEmpty()) {
            Set<Long> shouldIds = evaluateUnionConditions(should, includeHidden);
            candidateIds = candidateIds == null ? shouldIds : intersect(candidateIds, shouldIds);
        }

        if (candidateIds == null || candidateIds.isEmpty()) {
            return PhotoSearchExecution.empty();
        }

        if (!mustNot.isEmpty()) {
            Set<Long> excluded = evaluateUnionConditions(mustNot, includeHidden);
            candidateIds.removeAll(excluded);
            if (candidateIds.isEmpty()) {
                return PhotoSearchExecution.empty();
            }
        }

        List<Photo> matchedPhotos = includeHidden
            ? photoRepository.findAllByIdInIncludeHidden(candidateIds)
            : photoRepository.findAllByIdIn(candidateIds);

        if (!includeHidden) {
            matchedPhotos = matchedPhotos.stream()
                .filter(photo -> !Boolean.TRUE.equals(photo.getIsHidden()))
                .collect(Collectors.toList());
        }

        matchedPhotos.sort((a, b) -> {
            if (a.getTakenAt() == null && b.getTakenAt() == null) {
                return Long.compare(b.getId(), a.getId());
            }
            if (a.getTakenAt() == null) {
                return 1;
            }
            if (b.getTakenAt() == null) {
                return -1;
            }
            return b.getTakenAt().compareTo(a.getTakenAt());
        });

        int start = page * size;
        if (start >= matchedPhotos.size()) {
            return new PhotoSearchExecution(Collections.emptyList(), matchedPhotos, matchedPhotos.size());
        }
        int end = Math.min(start + size, matchedPhotos.size());
        List<PhotoDTO> pageDtos = matchedPhotos.subList(start, end).stream()
            .map(photoService::convertToDTO)
            .collect(Collectors.toList());

        return new PhotoSearchExecution(pageDtos, matchedPhotos, matchedPhotos.size());
    }

    private PhotoSearchExecution tryRelaxedPhotoQuery(AiSearchIntent intent, int page, int size) {
        List<RelaxationPlan> plans = List.of(
            new RelaxationPlan(true, false, false, "已自动放宽关键词限制"),
            new RelaxationPlan(false, true, false, "已自动放宽时间限制"),
            new RelaxationPlan(true, true, false, "已自动放宽关键词和时间限制"),
            new RelaxationPlan(true, true, true, "已自动放宽相册限制，仅保留核心主题词")
        );

        for (RelaxationPlan plan : plans) {
            AiSearchIntent relaxedIntent = cloneIntent(intent);
            if (applyRelaxation(relaxedIntent, plan)) {
                PhotoSearchExecution execution = executePhotoQuery(relaxedIntent, page, size);
                if (execution.totalMatched > 0) {
                    return execution.withRelaxed(plan.reason);
                }
            }
        }

        return PhotoSearchExecution.empty();
    }

    private boolean applyRelaxation(AiSearchIntent intent, RelaxationPlan plan) {
        boolean changed = false;
        if (plan.dropKeywordConditions) {
            changed |= removeConditionsByTypes(intent.getMust(), Set.of("keyword", "filename_keyword"));
            changed |= removeConditionsByTypes(intent.getShould(), Set.of("keyword", "filename_keyword"));
        }
        if (plan.dropDateConditions) {
            changed |= removeConditionsByTypes(intent.getMust(), Set.of("date_range"));
            changed |= removeConditionsByTypes(intent.getShould(), Set.of("date_range"));
        }
        if (plan.dropAlbumConditions) {
            changed |= removeConditionsByTypes(intent.getMust(), Set.of("album"));
            changed |= removeConditionsByTypes(intent.getShould(), Set.of("album"));
        }
        return changed;
    }

    private Set<Long> evaluateUnionConditions(List<AiSearchCondition> conditions, boolean includeHidden) {
        Set<Long> result = new LinkedHashSet<>();
        for (AiSearchCondition condition : conditions) {
            result.addAll(evaluateCondition(condition, includeHidden));
        }
        return result;
    }

    private Set<Long> evaluateCondition(AiSearchCondition condition, boolean includeHidden) {
        if (condition == null || isBlank(condition.getType())) {
            return Collections.emptySet();
        }

        String type = normalizeType(condition.getType());
        switch (type) {
            case "person":
                return photoIdsForPersonCondition(condition, includeHidden);
            case "tag":
                return photoIdsForTagCondition(condition, includeHidden);
            case "album":
                return photoIdsForAlbumCondition(condition, includeHidden);
            case "keyword":
                return photoIdsForKeywordCondition(condition, includeHidden);
            case "filename_keyword":
                return photoIdsForFilenameCondition(condition, includeHidden);
            case "camera_model":
            case "lens_model":
            case "focal_length":
            case "aperture":
            case "shutter_speed":
            case "iso":
            case "color":
            case "quality":
            case "date_range":
                return photoIdsForMetadataCondition(condition, includeHidden);
            default:
                log.debug("忽略未知AI搜索条件类型: {}", type);
                return Collections.emptySet();
        }
    }

    private Set<Long> photoIdsForPersonCondition(AiSearchCondition condition, boolean includeHidden) {
        List<Long> ids = safeLongList(condition.getIds());
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> result = new LinkedHashSet<>();
        for (Long personId : ids) {
            Page<Photo> page = includeHidden
                ? photoRepository.findByPersonIdIncludeHidden(personId, PageRequest.of(0, MAX_QUERY_FETCH))
                : photoRepository.findByPersonId(personId, PageRequest.of(0, MAX_QUERY_FETCH));
            page.getContent().forEach(photo -> {
                if (includeHidden || !Boolean.TRUE.equals(photo.getIsHidden())) {
                    result.add(photo.getId());
                }
            });
        }
        return result;
    }

    private Set<Long> photoIdsForTagCondition(AiSearchCondition condition, boolean includeHidden) {
        List<Long> ids = safeLongList(condition.getIds());
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }

        Page<Photo> page = includeHidden
            ? photoRepository.findByTagIdsIncludeHidden(ids, PageRequest.of(0, MAX_QUERY_FETCH))
            : photoRepository.findByTagIds(ids, PageRequest.of(0, MAX_QUERY_FETCH));
        return page.getContent().stream()
            .filter(photo -> includeHidden || !Boolean.TRUE.equals(photo.getIsHidden()))
            .map(Photo::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> photoIdsForAlbumCondition(AiSearchCondition condition, boolean includeHidden) {
        List<Long> ids = safeLongList(condition.getIds());
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }

        Page<Photo> page = includeHidden
            ? photoRepository.findByAlbumIdsIncludeHidden(ids, PageRequest.of(0, MAX_QUERY_FETCH))
            : photoRepository.findByAlbumIds(ids, PageRequest.of(0, MAX_QUERY_FETCH));
        return page.getContent().stream()
            .filter(photo -> includeHidden || !Boolean.TRUE.equals(photo.getIsHidden()))
            .map(Photo::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Long> photoIdsForKeywordCondition(AiSearchCondition condition, boolean includeHidden) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>(safeTextValues(condition));
        if (keywords.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> result = new LinkedHashSet<>();
        for (String keyword : keywords) {
            List<Photo> fileResults = includeHidden
                ? photoRepository.searchByFilenameIncludeHidden(keyword)
                : photoRepository.searchByFilename(keyword);
            fileResults.stream()
                .filter(photo -> includeHidden || !Boolean.TRUE.equals(photo.getIsHidden()))
                .forEach(photo -> result.add(photo.getId()));

            List<Album> albumResults = albumRepository.searchByName(keyword);
            albumResults.addAll(albumRepository.searchByPath(keyword));
            for (Album album : albumResults) {
                Page<Photo> albumPhotos = includeHidden
                    ? photoRepository.findByAlbumIdsIncludeHidden(List.of(album.getId()), PageRequest.of(0, MAX_QUERY_FETCH))
                    : photoRepository.findByAlbumIds(List.of(album.getId()), PageRequest.of(0, MAX_QUERY_FETCH));
                albumPhotos.getContent().stream()
                    .filter(photo -> includeHidden || !Boolean.TRUE.equals(photo.getIsHidden()))
                    .forEach(photo -> result.add(photo.getId()));
            }
        }
        return result;
    }

    private Set<Long> photoIdsForFilenameCondition(AiSearchCondition condition, boolean includeHidden) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>(safeTextValues(condition));
        if (keywords.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> result = new LinkedHashSet<>();
        for (String keyword : keywords) {
            List<Photo> matches = includeHidden
                ? photoRepository.searchByFilenameIncludeHidden(keyword)
                : photoRepository.searchByFilename(keyword);
            matches.stream()
                .filter(photo -> includeHidden || !Boolean.TRUE.equals(photo.getIsHidden()))
                .forEach(photo -> result.add(photo.getId()));
        }
        return result;
    }

    private Set<Long> photoIdsForMetadataCondition(AiSearchCondition condition, boolean includeHidden) {
        String type = normalizeType(condition.getType());
        String cameraModel = null;
        String lensModel = null;
        Double minAperture = null;
        Double maxAperture = null;
        Double minFocalLength = null;
        Double maxFocalLength = null;
        Double minShutterSpeed = null;
        Double maxShutterSpeed = null;
        Integer minIso = null;
        Integer maxIso = null;
        String colorCategory = null;
        Double minQualityScore = null;
        LocalDateTime startDate = null;
        LocalDateTime endDate = null;

        switch (type) {
            case "camera_model":
                cameraModel = firstTextValue(condition);
                break;
            case "lens_model":
                lensModel = firstTextValue(condition);
                break;
            case "aperture":
                minAperture = condition.getMinValue();
                maxAperture = condition.getMaxValue();
                break;
            case "focal_length":
                minFocalLength = condition.getMinValue();
                maxFocalLength = condition.getMaxValue();
                break;
            case "shutter_speed":
                minShutterSpeed = condition.getMinValue();
                maxShutterSpeed = condition.getMaxValue();
                break;
            case "iso":
                minIso = toInteger(condition.getMinValue());
                maxIso = toInteger(condition.getMaxValue());
                break;
            case "color":
                colorCategory = firstTextValue(condition);
                break;
            case "quality":
                minQualityScore = condition.getMinValue();
                break;
            case "date_range":
                startDate = parseDate(condition.getStartDate(), true);
                endDate = parseDate(condition.getEndDate(), false);
                break;
            default:
                break;
        }

        Page<Photo> page = photoRepository.findByExifFilters(
            blankToNull(cameraModel),
            blankToNull(lensModel),
            minAperture, maxAperture,
            minFocalLength, maxFocalLength,
            minShutterSpeed, maxShutterSpeed,
            minIso, maxIso,
            blankToNull(colorCategory),
            minQualityScore,
            startDate, endDate,
            List.of(-1L),
            PageRequest.of(0, MAX_QUERY_FETCH, Sort.by(Sort.Direction.DESC, "takenAt"))
        );

        return page.getContent().stream()
            .filter(photo -> includeHidden || !Boolean.TRUE.equals(photo.getIsHidden()))
            .map(Photo::getId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<AiSearchSuggestionAction> buildSuggestionActions(AiSearchIntent intent,
                                                                  PhotoSearchExecution photoSearch,
                                                                  List<AlbumDTO> albums,
                                                                  List<PersonSummaryDTO> persons) {
        List<AiSearchSuggestionAction> suggestions = new ArrayList<>();

        if (hasPositiveConditionType(intent, "date_range")) {
            suggestions.add(suggestionAction("去掉时间限制再搜", "remove_condition_types", List.of("date_range")));
        }
        if (hasPositiveConditionType(intent, "keyword") && photoSearch.totalMatched == 0) {
            suggestions.add(suggestionAction("只搜 " + buildKeywordSummary(intent), "keep_only_condition_types", List.of("keyword")));
        }
        if (hasPositiveConditionType(intent, "filename_keyword") && photoSearch.totalMatched == 0) {
            suggestions.add(suggestionAction("只按文件名再搜", "keep_only_condition_types", List.of("filename_keyword")));
        }

        suggestions.sort((left, right) -> Integer.compare(
            suggestionPriority(right, photoSearch.totalMatched),
            suggestionPriority(left, photoSearch.totalMatched)
        ));
        return suggestions.stream()
            .distinct()
            .limit(4)
            .collect(Collectors.toList());
    }

    private int suggestionPriority(AiSearchSuggestionAction suggestion, long totalMatched) {
        if (suggestion == null) {
            return Integer.MIN_VALUE;
        }

        int score = 0;
        String actionType = suggestion.getActionType() == null ? "" : suggestion.getActionType().trim().toLowerCase(Locale.ROOT);
        List<String> conditionTypes = suggestion.getConditionTypes() == null
            ? Collections.emptyList()
            : suggestion.getConditionTypes().stream().map(this::normalizeType).collect(Collectors.toList());

        if (totalMatched == 0) {
            score += 100;
        }
        if ("remove_condition_types".equals(actionType)) {
            score += 40;
        }
        if ("keep_only_condition_types".equals(actionType)) {
            score += 20;
        }
        if (conditionTypes.contains("date_range")) {
            score += 30;
        }
        if (conditionTypes.contains("keyword")) {
            score += 20;
        }
        if (conditionTypes.contains("filename_keyword")) {
            score += 10;
        }
        return score;
    }

    private String buildKeywordSummary(AiSearchIntent intent) {
        List<String> keywords = normalizeKeywordList(intent.getKeywords(), false);
        if (keywords.isEmpty()) {
            return "相关内容";
        }
        return String.join(" ", keywords.stream().limit(3).collect(Collectors.toList()));
    }

    private AiSearchSuggestionAction suggestionAction(String label, String actionType, List<String> conditionTypes) {
        AiSearchSuggestionAction action = new AiSearchSuggestionAction();
        action.setLabel(label);
        action.setActionType(actionType);
        action.setConditionTypes(conditionTypes);
        return action;
    }

    private AiSearchIntent applySuggestionAction(AiSearchIntent baseIntent, AiSearchSuggestionAction suggestionAction) {
        AiSearchIntent adjustedIntent = cloneIntent(baseIntent);
        if (suggestionAction == null || isBlank(suggestionAction.getActionType())) {
            return adjustedIntent;
        }

        Set<String> types = suggestionAction.getConditionTypes() == null
            ? Collections.emptySet()
            : suggestionAction.getConditionTypes().stream()
                .map(this::normalizeType)
                .filter(type -> !type.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String actionType = suggestionAction.getActionType().trim().toLowerCase(Locale.ROOT);
        if ("remove_condition_types".equals(actionType)) {
            removeConditionsByTypes(adjustedIntent.getMust(), types);
            removeConditionsByTypes(adjustedIntent.getShould(), types);
            removeConditionsByTypes(adjustedIntent.getMustNot(), types);
        } else if ("keep_only_condition_types".equals(actionType)) {
            retainConditionsByTypes(adjustedIntent.getMust(), types);
            retainConditionsByTypes(adjustedIntent.getShould(), types);
            adjustedIntent.setMustNot(new ArrayList<>());
        }

        resetLegacyFields(adjustedIntent);
        populateLegacyFieldsFromConditions(adjustedIntent);
        adjustedIntent.setNeedAnswer(false);
        adjustedIntent.setAnswerPrompt(null);
        adjustedIntent.setAnswerStyle(null);
        adjustedIntent.setResultTypes(new ArrayList<>());
        adjustedIntent.setExplanation(suggestionAction.getLabel());
        return adjustedIntent;
    }

    private String generateAnswer(String query,
                                  AiSearchIntent intent,
                                  PhotoSearchExecution photoSearch,
                                  List<AlbumDTO> albums,
                                  List<PersonSummaryDTO> persons) {
        if (photoSearch.totalMatched == 0 && albums.isEmpty() && persons.isEmpty()) {
            return "检索结论：图库中未找到可用于判断的相关结果。";
        }

        String apiUrl = systemConfigService.getAiSearchApiUrl();
        String apiKey = systemConfigService.getAiSearchApiKey();
        String model = systemConfigService.getAiSearchModel();
        if (isBlank(apiUrl) || isBlank(apiKey)) {
            return buildFallbackAnswer(photoSearch, albums, persons);
        }

        String endpoint = getChatEndpoint(apiUrl);
        String summary = buildAnswerSummary(intent, photoSearch, albums, persons);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.2);
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("messages", List.of(
            Map.of(
                "role", "system",
                "content", "你是图库检索结果解读助手。只能根据提供的检索结果摘要作答，不能假装看到了图片像素内容。请先判断证据是否足够，再输出结构化 JSON。规则：1. 如果已经命中照片、相册或人物，但证据不足以回答更主观的问题，也要先客观说明已找到相关结果，再说明仅凭当前元数据暂不足以进一步判断；不要把“有结果但无法深判”说成“未找到足够结果”。2. answer 使用简洁的“检索结论：...”口吻，不要自称，不要说“我认为/你可以”。3. evidenceStatus 只能是 sufficient、limited、none。4. 只返回 JSON：{\"answer\":\"...\",\"evidenceStatus\":\"limited\"}。"
            ),
            Map.of(
                "role", "user",
                "content", "用户问题：" + query + "\n" +
                    (isBlank(intent.getAnswerPrompt()) ? "" : "希望回答：" + intent.getAnswerPrompt() + "\n") +
                    (isBlank(intent.getAnswerStyle()) ? "" : "回答风格：" + intent.getAnswerStyle() + "\n") +
                    "\n检索结果摘要：\n" + summary)
        ));

        try {
            String responseBody = invokeChatCompletion(endpoint, apiKey, requestBody);
            AnswerDraft draft = parseAnswerDraft(responseBody);
            if (draft != null && !isBlank(draft.answer)) {
                return finalizeGeneratedAnswer(draft, photoSearch, albums, persons);
            }
        } catch (Exception e) {
            log.warn("生成AI回答失败，使用兜底回答: {}", e.getMessage());
        }

        return buildFallbackAnswer(photoSearch, albums, persons);
    }

    private String buildAnswerSummary(AiSearchIntent intent,
                                      PhotoSearchExecution photoSearch,
                                      List<AlbumDTO> albums,
                                      List<PersonSummaryDTO> persons) {
        StringBuilder sb = new StringBuilder();
        sb.append("- 搜索说明: ").append(nullToDefault(intent.getExplanation(), "无")).append("\n");
        sb.append("- 匹配照片数: ").append(photoSearch.totalMatched).append("\n");
        sb.append("- 匹配人物数: ").append(persons.size()).append("\n");
        sb.append("- 匹配相册数: ").append(albums.size()).append("\n");

        if (!photoSearch.allMatchedPhotos.isEmpty()) {
            LocalDateTime earliest = photoSearch.allMatchedPhotos.stream()
                .map(Photo::getTakenAt)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
            LocalDateTime latest = photoSearch.allMatchedPhotos.stream()
                .map(Photo::getTakenAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
            if (earliest != null || latest != null) {
                sb.append("- 拍摄时间范围: ")
                    .append(earliest != null ? earliest.toLocalDate() : "未知")
                    .append(" ~ ")
                    .append(latest != null ? latest.toLocalDate() : "未知")
                    .append("\n");
            }

            Map<String, Long> topTags = topTextCounts(photoSearch.allMatchedPhotos.stream()
                .flatMap(photo -> photo.getTags() == null ? java.util.stream.Stream.<Tag>empty() : photo.getTags().stream())
                .map(Tag::getName)
                .collect(Collectors.toList()), 5);
            if (!topTags.isEmpty()) {
                sb.append("- 高频标签: ")
                    .append(String.join("，", topTags.keySet()))
                    .append("\n");
            }

            Map<String, Long> topCameras = topTextCounts(photoSearch.allMatchedPhotos.stream()
                .map(Photo::getCameraModel)
                .collect(Collectors.toList()), 3);
            if (!topCameras.isEmpty()) {
                sb.append("- 常见相机: ")
                    .append(String.join("，", topCameras.keySet()))
                    .append("\n");
            }

            Map<String, Long> topColors = topTextCounts(photoSearch.allMatchedPhotos.stream()
                .map(Photo::getColorCategory)
                .collect(Collectors.toList()), 3);
            if (!topColors.isEmpty()) {
                sb.append("- 常见色彩: ")
                    .append(String.join("，", topColors.keySet()))
                    .append("\n");
            }

            Map<String, Long> topMonths = summarizeMonthCounts(photoSearch.allMatchedPhotos);
            if (!topMonths.isEmpty()) {
                sb.append("- 高频月份: ")
                    .append(topMonths.entrySet().stream()
                        .limit(3)
                        .map(entry -> entry.getKey() + "(" + entry.getValue() + "张)")
                        .collect(Collectors.joining("，")))
                    .append("\n");
            }

            Map<String, Long> topDays = summarizeDayCounts(photoSearch.allMatchedPhotos);
            if (!topDays.isEmpty()) {
                sb.append("- 高频日期: ")
                    .append(topDays.entrySet().stream()
                        .limit(3)
                        .map(entry -> entry.getKey() + "(" + entry.getValue() + "张)")
                        .collect(Collectors.joining("，")))
                    .append("\n");
            }

            Map<Long, Long> topAlbumCounts = summarizeAlbumCounts(photoSearch.allMatchedPhotos);
            if (!topAlbumCounts.isEmpty()) {
                List<AlbumDTO> topAlbums = fetchAlbumsByCount(topAlbumCounts, 3);
                if (!topAlbums.isEmpty()) {
                    Map<Long, Long> countLookup = new LinkedHashMap<>(topAlbumCounts);
                    sb.append("- 高频相册: ")
                        .append(topAlbums.stream()
                            .map(album -> album.getName() + "(" + countLookup.getOrDefault(album.getId(), 0L) + "张)")
                            .collect(Collectors.joining("，")))
                        .append("\n");
                }
            }

            Map<Long, String> albumNames = resolveAlbumNames(photoSearch.allMatchedPhotos);
            sb.append("- 部分照片:\n");
            photoSearch.allMatchedPhotos.stream().limit(8).forEach(photo -> {
                sb.append("  - ")
                    .append(photo.getFilename());
                if (photo.getTakenAt() != null) {
                    sb.append(" | ").append(photo.getTakenAt().toLocalDate());
                }
                String albumName = albumNames.get(photo.getAlbumId());
                if (albumName != null) {
                    sb.append(" | 相册:").append(albumName);
                }
                sb.append("\n");
            });
        }

        if (!persons.isEmpty()) {
            sb.append("- 人物结果: ")
                .append(persons.stream().limit(5)
                    .map(person -> person.getName() + "(" + nullSafeCount(person.getFaceCount()) + "张)")
                    .collect(Collectors.joining("，")))
                .append("\n");
        }

        if (!albums.isEmpty()) {
            sb.append("- 相册结果: ")
                .append(albums.stream().limit(5)
                    .map(album -> album.getName() + "(" + nullSafeCount(album.getPhotoCount()) + "张)")
                    .collect(Collectors.joining("，")))
                .append("\n");
        }

        return sb.toString();
    }

    private String buildFallbackAnswer(PhotoSearchExecution photoSearch,
                                       List<AlbumDTO> albums,
                                       List<PersonSummaryDTO> persons) {
        if (photoSearch.totalMatched > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("检索结论：已找到 ").append(photoSearch.totalMatched).append(" 张相关照片");
            LocalDateTime earliest = photoSearch.allMatchedPhotos.stream()
                .map(Photo::getTakenAt)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);
            LocalDateTime latest = photoSearch.allMatchedPhotos.stream()
                .map(Photo::getTakenAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
            if (earliest != null || latest != null) {
                sb.append("，拍摄时间大致在 ")
                    .append(earliest != null ? earliest.toLocalDate() : "未知")
                    .append(" 到 ")
                    .append(latest != null ? latest.toLocalDate() : "未知");
            }
            sb.append("。");
            return sb.toString();
        }
        if (!albums.isEmpty() || !persons.isEmpty()) {
            return "检索结论：已找到相关人物或相册结果，但暂未命中具体照片。";
        }
        return "检索结论：图库中暂未找到足够结果。";
    }

    private String finalizeGeneratedAnswer(AnswerDraft draft,
                                           PhotoSearchExecution photoSearch,
                                           List<AlbumDTO> albums,
                                           List<PersonSummaryDTO> persons) {
        String trimmed = draft == null || draft.answer == null ? "" : draft.answer.trim();
        if (trimmed.isBlank()) {
            return buildFallbackAnswer(photoSearch, albums, persons);
        }

        if (!hasAnswerEvidence(photoSearch, albums, persons)) {
            return ensureAnswerPrefix(trimmed);
        }

        String evidenceStatus = normalizeLooseText(draft == null ? "" : draft.evidenceStatus);
        if ("none".equals(evidenceStatus) || indicatesNoResultAnswer(trimmed)) {
            return buildFallbackAnswer(photoSearch, albums, persons);
        }

        if (("limited".equals(evidenceStatus) || indicatesLimitedJudgement(trimmed)) && !mentionsHitSummary(trimmed)) {
            return buildEvidenceAwareAnswer(photoSearch, albums, persons);
        }

        return ensureAnswerPrefix(trimmed);
    }

    private boolean hasAnswerEvidence(PhotoSearchExecution photoSearch,
                                      List<AlbumDTO> albums,
                                      List<PersonSummaryDTO> persons) {
        return photoSearch.totalMatched > 0 || !albums.isEmpty() || !persons.isEmpty();
    }

    private boolean indicatesNoResultAnswer(String answer) {
        String normalized = normalizeLooseText(answer);
        return normalized.contains("未找到足够结果")
            || normalized.contains("未找到可用于判断")
            || normalized.contains("未找到相关结果")
            || normalized.contains("没有找到足够结果");
    }

    private boolean indicatesLimitedJudgement(String answer) {
        String normalized = normalizeLooseText(answer);
        return normalized.contains("不足以判断")
            || normalized.contains("暂时无法判断")
            || normalized.contains("无法判断")
            || normalized.contains("难以判断")
            || normalized.contains("仅凭当前元数据")
            || normalized.contains("仅根据当前结果");
    }

    private boolean mentionsHitSummary(String answer) {
        String normalized = normalizeLooseText(answer);
        return normalized.contains("已找到")
            || normalized.contains("找到")
            || normalized.contains("命中")
            || normalized.contains("相关照片")
            || normalized.contains("相关相册")
            || normalized.contains("相关人物")
            || normalized.contains("匹配");
    }

    private String buildEvidenceAwareAnswer(PhotoSearchExecution photoSearch,
                                            List<AlbumDTO> albums,
                                            List<PersonSummaryDTO> persons) {
        if (photoSearch.totalMatched > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("检索结论：已找到 ").append(photoSearch.totalMatched).append(" 张相关照片");
            String timing = buildEvidenceTimingSummary(photoSearch);
            if (!timing.isBlank()) {
                sb.append("，").append(timing);
            }
            sb.append("，但仅凭当前的相册名、标签和时间信息，暂不足以进一步判断更细的内容。");
            return sb.toString();
        }
        if (!albums.isEmpty() || !persons.isEmpty()) {
            return "检索结论：已找到相关人物或相册结果，但暂未命中足够的照片证据，暂时无法进一步判断。";
        }
        return "检索结论：图库中暂未找到足够结果。";
    }

    private String buildEvidenceTimingSummary(PhotoSearchExecution photoSearch) {
        Map<String, Long> dayCounts = summarizeDayCounts(photoSearch.allMatchedPhotos);
        if (!dayCounts.isEmpty()) {
            Map.Entry<String, Long> topDay = dayCounts.entrySet().iterator().next();
            if (topDay.getValue() > 1) {
                return "拍摄主要集中在 " + topDay.getKey() + " 前后";
            }
            return "拍摄时间集中在 " + topDay.getKey();
        }

        Map<String, Long> monthCounts = summarizeMonthCounts(photoSearch.allMatchedPhotos);
        if (!monthCounts.isEmpty()) {
            Map.Entry<String, Long> topMonth = monthCounts.entrySet().iterator().next();
            return "拍摄主要集中在 " + topMonth.getKey();
        }

        return "";
    }

    private String ensureAnswerPrefix(String answer) {
        String trimmed = answer == null ? "" : answer.trim();
        if (trimmed.isBlank()) {
            return trimmed;
        }
        return trimmed.startsWith("检索结论") ? trimmed : "检索结论：" + trimmed;
    }

    private AnswerDraft parseAnswerDraft(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JsonNode message = choices.get(0).get("message");
            if (message == null || message.get("content") == null) {
                return null;
            }

            String content = message.get("content").asText();
            if (content == null || content.isBlank()) {
                return null;
            }

            JsonNode answerNode = objectMapper.readTree(extractJsonBlock(content));
            AnswerDraft draft = new AnswerDraft();
            draft.answer = blankToNull(answerNode.path("answer").asText(""));
            draft.evidenceStatus = blankToNull(answerNode.path("evidenceStatus").asText(""));
            return draft;
        } catch (Exception e) {
            throw new RuntimeException("解析回答失败: " + e.getMessage(), e);
        }
    }

    private String buildThemeOverviewAnswer(AiSearchIntent intent,
                                            PhotoSearchExecution photoSearch,
                                            Map<String, Long> themeCounts) {
        String periodLabel = buildTimeRangeSummary(intent);
        if (photoSearch.totalMatched == 0) {
            return "检索结论：" + periodLabel + "未找到可统计的公开照片。";
        }
        if (themeCounts.isEmpty()) {
            return "检索结论：" + periodLabel + "共找到 " + photoSearch.totalMatched
                + " 张公开照片，但暂时难以仅根据相册名和标签归纳出明显主题。";
        }

        String summary = themeCounts.entrySet().stream()
            .limit(4)
            .map(entry -> entry.getKey() + "(" + entry.getValue() + "张)")
            .collect(Collectors.joining("、"));
        return "检索结论：" + periodLabel + "拍得较多的主题有：" + summary + "。统计主要基于相册名与照片标签，属于粗略归纳。";
    }

    private String buildLocationOverviewAnswer(AiSearchIntent intent,
                                               PhotoSearchExecution photoSearch,
                                               Map<String, Long> locationCounts) {
        String periodLabel = buildTimeRangeSummary(intent);
        if (photoSearch.totalMatched == 0) {
            return "检索结论：" + periodLabel + "未找到可统计地点的公开照片。";
        }
        if (locationCounts.isEmpty()) {
            return "检索结论：" + periodLabel + "共找到 " + photoSearch.totalMatched
                + " 张公开照片，但暂时难以仅根据相册名和路径稳定归纳出拍摄地点。";
        }

        String summary = locationCounts.entrySet().stream()
            .limit(4)
            .map(entry -> entry.getKey() + "(" + entry.getValue() + "张)")
            .collect(Collectors.joining("、"));
        return "检索结论：" + periodLabel + "相关内容主要拍摄于：" + summary + "。地点主要依据相册名与路径推断，属于粗略归纳。";
    }

    private String buildAlbumOverviewAnswer(AiSearchIntent intent,
                                            PhotoSearchExecution photoSearch,
                                            Map<Long, Long> albumCounts,
                                            List<AlbumDTO> albums) {
        String periodLabel = buildTimeRangeSummary(intent);
        if (photoSearch.totalMatched == 0 || albumCounts.isEmpty()) {
            return "检索结论：" + periodLabel + "未找到可统计的公开相册结果。";
        }

        Map<Long, String> albumNames = albums.stream()
            .filter(album -> album.getId() != null)
            .collect(Collectors.toMap(AlbumDTO::getId, AlbumDTO::getName, (left, right) -> left, LinkedHashMap::new));
        String summary = albumCounts.entrySet().stream()
            .limit(4)
            .map(entry -> nullToDefault(albumNames.get(entry.getKey()), "相册#" + entry.getKey()) + "(" + entry.getValue() + "张)")
            .collect(Collectors.joining("、"));
        return "检索结论：" + periodLabel + "拍得较多的相册有：" + summary + "。";
    }

    private String buildMonthOverviewAnswer(AiSearchIntent intent,
                                            PhotoSearchExecution photoSearch,
                                            Map<String, Long> monthCounts) {
        String periodLabel = buildTimeRangeSummary(intent);
        if (photoSearch.totalMatched == 0 || monthCounts.isEmpty()) {
            return "检索结论：" + periodLabel + "未找到可统计月份的公开照片。";
        }
        String summary = monthCounts.entrySet().stream()
            .limit(4)
            .map(entry -> entry.getKey() + "(" + entry.getValue() + "张)")
            .collect(Collectors.joining("、"));
        return "检索结论：" + periodLabel + "拍摄较集中的月份有：" + summary + "。";
    }

    private String buildCountOverviewAnswer(AiSearchIntent intent,
                                            PhotoSearchExecution photoSearch,
                                            Map<Long, Long> albumCounts) {
        String periodLabel = buildTimeRangeSummary(intent);
        if (photoSearch.totalMatched == 0) {
            return "检索结论：" + periodLabel + "未找到相关公开照片。";
        }
        int albumSize = albumCounts.size();
        return "检索结论：" + periodLabel + "共找到 " + photoSearch.totalMatched + " 张相关公开照片，分布在 "
            + albumSize + " 个相册中。";
    }

    private String buildDayOverviewAnswer(AiSearchIntent intent,
                                          PhotoSearchExecution photoSearch,
                                          Map<String, Long> dayCounts) {
        String periodLabel = buildTimeRangeSummary(intent);
        if (photoSearch.totalMatched == 0 || dayCounts.isEmpty()) {
            return "检索结论：" + periodLabel + "未找到可统计具体日期的公开照片。";
        }
        String summary = dayCounts.entrySet().stream()
            .limit(5)
            .map(entry -> entry.getKey() + "(" + entry.getValue() + "张)")
            .collect(Collectors.joining("、"));
        return "检索结论：" + periodLabel + "拍摄主要集中在：" + summary + "。";
    }

    private String buildTagOverviewAnswer(AiSearchIntent intent,
                                          PhotoSearchExecution photoSearch,
                                          Map<String, Long> tagCounts) {
        String periodLabel = buildTimeRangeSummary(intent);
        if (photoSearch.totalMatched == 0 || tagCounts.isEmpty()) {
            return "检索结论：" + periodLabel + "未找到可统计的高频标签。";
        }
        String summary = tagCounts.entrySet().stream()
            .limit(5)
            .map(entry -> entry.getKey() + "(" + entry.getValue() + "张)")
            .collect(Collectors.joining("、"));
        return "检索结论：" + periodLabel + "出现较多的标签有：" + summary + "。";
    }

    private String buildYearCompareAnswer(AiSearchIntent baseIntent,
                                          YearComparison comparison,
                                          PhotoSearchExecution leftSearch,
                                          PhotoSearchExecution rightSearch) {
        long leftCount = leftSearch.totalMatched;
        long rightCount = rightSearch.totalMatched;
        String subject = buildKeywordSummary(baseIntent);
        if (leftCount == 0 && rightCount == 0) {
            return "检索结论：" + comparison.leftYear + " 年和 " + comparison.rightYear + " 年都未找到相关公开照片。";
        }
        if (leftCount == rightCount) {
            return "检索结论：" + comparison.leftYear + " 年与 " + comparison.rightYear + " 年关于 " + subject
                + " 的公开照片数量相同，都是 " + leftCount + " 张。";
        }

        boolean leftMore = leftCount > rightCount;
        long more = leftMore ? leftCount : rightCount;
        long less = leftMore ? rightCount : leftCount;
        int moreYear = leftMore ? comparison.leftYear : comparison.rightYear;
        int lessYear = leftMore ? comparison.rightYear : comparison.leftYear;
        String ratioText = less == 0 ? "明显更多" : String.format(Locale.ROOT, "约 %.1f 倍", (double) more / (double) less);

        return "检索结论：" + comparison.leftYear + " 年找到 " + leftCount + " 张，"
            + comparison.rightYear + " 年找到 " + rightCount + " 张；"
            + moreYear + " 年关于 " + subject + " 的拍摄更多，相比 " + lessYear + " 年" + ratioText + "。";
    }

    private String buildTimeRangeSummary(AiSearchIntent intent) {
        String startDate = intent.getStartDate();
        String endDate = intent.getEndDate();
        if (isBlank(startDate) && isBlank(endDate)) {
            return "当前图库中";
        }
        if (!isBlank(startDate) && !isBlank(endDate)
            && startDate.endsWith("-01-01") && endDate.endsWith("-12-31")
            && startDate.substring(0, 4).equals(endDate.substring(0, 4))) {
            return startDate.substring(0, 4) + " 年";
        }
        return nullToDefault(startDate, "开始时间未知") + " 到 " + nullToDefault(endDate, "现在");
    }

    private YearComparison resolveYearComparison(String query) {
        int currentYear = LocalDate.now().getYear();
        if (query != null) {
            if (query.contains("今年") && query.contains("去年")) {
                return new YearComparison(currentYear, currentYear - 1);
            }
            if (query.contains("去年") && query.contains("前年")) {
                return new YearComparison(currentYear - 1, currentYear - 2);
            }
            if (query.contains("今年") && query.contains("前年")) {
                return new YearComparison(currentYear, currentYear - 2);
            }
            if (query.contains("去年")) {
                return new YearComparison(currentYear - 1, currentYear - 2);
            }
        }
        return new YearComparison(currentYear, currentYear - 1);
    }

    private void setYearRange(AiSearchIntent intent, int year) {
        String startDate = year + "-01-01";
        String endDate = year + "-12-31";
        intent.setStartDate(startDate);
        intent.setEndDate(endDate);
        AiSearchCondition dateCondition = findFirstPositiveCondition(intent, "date_range");
        if (dateCondition == null) {
            dateCondition = new AiSearchCondition();
            dateCondition.setType("date_range");
            intent.getMust().add(dateCondition);
        }
        dateCondition.setStartDate(startDate);
        dateCondition.setEndDate(endDate);
    }

    private void applyAnalysisTopicKeywords(AiSearchIntent intent,
                                            CandidateContext candidates,
                                            List<String> topicKeywords) {
        if (topicKeywords.isEmpty()) {
            return;
        }

        String joined = String.join("", topicKeywords);
        Set<String> topicTokens = generateTokens(joined);
        List<Long> tagIds = rankTagMatches(candidates, joined, topicTokens, 8);

        if (!tagIds.isEmpty()) {
            intent.setTagIds(new ArrayList<>(tagIds));
        }
        intent.setKeywords(new ArrayList<>(topicKeywords));
    }

    private List<String> extractAnalysisKeywords(String query) {
        String text = stripAnalysisNoise(query);
        if (text.isBlank()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (!text.contains(" ")) {
            String normalized = normalizeAnalysisKeywordCandidate(text);
            if (!normalized.isEmpty()) {
                keywords.add(normalized);
            }
        }
        for (String part : text.split(" ")) {
            String normalized = normalizeAnalysisKeywordCandidate(part);
            if (!normalized.isEmpty()) {
                keywords.add(normalized);
            }
        }
        return new ArrayList<>(keywords);
    }

    private List<AlbumDTO> fetchTopAlbumsForMatchedPhotos(List<Photo> photos, int limit) {
        if (photos == null || photos.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Long> albumCounts = photos.stream()
            .map(Photo::getAlbumId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.groupingBy(albumId -> albumId, LinkedHashMap::new, Collectors.counting()));

        return albumCounts.entrySet().stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .map(albumRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(album -> !Boolean.TRUE.equals(album.getIsHidden()))
            .map(album -> {
                try {
                    return albumService.getAlbumById(album.getId());
                } catch (Exception e) {
                    log.debug("获取主题分析相册失败, albumId={}: {}", album.getId(), e.getMessage());
                    return null;
                }
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    }

    private Map<String, Long> summarizeThemes(List<Photo> photos, List<AlbumDTO> albums) {
        if (photos == null || photos.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<String>> albumThemes = new HashMap<>();
        Map<String, Integer> albumThemeFrequency = new HashMap<>();
        if (albums != null) {
            for (AlbumDTO album : albums) {
                if (album != null && album.getId() != null) {
                    List<String> themes = extractAlbumThemeCandidates(album);
                    albumThemes.put(album.getId(), themes);
                    themes.forEach(theme -> albumThemeFrequency.merge(theme, 1, Integer::sum));
                }
            }
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        Set<String> tagThemes = new HashSet<>();
        for (Photo photo : photos) {
            if (photo.getTags() != null) {
                for (Tag tag : photo.getTags()) {
                    for (String theme : extractThemeCandidates(tag == null ? null : tag.getName())) {
                        counts.merge(theme, 1L, Long::sum);
                        tagThemes.add(theme);
                    }
                }
            }

            List<String> themes = albumThemes.get(photo.getAlbumId());
            if (themes == null || themes.isEmpty()) {
                themes = albumRepository.findById(photo.getAlbumId())
                    .map(this::extractAlbumThemeCandidates)
                    .orElse(Collections.emptyList());
                albumThemes.put(photo.getAlbumId(), themes);
                themes.forEach(theme -> albumThemeFrequency.merge(theme, 1, Integer::sum));
            }
            for (String theme : themes) {
                if (!tagThemes.contains(theme) && albumThemeFrequency.getOrDefault(theme, 0) < 2) {
                    continue;
                }
                counts.merge(theme, 2L, Long::sum);
            }
        }

        return counts.entrySet().stream()
            .filter(entry -> entry.getValue() >= 2)
            .sorted((left, right) -> {
                int byCount = Long.compare(right.getValue(), left.getValue());
                if (byCount != 0) {
                    return byCount;
                }
                return left.getKey().compareTo(right.getKey());
            })
            .limit(8)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private Map<String, Long> summarizeLocations(List<Photo> photos, List<AlbumDTO> albums) {
        if (photos == null || photos.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, List<String>> albumLocations = new HashMap<>();
        if (albums != null) {
            for (AlbumDTO album : albums) {
                if (album != null && album.getId() != null) {
                    albumLocations.put(album.getId(), extractAlbumLocationCandidates(album));
                }
            }
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Photo photo : photos) {
            List<String> locations = albumLocations.get(photo.getAlbumId());
            if (locations == null || locations.isEmpty()) {
                locations = albumRepository.findById(photo.getAlbumId())
                    .map(this::extractAlbumLocationCandidates)
                    .orElse(Collections.emptyList());
                albumLocations.put(photo.getAlbumId(), locations);
            }
            for (String location : locations) {
                counts.merge(location, 1L, Long::sum);
            }
        }

        return counts.entrySet().stream()
            .filter(entry -> entry.getValue() >= 2)
            .sorted((left, right) -> {
                int byCount = Long.compare(right.getValue(), left.getValue());
                if (byCount != 0) {
                    return byCount;
                }
                return left.getKey().compareTo(right.getKey());
            })
            .limit(8)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private Map<Long, Long> summarizeAlbumCounts(List<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return Collections.emptyMap();
        }
        return photos.stream()
            .map(Photo::getAlbumId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.groupingBy(albumId -> albumId, LinkedHashMap::new, Collectors.counting()))
            .entrySet()
            .stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private List<AlbumDTO> fetchAlbumsByCount(Map<Long, Long> albumCounts, int limit) {
        if (albumCounts == null || albumCounts.isEmpty()) {
            return Collections.emptyList();
        }

        return albumCounts.keySet().stream()
            .limit(limit)
            .map(albumRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(album -> !Boolean.TRUE.equals(album.getIsHidden()))
            .map(album -> {
                try {
                    return albumService.getAlbumById(album.getId());
                } catch (Exception e) {
                    log.debug("获取相册统计结果失败, albumId={}: {}", album.getId(), e.getMessage());
                    return null;
                }
            })
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
    }

    private Map<String, Long> summarizeMonthCounts(List<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return Collections.emptyMap();
        }

        return photos.stream()
            .map(Photo::getTakenAt)
            .filter(java.util.Objects::nonNull)
            .map(takenAt -> String.format("%d-%02d", takenAt.getYear(), takenAt.getMonthValue()))
            .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()))
            .entrySet()
            .stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .limit(8)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private Map<String, Long> summarizeDayCounts(List<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return Collections.emptyMap();
        }

        return photos.stream()
            .map(Photo::getTakenAt)
            .filter(java.util.Objects::nonNull)
            .map(takenAt -> takenAt.toLocalDate().toString())
            .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()))
            .entrySet()
            .stream()
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .limit(8)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private Map<String, Long> summarizeTagCounts(List<Photo> photos) {
        if (photos == null || photos.isEmpty()) {
            return Collections.emptyMap();
        }

        return photos.stream()
            .flatMap(photo -> photo.getTags() == null ? java.util.stream.Stream.<Tag>empty() : photo.getTags().stream())
            .map(Tag::getName)
            .filter(name -> !isBlank(name))
            .map(String::trim)
            .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()))
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue() >= 2)
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .limit(8)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private List<String> extractAlbumThemeCandidates(AlbumDTO album) {
        if (album == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        themes.addAll(extractThemeCandidates(album.getName()));
        themes.addAll(extractThemeCandidates(album.getDisplayTitle()));
        themes.addAll(extractPathThemeCandidates(album.getPath()));
        themes.addAll(extractPathThemeCandidates(album.getRelativePath()));
        return new ArrayList<>(themes);
    }

    private List<String> extractAlbumLocationCandidates(AlbumDTO album) {
        if (album == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> locations = new LinkedHashSet<>();
        locations.addAll(extractLocationCandidates(album.getName()));
        locations.addAll(extractLocationCandidates(album.getDisplayTitle()));
        locations.addAll(extractPathLocationCandidates(album.getPath()));
        locations.addAll(extractPathLocationCandidates(album.getRelativePath()));
        return new ArrayList<>(locations);
    }

    private List<String> extractAlbumThemeCandidates(Album album) {
        if (album == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        themes.addAll(extractThemeCandidates(album.getName()));
        themes.addAll(extractPathThemeCandidates(album.getPath()));
        return new ArrayList<>(themes);
    }

    private List<String> extractAlbumLocationCandidates(Album album) {
        if (album == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> locations = new LinkedHashSet<>();
        locations.addAll(extractLocationCandidates(album.getName()));
        locations.addAll(extractPathLocationCandidates(album.getPath()));
        return new ArrayList<>(locations);
    }

    private List<String> extractPathThemeCandidates(String path) {
        if (isBlank(path)) {
            return Collections.emptyList();
        }
        String normalized = path.replace('\\', '/');
        int markerIndex = normalized.indexOf("/data/photos/");
        if (markerIndex >= 0) {
            normalized = normalized.substring(markerIndex + "/data/photos/".length());
        }
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        for (String segment : normalized.split("/")) {
            themes.addAll(extractThemeCandidates(segment));
        }
        return new ArrayList<>(themes);
    }

    private List<String> extractPathLocationCandidates(String path) {
        if (isBlank(path)) {
            return Collections.emptyList();
        }
        String normalized = path.replace('\\', '/');
        int markerIndex = normalized.indexOf("/data/photos/");
        if (markerIndex >= 0) {
            normalized = normalized.substring(markerIndex + "/data/photos/".length());
        }
        LinkedHashSet<String> locations = new LinkedHashSet<>();
        for (String segment : normalized.split("/")) {
            locations.addAll(extractLocationCandidates(segment));
        }
        return new ArrayList<>(locations);
    }

    private List<String> extractThemeCandidates(String rawText) {
        if (isBlank(rawText)) {
            return Collections.emptyList();
        }

        String normalized = rawText
            .replaceAll("^\\d{4}[._-]\\d{1,2}[._-]\\d{1,2}\\s*", "")
            .replaceAll("[()（）\\[\\]【】,，.。_/-]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> themes = new LinkedHashSet<>();
        if (!normalized.contains(" ")) {
            addThemeCandidate(themes, normalized);
        }
        for (String part : normalized.split(" ")) {
            addThemeCandidate(themes, part);
        }
        return new ArrayList<>(themes);
    }

    private List<String> extractLocationCandidates(String rawText) {
        if (isBlank(rawText)) {
            return Collections.emptyList();
        }

        String normalized = rawText
            .replaceAll("^\\d{4}[._-]\\d{1,2}[._-]\\d{1,2}\\s*", "")
            .replaceAll("[()（）\\[\\]【】,，.。_/]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> locations = new LinkedHashSet<>();
        if (!normalized.contains(" ")) {
            addLocationCandidate(locations, normalized);
        }
        for (String part : normalized.split("[\\s-]+")) {
            addLocationCandidate(locations, part);
        }
        return new ArrayList<>(locations);
    }

    private void addThemeCandidate(Set<String> themes, String candidate) {
        String theme = normalizeThemeCandidate(candidate);
        if (!theme.isEmpty()) {
            themes.add(theme);
        }
    }

    private void addLocationCandidate(Set<String> locations, String candidate) {
        String location = normalizeLocationCandidate(candidate);
        if (!location.isEmpty()) {
            locations.add(location);
        }
    }

    private String normalizeAnalysisKeywordCandidate(String candidate) {
        if (candidate == null) {
            return "";
        }
        String keyword = candidate.trim();
        if (keyword.isEmpty()) {
            return "";
        }
        keyword = keyword.replaceAll("^(关于|有关|拍的|拍了|拍)", "");
        keyword = keyword.replaceAll("(拍得|拍过|拍)$", "");
        keyword = keyword.replaceAll("(照片|图片|相片|相册|合集|记录|主题|题材)$", "");
        keyword = keyword.trim();
        if (keyword.length() < 2 || keyword.length() > 12) {
            return "";
        }
        if (keyword.chars().allMatch(Character::isDigit)) {
            return "";
        }
        if (STOP_WORDS.contains(keyword) || "地点".equals(keyword) || "地方".equals(keyword) || "日期".equals(keyword)) {
            return "";
        }
        return keyword;
    }

    private String normalizeThemeCandidate(String candidate) {
        if (candidate == null) {
            return "";
        }
        String theme = candidate.trim();
        if (theme.isEmpty()) {
            return "";
        }
        theme = theme.replaceAll("^(关于|有关|拍的|拍了|拍)", "");
        theme = theme.replaceAll("(拍得|拍过|拍)$", "");
        theme = theme.replaceAll("(照片|图片|相片|相册|合集|记录|主题|题材)$", "");
        if (theme.length() > 2) {
            theme = theme.replaceAll("(之旅|旅行|游玩|随拍|日常|生活)$", "");
        }
        if (theme.length() > 2) {
            theme = theme.replaceAll("(季)$", "");
        }
        theme = theme.trim();
        if (theme.length() < 2 || theme.length() > 12) {
            return "";
        }
        if (theme.chars().allMatch(Character::isDigit)) {
            return "";
        }
        if (THEME_STOP_WORDS.contains(theme) || TECHNICAL_THEME_STOP_WORDS.contains(theme)) {
            return "";
        }
        String lower = theme.toLowerCase(Locale.ROOT);
        if (lower.contains("iso") || theme.contains("光圈") || theme.contains("分辨率") || theme.contains("构图")) {
            return "";
        }
        for (String suffix : LOCATION_HINT_SUFFIXES) {
            if (theme.endsWith(suffix)) {
                return "";
            }
        }
        if (theme.contains("杭州") || theme.contains("上海") || theme.contains("北京") || theme.contains("苏州")) {
            return "";
        }
        return theme;
    }

    private String normalizeLocationCandidate(String candidate) {
        if (candidate == null) {
            return "";
        }
        String location = candidate.trim();
        if (location.isEmpty()) {
            return "";
        }
        location = location.replaceAll("^(在|去|到)", "");
        location = location.replaceAll("(照片|图片|相片|相册|合集|记录)$", "");
        location = location.trim();
        if (location.length() < 2 || location.length() > 12) {
            return "";
        }
        if (LOCATION_STOP_WORDS.contains(location) || THEME_STOP_WORDS.contains(location)) {
            return "";
        }
        if (location.chars().allMatch(Character::isDigit)) {
            return "";
        }
        if (location.contains("樱") || location.contains("花")) {
            return "";
        }
        if (location.contains("ISO") || location.toLowerCase(Locale.ROOT).contains("iso")) {
            return "";
        }
        for (String suffix : LOCATION_HINT_SUFFIXES) {
            if (location.endsWith(suffix)) {
                return location;
            }
        }
        if (location.contains("杭州") || location.contains("上海") || location.contains("北京") || location.contains("苏州")) {
            return location;
        }
        return "";
    }

    private Map<Long, String> resolveAlbumNames(List<Photo> photos) {
        Set<Long> albumIds = photos.stream()
            .map(Photo::getAlbumId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        if (albumIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, String> albumNames = new HashMap<>();
        for (Long albumId : albumIds) {
            albumRepository.findById(albumId).ifPresent(album -> albumNames.put(albumId, album.getName()));
        }
        return albumNames;
    }

    private String invokeChatCompletion(String endpoint, String apiKey, Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.POST, entity, String.class);
        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
            throw new RuntimeException("GPT API调用失败: " + response.getStatusCode());
        }
        return response.getBody();
    }

    private String extractAssistantText(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JsonNode message = choices.get(0).get("message");
            if (message == null || message.get("content") == null) {
                return null;
            }
            return message.get("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("解析回答失败: " + e.getMessage(), e);
        }
    }

    private void fillMatchedNames(AiSearchResponse response, AiSearchIntent intent, CandidateContext candidates) {
        LinkedHashSet<Long> personIds = collectPositiveConditionIds(intent, "person");
        if (!personIds.isEmpty()) {
            String names = candidates.persons.stream()
                .filter(person -> personIds.contains(person.getId()))
                .map(PersonProfile::getName)
                .collect(Collectors.joining(", "));
            if (!names.isEmpty()) {
                response.setMatchedPersonName(names);
            }
        }

        LinkedHashSet<Long> tagIds = collectPositiveConditionIds(intent, "tag");
        if (!tagIds.isEmpty()) {
            response.setMatchedTagNames(candidates.tags.stream()
                .filter(tag -> tagIds.contains(tag.getId()))
                .map(Tag::getName)
                .collect(Collectors.toList()));
        }

        LinkedHashSet<Long> albumIds = filterAlbumIdsForDisplay(intent, collectPositiveConditionIds(intent, "album"));
        if (!albumIds.isEmpty()) {
            response.setMatchedAlbumNames(candidates.albums.stream()
                .filter(album -> albumIds.contains(album.getId()))
                .map(Album::getName)
                .collect(Collectors.toList()));
        }
    }

    private LinkedHashSet<Long> filterAlbumIdsForDisplay(AiSearchIntent intent, LinkedHashSet<Long> albumIds) {
        if (albumIds.isEmpty()) {
            return albumIds;
        }

        LocalDateTime startDate = parseDate(intent.getStartDate(), true);
        LocalDateTime endDate = parseDate(intent.getEndDate(), false);
        if (startDate == null && endDate == null) {
            AiSearchCondition dateCondition = findFirstPositiveCondition(intent, "date_range");
            if (dateCondition != null) {
                startDate = parseDate(dateCondition.getStartDate(), true);
                endDate = parseDate(dateCondition.getEndDate(), false);
            }
        }

        if (startDate == null && endDate == null) {
            return albumIds;
        }

        LinkedHashSet<Long> filtered = new LinkedHashSet<>();
        for (Long albumId : albumIds) {
            if (albumMatchesDateRange(albumId, startDate, endDate)) {
                filtered.add(albumId);
            }
        }
        return filtered;
    }

    private boolean albumMatchesDateRange(Long albumId, LocalDateTime startDate, LocalDateTime endDate) {
        if (photoRepository.existsVisiblePhotoInAlbumDuringRange(albumId, startDate, endDate)) {
            return true;
        }

        return albumRepository.findById(albumId)
            .map(Album::getName)
            .map(this::extractAlbumDate)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(albumDate -> isWithinRange(albumDate.atStartOfDay(), startDate, endDate))
            .orElse(false);
    }

    private Optional<LocalDate> extractAlbumDate(String albumName) {
        if (albumName == null || albumName.length() < 10) {
            return Optional.empty();
        }

        String datePrefix = albumName.substring(0, 10);
        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return Optional.of(LocalDate.parse(datePrefix, formatter));
            } catch (Exception ignored) {
                // ignore
            }
        }
        return Optional.empty();
    }

    private boolean isWithinRange(LocalDateTime value, LocalDateTime startDate, LocalDateTime endDate) {
        if (value == null) {
            return false;
        }
        if (startDate != null && value.isBefore(startDate)) {
            return false;
        }
        if (endDate != null && value.isAfter(endDate)) {
            return false;
        }
        return true;
    }

    private LinkedHashSet<Long> collectPositiveConditionIds(AiSearchIntent intent, String type) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (AiSearchCondition condition : safeList(intent.getMust())) {
            if (type.equals(normalizeType(condition.getType()))) {
                ids.addAll(safeLongList(condition.getIds()));
            }
        }
        for (AiSearchCondition condition : safeList(intent.getShould())) {
            if (type.equals(normalizeType(condition.getType()))) {
                ids.addAll(safeLongList(condition.getIds()));
            }
        }
        return ids;
    }

    private AiSearchCondition findFirstPositiveCondition(AiSearchIntent intent, String type) {
        for (AiSearchCondition condition : safeList(intent.getMust())) {
            if (type.equals(normalizeType(condition.getType()))) {
                return condition;
            }
        }
        for (AiSearchCondition condition : safeList(intent.getShould())) {
            if (type.equals(normalizeType(condition.getType()))) {
                return condition;
            }
        }
        return null;
    }

    private boolean hasPositiveConditionType(AiSearchIntent intent, String type) {
        return findFirstPositiveCondition(intent, type) != null;
    }

    private List<String> collectTextValues(AiSearchIntent intent, String type) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (AiSearchCondition condition : safeList(intent.getMust())) {
            if (type.equals(normalizeType(condition.getType()))) {
                values.addAll(safeTextValues(condition));
            }
        }
        for (AiSearchCondition condition : safeList(intent.getShould())) {
            if (type.equals(normalizeType(condition.getType()))) {
                values.addAll(safeTextValues(condition));
            }
        }
        return new ArrayList<>(values);
    }

    private List<Long> getEffectivePersonIds(AiSearchIntent intent) {
        if (intent.getPersonIds() != null && !intent.getPersonIds().isEmpty()) {
            return intent.getPersonIds();
        }
        if (intent.getPersonId() != null) {
            return List.of(intent.getPersonId());
        }
        return Collections.emptyList();
    }

    private Set<Long> intersect(Set<Long> a, Set<Long> b) {
        if (a == null) {
            return b == null ? null : new LinkedHashSet<>(b);
        }
        if (b == null) {
            return a;
        }
        a.retainAll(b);
        return a;
    }

    private void normalizeConditionTextValues(List<AiSearchCondition> conditions) {
        for (AiSearchCondition condition : safeList(conditions)) {
            String type = normalizeType(condition.getType());
            if (condition.getValue() != null) {
                condition.setValue(condition.getValue().trim());
            }
            if ("keyword".equals(type)) {
                condition.setValues(normalizeKeywordList(condition.getValues(), false));
                if (condition.getValue() != null && !condition.getValue().isBlank()) {
                    condition.setValue(normalizeKeywordList(List.of(condition.getValue()), false).stream().findFirst().orElse(null));
                }
            } else if ("filename_keyword".equals(type)) {
                condition.setValues(normalizeKeywordList(condition.getValues(), true));
                if (condition.getValue() != null) {
                    condition.setValue(condition.getValue().trim());
                }
            } else if (condition.getValues() != null) {
                condition.setValues(condition.getValues().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList()));
            }
        }
    }

    private List<String> normalizeKeywordList(List<String> values, boolean preserveShortTokens) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (values == null) {
            return new ArrayList<>();
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!preserveShortTokens && STOP_WORDS.contains(trimmed)) {
                continue;
            }
            if (!preserveShortTokens && trimmed.length() == 1) {
                continue;
            }
            normalized.add(trimmed);
        }
        return new ArrayList<>(normalized);
    }

    private AiSearchIntent cloneIntent(AiSearchIntent intent) {
        return objectMapper.convertValue(intent, AiSearchIntent.class);
    }

    private void retainConditionsByTypes(List<AiSearchCondition> conditions, Set<String> types) {
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        conditions.removeIf(condition -> !types.contains(normalizeType(condition.getType())));
    }

    private boolean removeConditionsByTypes(List<AiSearchCondition> conditions, Set<String> types) {
        if (conditions == null || conditions.isEmpty()) {
            return false;
        }
        return conditions.removeIf(condition -> types.contains(normalizeType(condition.getType())));
    }

    private void resetLegacyFields(AiSearchIntent intent) {
        intent.setPersonId(null);
        intent.setPersonIds(new ArrayList<>());
        intent.setTagIds(new ArrayList<>());
        intent.setAlbumIds(new ArrayList<>());
        intent.setStartDate(null);
        intent.setEndDate(null);
        intent.setCameraModel(null);
        intent.setLensModel(null);
        intent.setMinFocalLength(null);
        intent.setMaxFocalLength(null);
        intent.setMinAperture(null);
        intent.setMaxAperture(null);
        intent.setMinShutterSpeed(null);
        intent.setMaxShutterSpeed(null);
        intent.setMinIso(null);
        intent.setMaxIso(null);
        intent.setColorCategory(null);
        intent.setMinQualityScore(null);
        intent.setKeywords(new ArrayList<>());
        intent.setFilenameKeywords(new ArrayList<>());
    }

    private LocalDateTime parseDate(String dateStr, boolean isStart) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return isStart ? date.atStartOfDay() : date.atTime(23, 59, 59);
        } catch (Exception e) {
            log.warn("日期解析失败: {}", dateStr);
            return null;
        }
    }

    private String extractJsonBlock(String content) {
        String json = content.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('\n');
            int end = json.lastIndexOf("```");
            if (start >= 0 && end > start) {
                json = json.substring(start + 1, end).trim();
            }
        }
        return json;
    }

    private String getChatEndpoint(String apiUrl) {
        return apiUrl.endsWith("/") ? apiUrl + "chat/completions" : apiUrl + "/chat/completions";
    }

    private Map<String, Long> topTextCounts(List<String> values, int limit) {
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim)
            .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()))
            .entrySet()
            .stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .limit(limit)
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("s") && ("persons".equals(normalized) || "albums".equals(normalized) || "photos".equals(normalized))) {
            return normalized;
        }
        return normalized;
    }

    private List<AiSearchCondition> safeList(List<AiSearchCondition> conditions) {
        return conditions != null ? conditions : Collections.emptyList();
    }

    private List<Long> safeLongList(List<Long> ids) {
        return ids != null ? ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toList()) : Collections.emptyList();
    }

    private List<String> safeTextValues(AiSearchCondition condition) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (!isBlank(condition.getValue())) {
            values.add(condition.getValue().trim());
        }
        if (condition.getValues() != null) {
            condition.getValues().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .forEach(values::add);
        }
        return new ArrayList<>(values);
    }

    private String firstTextValue(AiSearchCondition condition) {
        List<String> values = safeTextValues(condition);
        return values.isEmpty() ? null : values.get(0);
    }

    private AiSearchCondition idsCondition(String type, List<Long> ids) {
        AiSearchCondition condition = new AiSearchCondition();
        condition.setType(type);
        condition.setIds(ids);
        return condition;
    }

    private AiSearchCondition valueCondition(String type, String value) {
        AiSearchCondition condition = new AiSearchCondition();
        condition.setType(type);
        condition.setValue(value);
        return condition;
    }

    private AiSearchCondition rangeCondition(String type, Double minValue, Double maxValue) {
        AiSearchCondition condition = new AiSearchCondition();
        condition.setType(type);
        condition.setMinValue(minValue);
        condition.setMaxValue(maxValue);
        return condition;
    }

    private boolean containsCue(String text, Set<String> cues) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String cue : cues) {
            if (text.contains(cue)) {
                return true;
            }
        }
        return false;
    }

    private List<Long> rankPersonMatches(CandidateContext candidates, String subject, Set<String> tokens, int limit) {
        return candidates.persons.stream()
            .map(person -> Map.entry(person.getId(), scoreCandidate(List.of(person.getName()), subject, tokens)))
            .filter(entry -> entry.getValue() > 0)
            .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private List<Long> rankTagMatches(CandidateContext candidates, String subject, Set<String> tokens, int limit) {
        return candidates.tags.stream()
            .map(tag -> Map.entry(tag.getId(), scoreCandidate(List.of(tag.getName()), subject, tokens)))
            .filter(entry -> entry.getValue() > 0)
            .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private List<Long> rankAlbumMatches(CandidateContext candidates, String subject, Set<String> tokens, int limit) {
        return candidates.albums.stream()
            .map(album -> Map.entry(album.getId(), scoreCandidate(List.of(album.getName(), album.getPath()), subject, tokens)))
            .filter(entry -> entry.getValue() > 1)
            .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
            .limit(limit)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    private int scoreCandidate(List<String> texts, String subject, Set<String> tokens) {
        int score = 0;
        for (String text : texts) {
            if (isBlank(text)) {
                continue;
            }
            if (looseContains(text, subject) || looseContains(subject, text)) {
                score += 4;
            }
            String normalizedText = normalizeLooseText(text);
            for (String token : tokens) {
                if (token.length() >= 2 && normalizedText.contains(token)) {
                    score += token.length() >= 3 ? 2 : 1;
                }
            }
        }
        return score;
    }

    private List<String> extractDirectKeywords(String subject,
                                               List<Long> personIds,
                                               List<Long> tagIds,
                                               List<Long> albumIds) {
        if (!personIds.isEmpty() || !tagIds.isEmpty() || !albumIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (subject.isBlank()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        keywords.add(subject);
        for (String token : generateTokens(subject)) {
            if (token.length() >= 2 && token.length() <= 4) {
                keywords.add(token);
            }
        }
        return keywords.stream().limit(3).collect(Collectors.toList());
    }

    private String stripDirectQueryNoise(String query) {
        if (query == null) {
            return "";
        }
        String result = query
            .replace("去年", " ")
            .replace("今年", " ")
            .replace("前年", " ")
            .replace("一下", " ")
            .replace("看看", " ")
            .replace("搜索", " ")
            .replace("搜", " ")
            .replace("查找", " ")
            .replace("照片", " ")
            .replace("图片", " ")
            .replace("相片", " ")
            .replace("的", " ");
        return normalizeLooseText(result);
    }

    private String normalizeSemanticQuery(String query) {
        if (query == null) {
            return "";
        }
        String normalized = query;
        normalized = normalized.replace("上一年", "去年");
        normalized = normalized.replace("上1年", "去年");
        normalized = normalized.replace("上年", "去年");
        normalized = normalized.replace("上一年度", "去年");
        normalized = normalized.replace("上一整年", "去年");
        normalized = normalized.replace("本年", "今年");
        normalized = normalized.replace("这一年", "今年");
        normalized = normalized.replace("上上一年", "前年");
        normalized = normalized.replace("上上年", "前年");
        normalized = normalized.replace("哪儿", "哪里");
        normalized = normalized.replace("在哪儿", "哪里");
        normalized = normalized.replace("在什么地方", "哪里");
        normalized = normalized.replace("在什么地点", "哪里");
        normalized = normalized.replace("什么地方", "哪里");
        normalized = normalized.replace("什么地点", "哪里");
        normalized = normalized.replace("哪些地方", "哪些地点");
        normalized = normalized.replace("哪个地方", "哪个地点");
        normalized = normalized.replace("拍摄", "拍");
        normalized = normalized.replace("月份", "月");
        normalized = normalized.replace("最常", "最多");
        normalized = normalized.replace("最频繁", "最多");
        normalized = normalized.replaceAll("(?<!比)较多", "比较多");
        normalized = normalized.replace("夜晚的樱花", "夜樱");
        normalized = normalized.replace("晚上的樱花", "夜樱");
        normalized = normalized.replace("夜间的樱花", "夜樱");
        normalized = normalized.replace("夜里的樱花", "夜樱");
        normalized = normalized.replace("夜晚樱花", "夜樱");
        normalized = normalized.replace("晚上樱花", "夜樱");
        normalized = normalized.replace("夜间樱花", "夜樱");
        normalized = normalized.replace("夜里樱花", "夜樱");
        normalized = normalized.replace("夜晚拍的樱花", "夜樱");
        normalized = normalized.replace("晚上拍的樱花", "夜樱");
        return normalized.trim();
    }

    private String stripComparisonNoise(String query) {
        if (query == null) {
            return "";
        }
        String result = query
            .replace("去年和前年相比", " ")
            .replace("今年和去年相比", " ")
            .replace("和前年相比", " ")
            .replace("和去年相比", " ")
            .replace("相比", " ")
            .replace("对比", " ")
            .replace("比较", " ")
            .replace("更多还是更少", " ")
            .replace("更少还是更多", " ")
            .replace("更多还是少", " ");
        return stripAnalysisNoise(result);
    }

    private String stripAnalysisNoise(String query) {
        if (query == null) {
            return "";
        }
        String result = normalizeSemanticQuery(query)
            .replace("去年", " ")
            .replace("今年", " ")
            .replace("前年", " ")
            .replace("主要集中在", " ")
            .replace("集中在", " ")
            .replace("主要集中", " ")
            .replace("主要", " ")
            .replace("集中", " ")
            .replace("哪些地点", " ")
            .replace("哪个地点", " ")
            .replace("地点", " ")
            .replace("地方", " ")
            .replace("哪个相册拍得最多", " ")
            .replace("哪些相册拍得最多", " ")
            .replace("哪个相册最多", " ")
            .replace("哪些相册最多", " ")
            .replace("哪个月拍", " ")
            .replace("哪个月", " ")
            .replace("几月", " ")
            .replace("什么时候", " ")
            .replace("拍了多少张", " ")
            .replace("拍了多少次", " ")
            .replace("多少张", " ")
            .replace("多少次", " ")
            .replace("几张", " ")
            .replace("几次", " ")
            .replace("哪几天", " ")
            .replace("哪些天", " ")
            .replace("哪一天", " ")
            .replace("哪天", " ")
            .replace("日期", " ")
            .replace("几号", " ")
            .replace("哪里拍过", " ")
            .replace("在哪里拍过", " ")
            .replace("哪里拍的", " ")
            .replace("在哪里拍的", " ")
            .replace("哪儿拍过", " ")
            .replace("在哪拍过", " ")
            .replace("拍得最多", " ")
            .replace("最多", " ")
            .replace("比较多", " ")
            .replace("更多还是更少", " ")
            .replace("更少还是更多", " ")
            .replace("相比", " ")
            .replace("对比", " ")
            .replace("标签", " ")
            .replace("tag", " ")
            .replace("tags", " ")
            .replace("拍的什么主题比较多", " ")
            .replace("拍了什么主题比较多", " ")
            .replace("什么主题比较多", " ")
            .replace("什么题材比较多", " ")
            .replace("什么", " ")
            .replace("主题", " ")
            .replace("题材", " ")
            .replace("照片", " ")
            .replace("图片", " ")
            .replace("相片", " ")
            .replace("相册", " ")
            .replace("拍过", " ")
            .replace("拍的", " ")
            .replace("拍了", " ")
            .replace("拍得", " ");
        return normalizeSpacedText(result);
    }

    private boolean looseContains(String source, String target) {
        if (source == null || target == null) {
            return false;
        }
        String normalizedSource = normalizeLooseText(source);
        String normalizedTarget = normalizeLooseText(target);
        return !normalizedSource.isBlank()
            && !normalizedTarget.isBlank()
            && normalizedSource.contains(normalizedTarget);
    }

    private String normalizeLooseText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c > 127) {
                cleaned.append(Character.toLowerCase(c));
            }
        }
        return cleaned.toString().trim();
    }

    private String normalizeSpacedText(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        boolean lastWasSpace = false;
        for (char c : value.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c > 127) {
                cleaned.append(Character.toLowerCase(c));
                lastWasSpace = false;
            } else if (!lastWasSpace) {
                cleaned.append(' ');
                lastWasSpace = true;
            }
        }
        return cleaned.toString().trim().replaceAll("\\s+", " ");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Double toDouble(Integer value) {
        return value == null ? null : value.doubleValue();
    }

    private Integer toInteger(Double value) {
        return value == null ? null : value.intValue();
    }

    private int nullSafeCount(Number number) {
        return number == null ? 0 : number.intValue();
    }

    private String nullToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static class CandidateContext {
        final List<PersonProfile> persons;
        final List<Tag> tags;
        final List<Album> albums;
        final List<String> cameraModels;
        final List<String> lensModels;

        CandidateContext(List<PersonProfile> persons,
                         List<Tag> tags,
                         List<Album> albums,
                         List<String> cameraModels,
                         List<String> lensModels) {
            this.persons = persons;
            this.tags = tags;
            this.albums = albums;
            this.cameraModels = cameraModels;
            this.lensModels = lensModels;
        }
    }

    static class PhotoSearchExecution {
        final List<PhotoDTO> pagedPhotoDtos;
        final List<Photo> allMatchedPhotos;
        final long totalMatched;
        final boolean relaxed;
        final String relaxedReason;

        PhotoSearchExecution(List<PhotoDTO> pagedPhotoDtos, List<Photo> allMatchedPhotos, long totalMatched) {
            this(pagedPhotoDtos, allMatchedPhotos, totalMatched, false, null);
        }

        PhotoSearchExecution(List<PhotoDTO> pagedPhotoDtos,
                             List<Photo> allMatchedPhotos,
                             long totalMatched,
                             boolean relaxed,
                             String relaxedReason) {
            this.pagedPhotoDtos = pagedPhotoDtos;
            this.allMatchedPhotos = allMatchedPhotos;
            this.totalMatched = totalMatched;
            this.relaxed = relaxed;
            this.relaxedReason = relaxedReason;
        }

        PhotoSearchExecution withRelaxed(String reason) {
            return new PhotoSearchExecution(pagedPhotoDtos, allMatchedPhotos, totalMatched, true, reason);
        }

        static PhotoSearchExecution empty() {
            return new PhotoSearchExecution(Collections.emptyList(), Collections.emptyList(), 0);
        }
    }

    static class RelaxationPlan {
        final boolean dropKeywordConditions;
        final boolean dropDateConditions;
        final boolean dropAlbumConditions;
        final String reason;

        RelaxationPlan(boolean dropKeywordConditions,
                       boolean dropDateConditions,
                       boolean dropAlbumConditions,
                       String reason) {
            this.dropKeywordConditions = dropKeywordConditions;
            this.dropDateConditions = dropDateConditions;
            this.dropAlbumConditions = dropAlbumConditions;
            this.reason = reason;
        }
    }

    static class AnswerDraft {
        String answer;
        String evidenceStatus;
    }

    static class AnalysisRouting {
        final String type;
        final String resolvedQuery;
        final boolean usedAi;

        AnalysisRouting(String type, String resolvedQuery, boolean usedAi) {
            this.type = type;
            this.resolvedQuery = resolvedQuery;
            this.usedAi = usedAi;
        }

        boolean isResolved() {
            return type != null && !type.isBlank();
        }

        static AnalysisRouting none() {
            return new AnalysisRouting(null, null, false);
        }

        static AnalysisRouting aiAttempted() {
            return new AnalysisRouting(null, null, true);
        }
    }

    static class YearComparison {
        final int leftYear;
        final int rightYear;

        YearComparison(int leftYear, int rightYear) {
            this.leftYear = leftYear;
            this.rightYear = rightYear;
        }
    }
}
