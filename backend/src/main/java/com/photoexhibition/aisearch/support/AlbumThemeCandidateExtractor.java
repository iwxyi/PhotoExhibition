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
public class AlbumThemeCandidateExtractor {

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
    private static final Set<String> LOCATION_HINT_SUFFIXES = Set.of(
        "园", "山", "湖", "江", "河", "海", "湾", "岛", "桥", "街", "路", "村",
        "镇", "城", "馆", "寺", "塔", "站", "场", "公园", "植物园", "景区", "校园"
    );

    private final UserPathService userPathService;

    public List<String> extractAlbumThemeCandidates(Album album) {
        if (album == null) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        themes.addAll(extractThemeCandidates(album.getName()));
        themes.addAll(extractPathThemeCandidates(album.getPath()));
        return new ArrayList<>(themes);
    }

    public List<String> extractThemeCandidates(String rawText) {
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

    private List<String> extractPathThemeCandidates(String path) {
        if (isBlank(path)) {
            return Collections.emptyList();
        }
        String normalized = normalizeTenantRelativePath(path);
        LinkedHashSet<String> themes = new LinkedHashSet<>();
        for (String segment : normalized.split("/")) {
            themes.addAll(extractThemeCandidates(segment));
        }
        return new ArrayList<>(themes);
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

    private void addThemeCandidate(Set<String> themes, String candidate) {
        String theme = normalizeThemeCandidate(candidate);
        if (!theme.isEmpty()) {
            themes.add(theme);
        }
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
