package com.photoexhibition.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    public AiSearchResponse search(String query, int page, int size) {
        log.info("AI搜索开始, query={}, page={}, size={}", query, page, size);

        try {
            Set<String> tokens = generateTokens(query);
            log.info("分词结果: {}", tokens);

            CandidateContext candidates = preRetrieve(tokens);
            log.info("预检索候选: persons={}, tags={}, albums={}",
                candidates.persons.size(), candidates.tags.size(), candidates.albums.size());

            AiSearchIntent intent = callGpt(query, candidates);
            normalizeIntent(query, intent, true);
            log.info("GPT解析结果: {}", intent);
            return buildSearchResponse(query, intent, candidates, page, size);
        } catch (Exception e) {
            log.error("AI搜索失败: {}", e.getMessage(), e);
            AiSearchResponse response = new AiSearchResponse();
            response.setAiSearchEnabled(true);
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

        if (intent == null) {
            return search(query, page, size);
        }

        try {
            Set<String> tokens = generateTokens(query == null ? "" : query);
            CandidateContext candidates = preRetrieve(tokens);
            AiSearchIntent adjustedIntent = suggestionAction == null
                ? cloneIntent(intent)
                : applySuggestionAction(intent, suggestionAction);
            normalizeIntent(query, adjustedIntent, false);
            return buildSearchResponse(query, adjustedIntent, candidates, page, size);
        } catch (Exception e) {
            log.error("执行AI搜索建议失败: {}", e.getMessage(), e);
            AiSearchResponse response = new AiSearchResponse();
            response.setAiSearchEnabled(true);
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
                                                 int size) {
        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);
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

    private AiSearchIntent callGpt(String query, CandidateContext candidates) {
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
        String systemPrompt = buildSystemPrompt(candidates);

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

    private String buildSystemPrompt(CandidateContext candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是照片搜索规划助手。根据用户的单轮自然语言输入和数据库候选项，输出结构化JSON，供后端执行检索。\n\n");

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
            return objectMapper.readValue(json, AiSearchIntent.class);
        } catch (Exception e) {
            log.error("解析GPT响应失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析AI搜索结果失败: " + e.getMessage());
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

        intent.setIncludeHidden(false);
        if (intent.getNeedAnswer() == null) {
            intent.setNeedAnswer(false);
        }
        if (intent.getResultTypes() == null) {
            intent.setResultTypes(new ArrayList<>());
        }
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
        requestBody.put("messages", List.of(
            Map.of(
                "role", "system",
                "content", "你是图库检索结果解读助手。只能根据提供的检索结果摘要作答，不能假装看到了图片像素内容。请使用简洁的“检索结论”口吻，不要自称，不要说“我认为/你可以”。若证据不足，请直接说明“图库中未找到足够结果”。回答控制在1到2句中文。"
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
            String answer = extractAssistantText(responseBody);
            if (answer != null && !answer.isBlank()) {
                return answer.trim();
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
}
