package com.photoexhibition.controller;

import com.photoexhibition.dto.AiSearchExecuteRequest;
import com.photoexhibition.dto.AiSearchResponse;
import com.photoexhibition.service.AiSearchService;
import com.photoexhibition.service.SystemConfigService;
import com.photoexhibition.service.UserPathService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/photos/ai-search")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AiSearchController {

    private static final Pattern EMBEDDED_PATH_PATTERN =
        Pattern.compile("(storage://[^\\s,;]+|[A-Za-z]:\\\\[^\\s,;]+|/(?:[^\\s,;])+)");

    private final AiSearchService aiSearchService;
    private final SystemConfigService systemConfigService;
    private final UserPathService userPathService;

    @GetMapping
    public ResponseEntity<AiSearchResponse> aiSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (!systemConfigService.isAiSearchEnabled()) {
            AiSearchResponse response = new AiSearchResponse();
            response.setAiSearchEnabled(false);
            response.setExplanation("AI搜索功能未启用");
            response.setPhotos(Collections.emptyList());
            response.setAlbums(Collections.emptyList());
            response.setPersons(Collections.emptyList());
            return ResponseEntity.ok(response);
        }

        try {
            AiSearchResponse result = aiSearchService.search(q, page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("AI搜索失败: {}", e.getMessage(), e);
            AiSearchResponse errorResponse = new AiSearchResponse();
            errorResponse.setAiSearchEnabled(true);
            errorResponse.setExplanation("AI搜索出错: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            errorResponse.setPhotos(Collections.emptyList());
            errorResponse.setAlbums(Collections.emptyList());
            errorResponse.setPersons(Collections.emptyList());
            return ResponseEntity.ok(errorResponse);
        }
    }

    @PostMapping("/execute")
    public ResponseEntity<AiSearchResponse> executeAiSearch(@RequestBody AiSearchExecuteRequest request) {
        if (!systemConfigService.isAiSearchEnabled()) {
            AiSearchResponse response = new AiSearchResponse();
            response.setAiSearchEnabled(false);
            response.setExplanation("AI搜索功能未启用");
            response.setPhotos(Collections.emptyList());
            response.setAlbums(Collections.emptyList());
            response.setPersons(Collections.emptyList());
            return ResponseEntity.ok(response);
        }

        try {
            String query = request.getQuery() == null ? "" : request.getQuery();
            int page = request.getPage() == null ? 0 : request.getPage();
            int size = request.getSize() == null ? 20 : request.getSize();
            AiSearchResponse result = aiSearchService.searchWithSuggestion(query, request.getIntent(), request.getSuggestionAction(), page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("AI搜索执行建议失败: {}", e.getMessage(), e);
            AiSearchResponse errorResponse = new AiSearchResponse();
            errorResponse.setAiSearchEnabled(true);
            errorResponse.setExplanation("AI搜索出错: " + sanitizeErrorMessage(e.getMessage(), "系统异常"));
            errorResponse.setPhotos(Collections.emptyList());
            errorResponse.setAlbums(Collections.emptyList());
            errorResponse.setPersons(Collections.emptyList());
            return ResponseEntity.ok(errorResponse);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("enabled", systemConfigService.isAiSearchEnabled());
        return ResponseEntity.ok(resp);
    }

    private String sanitizeErrorMessage(String message, String fallback) {
        if (message == null || message.isBlank()) {
            return fallback;
        }
        Matcher matcher = EMBEDDED_PATH_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        boolean replaced = false;
        while (matcher.find()) {
            String candidate = matcher.group(1);
            String sanitizedCandidate = userPathService.toDisplayPath(candidate, true);
            if (!candidate.equals(sanitizedCandidate)) {
                replaced = true;
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(sanitizedCandidate));
        }
        matcher.appendTail(buffer);
        return replaced ? buffer.toString() : message;
    }
}
