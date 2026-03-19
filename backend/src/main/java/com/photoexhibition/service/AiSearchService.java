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
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
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

        AiSearchResponse response = new AiSearchResponse();
        response.setAiSearchEnabled(true);

        try {
            // Phase 0: 分词 + 预检索候选项
            Set<String> tokens = generateTokens(query);
            log.info("分词结果: {}", tokens);

            CandidateContext candidates = preRetrieve(tokens);
            log.info("预检索候选: persons={}, tags={}, albums={}",
                candidates.persons.size(), candidates.tags.size(), candidates.albums.size());

            // Phase 1: 调用 GPT 解析意图
            AiSearchIntent intent = callGpt(query, candidates);
            log.info("GPT解析结果: {}", intent);

            response.setParsedIntent(intent);
            response.setExplanation(intent.getExplanation());

            // 填充匹配的名称信息
            fillMatchedNames(response, intent, candidates);

            // 确定返回的结果类型
            List<String> resultTypes = intent.getResultTypes();
            if (resultTypes == null || resultTypes.isEmpty()) {
                resultTypes = List.of("photos");
            }

            // Phase 2: 组合查询照片
            if (resultTypes.contains("photos")) {
                List<PhotoDTO> photos = executeCombinedQuery(intent, page, size);
                response.setPhotos(photos);
                response.setTotalElements(photos.size());
                log.info("AI搜索照片结果: {}张", photos.size());
            } else {
                response.setPhotos(Collections.emptyList());
                response.setTotalElements(0);
            }

            // 获取相册结果
            if (resultTypes.contains("albums")) {
                List<AlbumDTO> albumResults = fetchAlbumResults(intent, candidates);
                response.setAlbums(albumResults);
                log.info("AI搜索相册结果: {}个", albumResults.size());
            } else {
                response.setAlbums(Collections.emptyList());
            }

            // 获取人物结果
            if (resultTypes.contains("persons")) {
                List<PersonSummaryDTO> personResults = fetchPersonResults(intent, candidates);
                response.setPersons(personResults);
                log.info("AI搜索人物结果: {}个", personResults.size());
            } else {
                response.setPersons(Collections.emptyList());
            }

        } catch (Exception e) {
            log.error("AI搜索失败: {}", e.getMessage(), e);
            response.setExplanation("AI搜索出错: " + e.getMessage());
            response.setPhotos(Collections.emptyList());
            response.setAlbums(Collections.emptyList());
            response.setPersons(Collections.emptyList());
        }

        return response;
    }

    // ===== 获取相册和人物结果 =====

    private List<AlbumDTO> fetchAlbumResults(AiSearchIntent intent, CandidateContext candidates) {
        List<AlbumDTO> results = new ArrayList<>();
        if (intent.getAlbumIds() == null || intent.getAlbumIds().isEmpty()) {
            return results;
        }
        for (Long albumId : intent.getAlbumIds()) {
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

    private List<PersonSummaryDTO> fetchPersonResults(AiSearchIntent intent, CandidateContext candidates) {
        List<PersonSummaryDTO> results = new ArrayList<>();
        List<Long> personIds = getEffectivePersonIds(intent);
        if (personIds.isEmpty()) {
            return results;
        }
        for (Long personId : personIds) {
            try {
                Optional<PersonProfile> opt = personProfileRepository.findById(personId);
                if (opt.isPresent()) {
                    PersonProfile person = opt.get();
                    // 不含隐藏时跳过隐藏人物
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

    // ===== Phase 0: 分词 + 预检索 =====

    Set<String> generateTokens(String query) {
        // 去除停用词和标点
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
            if (word.isEmpty()) continue;

            // 去停用词（单字或完整匹配）
            if (STOP_WORDS.contains(word)) continue;

            // 保留完整词
            if (word.length() >= 2) {
                tokens.add(word);
            }

            // 滑动窗口生成 2-char 和 3-char 子串
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
            if (token.length() < 2) continue;

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
            } catch (Exception e) {
                log.debug("搜索相册失败, token={}: {}", token, e.getMessage());
            }
        }

        // 相机/镜头数量少，全量给
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

    // ===== Phase 1: 调用 GPT =====

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

        String systemPrompt = buildSystemPrompt(candidates);
        String userPrompt = query;

        // 构建请求体
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("temperature", 0.1);
        requestBody.put("response_format", Map.of("type", "json_object"));

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userPrompt));
        requestBody.put("messages", messages);

        // 调用 API
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        String endpoint = apiUrl.endsWith("/") ? apiUrl + "chat/completions" : apiUrl + "/chat/completions";

        log.info("调用GPT API: url={}, model={}", endpoint, model);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> gptResponse = restTemplate.exchange(
            endpoint, HttpMethod.POST, entity, String.class
        );

        if (gptResponse.getStatusCode() != HttpStatus.OK || gptResponse.getBody() == null) {
            throw new RuntimeException("GPT API调用失败: " + gptResponse.getStatusCode());
        }

        // 解析响应
        return parseGptResponse(gptResponse.getBody());
    }

    private String buildSystemPrompt(CandidateContext candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是照片搜索助手。根据用户的自然语言查询和数据库中的候选项，生成精确的搜索条件JSON。\n\n");

        // 候选人物
        if (!candidates.persons.isEmpty()) {
            sb.append("## 匹配到的人物\n");
            for (PersonProfile p : candidates.persons) {
                sb.append("- id:").append(p.getId()).append(" \"").append(p.getName()).append("\"\n");
            }
            sb.append("\n");
        }

        // 候选标签
        if (!candidates.tags.isEmpty()) {
            sb.append("## 匹配到的标签\n");
            for (Tag t : candidates.tags) {
                sb.append("- id:").append(t.getId()).append(" \"").append(t.getName()).append("\"\n");
            }
            sb.append("\n");
        }

        // 候选相册
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

        // 相机/镜头
        if (!candidates.cameraModels.isEmpty()) {
            sb.append("## 可用相机型号\n");
            sb.append(String.join(", ", candidates.cameraModels)).append("\n\n");
        }
        if (!candidates.lensModels.isEmpty()) {
            sb.append("## 可用镜头型号\n");
            sb.append(String.join(", ", candidates.lensModels)).append("\n\n");
        }

        sb.append("## 色彩分类\nRED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, PINK, BROWN, GRAY, BLACK, WHITE\n\n");

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
        sb.append("  \"colorCategory\": null,\n");
        sb.append("  \"minQualityScore\": null,\n");
        sb.append("  \"keywords\": [],\n");
        sb.append("  \"filenameKeywords\": [],\n");
        sb.append("  \"resultTypes\": [\"photos\"],\n");
        sb.append("  \"includeHidden\": false,\n");
        sb.append("  \"explanation\": \"对搜索条件的中文描述\"\n");
        sb.append("}\n");
        sb.append("```\n\n");

        sb.append("## 规则\n");
        int currentYear = LocalDate.now().getYear();
        sb.append("1. 日期映射：\"去年\"=").append((currentYear - 1)).append("年, \"前年\"=").append((currentYear - 2)).append("年, \"今年\"=").append(currentYear).append("年\n");
        sb.append("2. 人物名语义匹配：用户说\"某某\"，候选有\"王某某\"，应匹配\n");
        sb.append("3. 标签做语义关联：用户说\"白天\"，候选中\"蓝天\"\"晴天\"都是白天场景，应选中\n");
        sb.append("4. 只使用候选列表中存在的ID，不要编造\n");
        sb.append("5. 无法匹配到候选的关键词放入keywords数组\n");
        sb.append("6. \"长焦\"→minFocalLength:85, \"广角\"→maxFocalLength:35\n");
        sb.append("7. \"虚化/大光圈\"→maxAperture:2.8\n");
        sb.append("8. \"暖色\"→colorCategory:\"ORANGE\", \"冷色\"→\"BLUE\"\n");
        sb.append("9. 日期格式: yyyy-MM-dd\n");
        sb.append("10. 只返回JSON，不要解释\n");
        sb.append("11. resultTypes 决定返回哪些结果类型，可包含 \"albums\"、\"persons\"、\"photos\"。默认 [\"photos\"]\n");
        sb.append("12. 当查询提到人物名时，将 \"persons\" 加入 resultTypes，同时设置 personIds 用于筛选照片\n");
        sb.append("13. 当查询提到相册名/地点名时，将 \"albums\" 加入 resultTypes，同时设置 albumIds\n");
        sb.append("14. 当用户用\"或\"连接多个实体时，各自独立返回对应类型。如\"某某或者蓝天\"→返回人物某某+蓝天标签的照片，resultTypes含persons和photos\n");
        sb.append("15. \"包括隐藏\"\"包含隐藏的\"\"隐藏的也要\"\"包括隐藏的\" → includeHidden: true。默认false\n");
        sb.append("16. 文件名模式（如IMG_、DSC_、_MG_开头或含.jpg/.png/.cr2/.arw等扩展名的字符串）放入 filenameKeywords\n");
        sb.append("17. 优先使用 personIds（数组）而不是 personId（单值）。personIds 支持多人物搜索\n");
        sb.append("18. 当用户搜索相机品牌（如\"佳能\"\"索尼\"\"尼康\"），将匹配的相机型号设入 cameraModel。搜索镜头焦段（如\"35mm\"\"50mm\"）时设置对应的 focalLength 范围\n");

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

            // 尝试提取JSON（可能被包裹在```json```中）
            String json = content.trim();
            if (json.startsWith("```")) {
                int start = json.indexOf('\n');
                int end = json.lastIndexOf("```");
                if (start >= 0 && end > start) {
                    json = json.substring(start + 1, end).trim();
                }
            }

            return objectMapper.readValue(json, AiSearchIntent.class);
        } catch (Exception e) {
            log.error("解析GPT响应失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析AI搜索结果失败: " + e.getMessage());
        }
    }

    // ===== Phase 2: 组合查询 =====

    private List<PhotoDTO> executeCombinedQuery(AiSearchIntent intent, int page, int size) {
        Set<Long> candidateIds = null;
        boolean includeHidden = intent.isIncludeHidden();

        // 1. 按人物筛选（多人物做 union）
        List<Long> personIds = getEffectivePersonIds(intent);
        if (!personIds.isEmpty()) {
            Set<Long> personPhotoIds = new HashSet<>();
            for (Long pid : personIds) {
                Page<Photo> personPhotos = photoRepository.findByPersonId(
                    pid, PageRequest.of(0, 10000)
                );
                personPhotos.getContent().forEach(p -> personPhotoIds.add(p.getId()));
            }
            candidateIds = intersect(candidateIds, personPhotoIds);
            log.info("人物筛选后: {}张", candidateIds != null ? candidateIds.size() : "全部");
        }

        // 2. 按标签筛选
        if (intent.getTagIds() != null && !intent.getTagIds().isEmpty()) {
            Page<Photo> tagPhotos = photoRepository.findByTagIds(
                intent.getTagIds(), PageRequest.of(0, 10000)
            );
            Set<Long> ids = tagPhotos.getContent().stream()
                .map(Photo::getId).collect(Collectors.toSet());
            candidateIds = intersect(candidateIds, ids);
            log.info("标签筛选后: {}张", candidateIds != null ? candidateIds.size() : "全部");
        }

        // 3. 按相册筛选
        if (intent.getAlbumIds() != null && !intent.getAlbumIds().isEmpty()) {
            Page<Photo> albumPhotos = includeHidden
                ? photoRepository.findByAlbumIdsIncludeHidden(intent.getAlbumIds(), PageRequest.of(0, 10000))
                : photoRepository.findByAlbumIds(intent.getAlbumIds(), PageRequest.of(0, 10000));
            Set<Long> ids = albumPhotos.getContent().stream()
                .map(Photo::getId).collect(Collectors.toSet());
            candidateIds = intersect(candidateIds, ids);
            log.info("相册筛选后: {}张", candidateIds != null ? candidateIds.size() : "全部");
        }

        // 4. 文件名搜索
        if (intent.getFilenameKeywords() != null && !intent.getFilenameKeywords().isEmpty()) {
            Set<Long> filenameIds = new HashSet<>();
            for (String fn : intent.getFilenameKeywords()) {
                if (fn == null || fn.trim().isEmpty()) continue;
                List<Photo> results = includeHidden
                    ? photoRepository.searchByFilenameIncludeHidden(fn.trim())
                    : photoRepository.searchByFilename(fn.trim());
                results.forEach(p -> filenameIds.add(p.getId()));
            }
            if (!filenameIds.isEmpty()) {
                candidateIds = intersect(candidateIds, filenameIds);
                log.info("文件名筛选后: {}张", candidateIds != null ? candidateIds.size() : "全部");
            }
        }

        // 5. 关键词模糊搜索（intersect 到候选）
        if (intent.getKeywords() != null && !intent.getKeywords().isEmpty()) {
            Set<Long> keywordIds = new HashSet<>();
            for (String kw : intent.getKeywords()) {
                if (kw == null || kw.trim().isEmpty()) continue;
                // 搜索文件名
                List<Photo> fileResults = includeHidden
                    ? photoRepository.searchByFilenameIncludeHidden(kw.trim())
                    : photoRepository.searchByFilename(kw.trim());
                fileResults.forEach(p -> keywordIds.add(p.getId()));
                // 搜索相册名
                List<Album> albumResults = albumRepository.searchByName(kw.trim());
                for (Album a : albumResults) {
                    Page<Photo> aPhotos = includeHidden
                        ? photoRepository.findByAlbumIdsIncludeHidden(List.of(a.getId()), PageRequest.of(0, 10000))
                        : photoRepository.findByAlbumIds(List.of(a.getId()), PageRequest.of(0, 10000));
                    aPhotos.getContent().forEach(p -> keywordIds.add(p.getId()));
                }
            }
            if (!keywordIds.isEmpty()) {
                candidateIds = intersect(candidateIds, keywordIds);
                log.info("关键词筛选后: {}张", candidateIds != null ? candidateIds.size() : "全部");
            }
        }

        // 6. 应用 EXIF/日期/色彩条件
        boolean hasExifFilters = hasExifOrDateFilters(intent);

        List<Photo> resultPhotos;

        if (candidateIds != null && candidateIds.isEmpty()) {
            // 前面的交集已经为空
            return Collections.emptyList();
        }

        if (candidateIds != null) {
            if (hasExifFilters) {
                // 先加载候选照片，再在内存中过滤 EXIF 条件
                List<Photo> candidates = includeHidden
                    ? photoRepository.findAllByIdInIncludeHidden(candidateIds)
                    : photoRepository.findAllByIdIn(candidateIds);
                resultPhotos = filterByExifInMemory(candidates, intent);
            } else {
                // 无 EXIF 条件，直接按 ID 查询
                resultPhotos = includeHidden
                    ? photoRepository.findAllByIdInIncludeHidden(candidateIds)
                    : photoRepository.findAllByIdIn(candidateIds);
            }
        } else {
            // 没有任何前置筛选，只有 EXIF 条件
            if (hasExifFilters) {
                LocalDateTime startDate = parseDate(intent.getStartDate(), true);
                LocalDateTime endDate = parseDate(intent.getEndDate(), false);
                Page<Photo> exifPhotos = photoRepository.findByExifFilters(
                    blankToNull(intent.getCameraModel()),
                    blankToNull(intent.getLensModel()),
                    intent.getMinAperture(), intent.getMaxAperture(),
                    intent.getMinFocalLength(), intent.getMaxFocalLength(),
                    intent.getMinShutterSpeed(), intent.getMaxShutterSpeed(),
                    intent.getMinIso(), intent.getMaxIso(),
                    blankToNull(intent.getColorCategory()),
                    intent.getMinQualityScore(),
                    startDate, endDate,
                    List.of(-1L),
                    PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "takenAt"))
                );
                resultPhotos = exifPhotos.getContent();
            } else {
                // 没有任何条件，返回空
                return Collections.emptyList();
            }
        }

        // 排序：按拍摄时间倒序
        resultPhotos.sort((a, b) -> {
            if (a.getTakenAt() == null && b.getTakenAt() == null) return 0;
            if (a.getTakenAt() == null) return 1;
            if (b.getTakenAt() == null) return -1;
            return b.getTakenAt().compareTo(a.getTakenAt());
        });

        // 手动分页
        int start = page * size;
        int end = Math.min(start + size, resultPhotos.size());
        if (start >= resultPhotos.size()) {
            return Collections.emptyList();
        }
        List<Photo> pagedPhotos = resultPhotos.subList(start, end);

        // 转换为 DTO
        return pagedPhotos.stream()
            .map(photoService::convertToDTO)
            .collect(Collectors.toList());
    }

    private List<Photo> filterByExifInMemory(List<Photo> photos, AiSearchIntent intent) {
        return photos.stream().filter(p -> {
            if (intent.getCameraModel() != null && !intent.getCameraModel().isEmpty()) {
                if (p.getCameraModel() == null || !p.getCameraModel().equals(intent.getCameraModel())) return false;
            }
            if (intent.getLensModel() != null && !intent.getLensModel().isEmpty()) {
                if (p.getLensModel() == null || !p.getLensModel().equals(intent.getLensModel())) return false;
            }
            if (intent.getMinAperture() != null) {
                if (p.getApertureValue() == null || p.getApertureValue() < intent.getMinAperture()) return false;
            }
            if (intent.getMaxAperture() != null) {
                if (p.getApertureValue() == null || p.getApertureValue() > intent.getMaxAperture()) return false;
            }
            if (intent.getMinFocalLength() != null) {
                if (p.getFocalLengthMm() == null || p.getFocalLengthMm() < intent.getMinFocalLength()) return false;
            }
            if (intent.getMaxFocalLength() != null) {
                if (p.getFocalLengthMm() == null || p.getFocalLengthMm() > intent.getMaxFocalLength()) return false;
            }
            if (intent.getColorCategory() != null && !intent.getColorCategory().isEmpty()) {
                if (p.getColorCategory() == null || !p.getColorCategory().equals(intent.getColorCategory())) return false;
            }
            if (intent.getMinQualityScore() != null) {
                if (p.getQualityScore() == null || p.getQualityScore() < intent.getMinQualityScore()) return false;
            }
            // 日期范围
            LocalDateTime startDate = parseDate(intent.getStartDate(), true);
            LocalDateTime endDate = parseDate(intent.getEndDate(), false);
            if (startDate != null) {
                if (p.getTakenAt() == null || p.getTakenAt().isBefore(startDate)) return false;
            }
            if (endDate != null) {
                if (p.getTakenAt() == null || p.getTakenAt().isAfter(endDate)) return false;
            }
            return true;
        }).collect(Collectors.toList());
    }

    // ===== 辅助方法 =====

    /**
     * 获取有效的人物ID列表：优先 personIds，回退 personId
     */
    private List<Long> getEffectivePersonIds(AiSearchIntent intent) {
        if (intent.getPersonIds() != null && !intent.getPersonIds().isEmpty()) {
            return intent.getPersonIds();
        }
        if (intent.getPersonId() != null) {
            return List.of(intent.getPersonId());
        }
        return Collections.emptyList();
    }

    private void fillMatchedNames(AiSearchResponse response, AiSearchIntent intent, CandidateContext candidates) {
        // 支持多人物名称
        List<Long> personIds = getEffectivePersonIds(intent);
        if (!personIds.isEmpty()) {
            String names = candidates.persons.stream()
                .filter(p -> personIds.contains(p.getId()))
                .map(PersonProfile::getName)
                .collect(Collectors.joining(", "));
            if (!names.isEmpty()) {
                response.setMatchedPersonName(names);
            }
        }

        if (intent.getTagIds() != null && !intent.getTagIds().isEmpty()) {
            List<String> tagNames = candidates.tags.stream()
                .filter(t -> intent.getTagIds().contains(t.getId()))
                .map(Tag::getName)
                .collect(Collectors.toList());
            response.setMatchedTagNames(tagNames);
        }

        if (intent.getAlbumIds() != null && !intent.getAlbumIds().isEmpty()) {
            List<String> albumNames = candidates.albums.stream()
                .filter(a -> intent.getAlbumIds().contains(a.getId()))
                .map(Album::getName)
                .collect(Collectors.toList());
            response.setMatchedAlbumNames(albumNames);
        }
    }

    private Set<Long> intersect(Set<Long> a, Set<Long> b) {
        if (a == null) return new HashSet<>(b);
        if (b == null) return a;
        a.retainAll(b);
        return a;
    }

    private boolean hasExifOrDateFilters(AiSearchIntent intent) {
        return (intent.getCameraModel() != null && !intent.getCameraModel().isEmpty()) ||
               (intent.getLensModel() != null && !intent.getLensModel().isEmpty()) ||
               intent.getMinAperture() != null || intent.getMaxAperture() != null ||
               intent.getMinFocalLength() != null || intent.getMaxFocalLength() != null ||
               intent.getMinShutterSpeed() != null || intent.getMaxShutterSpeed() != null ||
               intent.getMinIso() != null || intent.getMaxIso() != null ||
               (intent.getColorCategory() != null && !intent.getColorCategory().isEmpty()) ||
               intent.getMinQualityScore() != null ||
               (intent.getStartDate() != null && !intent.getStartDate().isEmpty()) ||
               (intent.getEndDate() != null && !intent.getEndDate().isEmpty());
    }

    private LocalDateTime parseDate(String dateStr, boolean isStart) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        try {
            LocalDate date = LocalDate.parse(dateStr);
            return isStart ? date.atStartOfDay() : date.atTime(23, 59, 59);
        } catch (Exception e) {
            log.warn("日期解析失败: {}", dateStr);
            return null;
        }
    }

    private String blankToNull(String s) {
        return (s != null && !s.isBlank()) ? s : null;
    }

    // ===== 内部类 =====

    static class CandidateContext {
        final List<PersonProfile> persons;
        final List<Tag> tags;
        final List<Album> albums;
        final List<String> cameraModels;
        final List<String> lensModels;

        CandidateContext(List<PersonProfile> persons, List<Tag> tags, List<Album> albums,
                         List<String> cameraModels, List<String> lensModels) {
            this.persons = persons;
            this.tags = tags;
            this.albums = albums;
            this.cameraModels = cameraModels;
            this.lensModels = lensModels;
        }
    }
}
