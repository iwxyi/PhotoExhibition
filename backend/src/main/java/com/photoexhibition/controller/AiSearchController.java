package com.photoexhibition.controller;

import com.photoexhibition.dto.AiSearchExecuteRequest;
import com.photoexhibition.dto.AiSearchResponse;
import com.photoexhibition.service.AiSearchService;
import com.photoexhibition.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/photos/ai-search")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AiSearchController {

    private final AiSearchService aiSearchService;
    private final SystemConfigService systemConfigService;

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
            errorResponse.setExplanation("AI搜索出错: " + e.getMessage());
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
            errorResponse.setExplanation("AI搜索出错: " + e.getMessage());
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
}
