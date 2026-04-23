package com.photoexhibition.aisearch.support;

import com.photoexhibition.entity.Album;
import com.photoexhibition.service.UserPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AlbumLocationCandidateExtractor {

    private static final Set<String> THEME_STOP_WORDS = Set.of(
        "照片", "图片", "相片", "相册", "合集", "记录", "主题", "题材", "拍摄", "拍的", "拍了",
        "去年", "前年", "今年", "什么", "比较多", "最多", "很多", "一下", "一下子", "内容",
        "人像", "风景", "宠物", "城市风光", "自然风光", "建筑", "夜景", "节日活动"
    );
    private static final Set<String> LOCATION_STOP_WORDS = Set.of(
        "人像", "风景", "宠物", "写真", "团片", "日常", "生活", "合集", "记录",
        "主题", "题材", "樱花", "夜樱", "花海", "语嫣", "小明", "小红"
    );
    private static final Set<String> LOCATION_HINT_SUFFIXES = Set.of(
        "园", "山", "湖", "江", "河", "海", "湾", "岛", "桥", "街", "路", "村",
        "镇", "城", "馆", "寺", "塔", "站", "场", "公园", "植物园", "景区", "校园"
    );

    private final UserPathService userPathService;

    public List<String> extractAlbumLocationCandidates(Album album) {
        if (album == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> locations = new LinkedHashSet<>();
        locations.addAll(extractLocationCandidates(album.getName()));
        locations.addAll(extractPathLocationCandidates(album.getPath()));
        return new ArrayList<>(locations);
    }

    private List<String> extractPathLocationCandidates(String path) {
        if (isBlank(path)) {
            return Collections.emptyList();
        }
        String normalized = normalizeTenantRelativePath(path);
        LinkedHashSet<String> locations = new LinkedHashSet<>();
        for (String segment : normalized.split("/")) {
            locations.addAll(extractLocationCandidates(segment));
        }
        return new ArrayList<>(locations);
    }

    private String normalizeTenantRelativePath(String path) {
        if (path == null || path.isBlank()) {
            return path;
        }
        String relativePath = userPathService.extractTenantRelativePhotoPath(path);
        if (relativePath != null && !relativePath.isBlank()) {
            return relativePath.replace('\\', '/');
        }
        String normalized = path.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
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

    private void addLocationCandidate(Set<String> locations, String candidate) {
        String location = normalizeLocationCandidate(candidate);
        if (!location.isEmpty()) {
            locations.add(location);
        }
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
