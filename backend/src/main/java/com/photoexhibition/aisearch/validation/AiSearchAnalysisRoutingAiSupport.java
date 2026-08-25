package com.photoexhibition.aisearch.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AiSearchAnalysisRoutingAiSupport {

    public String buildPrompt(List<String> candidateTagNames, int localBestScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是图库搜索的分析问题分类器。");
        sb.append("请把用户问题分类到以下类型之一：theme, location, album, month, count, person, person_cooccurrence, person_pair_cooccurrence, day, tag, year_compare, body_change, unknown。\n");
        sb.append("规则：\n");
        sb.append("1. 只做类型分类和主题词抽取，不生成答案。\n");
        sb.append("2. theme=主题/题材分布；location=地点分布；album=相册排行；month=月份分布；count=数量统计；person=人物概览；person_cooccurrence=与指定人物共同出现；person_pair_cooccurrence=全局人物对共同出现；day=日期分布；tag=标签排行；year_compare=两个年份对比；body_change=人物在两个时间段的胖瘦变化。\n");
        sb.append("3. 如果问题像“去年哪些地方最常拍夜樱”，应返回 location，topicKeywords 可返回 [\"夜樱\"]。\n");
        sb.append("4. 如果问题像“去年和前年相比樱花拍得更多还是更少”，应返回 year_compare，topicKeywords 可返回 [\"樱花\"]，leftYear/rightYear 可返回具体年份。\n");
        sb.append("5. 如果问题像“去年有谁”或“在杭州拍到了哪些人”，应返回 person，topicKeywords 可返回 [\"杭州\"]。\n");
        sb.append("6. 如果问题像“小明经常一起出现的是谁”或“和小明同框最多的是谁”，应返回 person_cooccurrence。\n");
        sb.append("7. 如果问题像“经常一起出现的是谁”或“谁和谁最常同框”，应返回 person_pair_cooccurrence。\n");
        sb.append("8. 如果问题像“小明今年比去年胖了吗”，应返回 body_change。\n");
        sb.append("9. 不确定时返回 unknown。\n");
        sb.append("10. 只返回 JSON：{\"analysisType\":\"...\",\"topicKeywords\":[],\"keywordSummary\":\"\",\"leftYear\":null,\"rightYear\":null,\"confidence\":0.0}\n");
        sb.append("当前本地路由最高分: ").append(localBestScore).append("\n");
        if (candidateTagNames != null && !candidateTagNames.isEmpty()) {
            sb.append("候选标签示例: ");
            sb.append(candidateTagNames.stream().limit(12).collect(Collectors.joining("、")));
            sb.append("\n");
        }
        return sb.toString();
    }

    public String mapAnalysisType(String type) {
        String normalized = normalizeLooseText(type);
        switch (normalized) {
            case "theme":
            case "location":
            case "album":
            case "month":
            case "count":
            case "personcooccurrence":
            case "person_cooccurrence":
            case "personpaircooccurrence":
            case "person_pair_cooccurrence":
            case "person":
            case "day":
            case "tag":
            case "yearcompare":
            case "year_compare":
            case "bodychange":
            case "body_change":
                if ("yearcompare".equals(normalized)) {
                    return "year_compare";
                }
                if ("personcooccurrence".equals(normalized)) {
                    return "person_cooccurrence";
                }
                if ("personpaircooccurrence".equals(normalized)) {
                    return "person_pair_cooccurrence";
                }
                if ("bodychange".equals(normalized)) {
                    return "body_change";
                }
                return normalized;
            default:
                return "";
        }
    }

    public AiSearchAnalysisFallbackRequest parseFallbackRequest(String query, JsonNode result) {
        AiSearchAnalysisFallbackRequest request = new AiSearchAnalysisFallbackRequest();
        request.setAiDerived(true);
        request.setRoutingType(mapAnalysisType(result == null ? "" : result.path("analysisType").asText("")));

        LinkedHashSet<String> extraKeywords = new LinkedHashSet<>();
        if (result != null) {
            JsonNode topicKeywords = result.get("topicKeywords");
            if (topicKeywords != null && topicKeywords.isArray()) {
                for (JsonNode item : topicKeywords) {
                    String keyword = normalizeSemanticQuery(item.asText(""));
                    if (!keyword.isBlank()) {
                        extraKeywords.add(keyword);
                    }
                }
            }
        }
        request.setTopicKeywords(new ArrayList<>(extraKeywords));

        String resolvedQuery = query == null ? "" : query;
        if (!extraKeywords.isEmpty()) {
            resolvedQuery = (resolvedQuery + " " + String.join(" ", extraKeywords)).trim();
        }
        request.setResolvedQuery(resolvedQuery);

        request.setLeftYear(readNullableInt(result, "leftYear"));
        request.setRightYear(readNullableInt(result, "rightYear"));

        String keywordSummary = normalizeSemanticQuery(result == null ? "" : result.path("keywordSummary").asText(""));
        if (!keywordSummary.isBlank()) {
            request.setKeywordSummary(keywordSummary);
        }
        return request;
    }

    private Integer readNullableInt(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value.isInt() || value.isLong()) {
            return value.asInt();
        }
        String text = value.asText("");
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
}
