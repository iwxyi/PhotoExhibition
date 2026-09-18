package com.photoexhibition.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photoexhibition.entity.Photo;
import com.photoexhibition.entity.PhotoVisualAnalysis;
import com.photoexhibition.entity.UserAccount;
import com.photoexhibition.entity.UserRole;
import com.photoexhibition.repository.PhotoRepository;
import com.photoexhibition.repository.PhotoVisualAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PhotoVisualAnalysisService {
    private final PhotoRepository photoRepository;
    private final PhotoVisualAnalysisRepository analysisRepository;
    private final SystemConfigService systemConfigService;
    private final UserPathService userPathService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public Map<String, Object> get(Long photoId, UserAccount user) {
        Photo photo = requireVisiblePhoto(photoId, user);
        return analysisRepository.findByPhotoId(photo.getId()).map(this::toResponse)
            .orElse(Map.of("photoId", photoId, "status", "NOT_ANALYZED"));
    }

    public Map<String, Object> analyze(Long photoId, UserAccount user) {
        if (!systemConfigService.isAiVisualAnalysisEnabled()) throw new IllegalStateException("AI图片视觉分析未启用");
        String apiUrl = systemConfigService.getAiSearchApiUrl();
        String apiKey = systemConfigService.getAiSearchApiKey();
        if (apiUrl == null || apiUrl.isBlank() || apiKey == null || apiKey.isBlank()) throw new IllegalStateException("AI提供商配置不完整");
        Photo photo = requireVisiblePhoto(photoId, user);
        PhotoVisualAnalysis record = analysisRepository.findByPhotoId(photoId).orElseGet(PhotoVisualAnalysis::new);
        record.setPhotoId(photoId); record.setUserId(photo.getUserId()); record.setStatus("RUNNING"); record.setErrorMessage(null);
        analysisRepository.save(record);
        try {
            Path imagePath = resolveAnalysisImagePath(photo, user);
            if (imagePath == null) throw new IllegalStateException("找不到可供视觉分析的图片文件");
            String mime = Optional.ofNullable(Files.probeContentType(imagePath)).orElse("image/jpeg");
            String image = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(imagePath));
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", systemConfigService.getAiSearchModel()); body.put("temperature", 0.1); body.put("response_format", Map.of("type", "json_object"));
            body.put("messages", List.of(Map.of("role", "system", "content", prompt()), Map.of("role", "user", "content", List.of(Map.of("type", "text", "text", "请分析这张照片。"), Map.of("type", "image_url", "image_url", Map.of("url", image, "detail", "low"))))));
            HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(apiKey); headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> response = restTemplate.exchange(chatEndpoint(apiUrl), HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            String content = String.valueOf(((Map)((List<?>) response.getBody().get("choices")).get(0)).get("message") instanceof Map ? ((Map)((Map)((List<?>) response.getBody().get("choices")).get(0)).get("message")).get("content") : "");
            Map<String, Object> parsed = objectMapper.readValue(stripFence(content), new TypeReference<Map<String, Object>>() {});
            String shortDescription = String.valueOf(parsed.getOrDefault("shortDescription", parsed.getOrDefault("caption", "")));
            String detailedDescription = String.valueOf(parsed.getOrDefault("detailedDescription", parsed.getOrDefault("description", shortDescription)));
            parsed.put("shortDescription", shortDescription); parsed.put("detailedDescription", detailedDescription);
            record.setCaption(shortDescription); record.setAnalysisJson(objectMapper.writeValueAsString(parsed));
            record.setModelName(systemConfigService.getAiSearchModel()); record.setStatus("COMPLETED"); record.setAnalyzedAt(LocalDateTime.now());
            return toResponse(analysisRepository.save(record));
        } catch (Exception e) {
            record.setStatus("FAILED"); record.setErrorMessage(e.getMessage()); analysisRepository.save(record); throw new IllegalStateException("图片视觉分析失败: " + e.getMessage());
        }
    }
    public boolean isCompleted(Long photoId) { return analysisRepository.findByPhotoId(photoId).map(item -> "COMPLETED".equals(item.getStatus())).orElse(false); }
    private Photo requireVisiblePhoto(Long id, UserAccount user) { Photo p = photoRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("照片不存在")); if (user.getRole() != UserRole.SUPER_ADMIN && !Objects.equals(p.getUserId(), user.getId())) throw new SecurityException("无权访问该照片"); return p; }
    private Map<String,Object> toResponse(PhotoVisualAnalysis r) { Map<String,Object> m=new LinkedHashMap<>(); m.put("photoId",r.getPhotoId());m.put("status",r.getStatus());m.put("caption",r.getCaption());m.put("analysisJson",r.getAnalysisJson());m.put("modelName",r.getModelName());m.put("analyzedAt",r.getAnalyzedAt());m.put("errorMessage",r.getErrorMessage());return m; }
    private Path resolveAnalysisImagePath(Photo photo, UserAccount user) {
        String[] candidates = { photo.getMediumThumbPath(), photo.getLargeThumbPath(), photo.getThumbnailPath(), photo.getWebpPath(), photo.getOriginalPath() };
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            try {
                Path resolved = userPathService.isStoragePathReference(candidate)
                    ? userPathService.resolveStoredPhotoPath(candidate)
                    : userPathService.resolveScopedPath(candidate, user);
                if (resolved != null && Files.isRegularFile(resolved)) return resolved;
            } catch (Exception ignored) {
                // Try the next available rendition, ending with the original image.
            }
        }
        return null;
    }
    private String chatEndpoint(String url) { return url.endsWith("/") ? url + "chat/completions" : url + "/chat/completions"; }
    private String stripFence(String value) { return value == null ? "{}" : value.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim(); }
    private String prompt() { return "你是专业摄影师和图片理解助手。只返回 JSON，不要 Markdown。字段必须包括：shortDescription(中文短描述，30字内)、detailedDescription(详细画面描述，像生成图片提示词一样具体，包含主体、构图、景别、光线、色彩、环境、动作和情绪)、caption(简短说明)、objects(物体数组)、actions(动作数组)、scenes(场景数组)、moods(氛围数组)、memoryPoints(记忆点数组)、visualTags(标签数组)、relationships(人物关系数组)、locationCandidates(地点候选数组)、photography(对象，含composition、lighting、color、focus、qualityScore、suggestions)。未知时使用空数组、空字符串或 null；不虚构身份或地点。"; }
}
