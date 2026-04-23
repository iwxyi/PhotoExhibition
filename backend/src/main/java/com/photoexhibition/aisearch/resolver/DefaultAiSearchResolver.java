package com.photoexhibition.aisearch.resolver;

import com.photoexhibition.aisearch.reducer.AiSearchEvidenceBundle;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class DefaultAiSearchResolver implements AiSearchResolver {

    @Override
    public String resolve(AiSearchEvidenceBundle evidenceBundle) {
        if (evidenceBundle == null) {
            return null;
        }
        if ("relative_new_persons".equals(evidenceBundle.getPlanType())) {
            return resolveRelativeNewPersons(evidenceBundle);
        }
        if ("person_overview".equals(evidenceBundle.getPlanType())) {
            return resolvePersonOverview(evidenceBundle);
        }
        if ("person_cooccurrence".equals(evidenceBundle.getPlanType())) {
            return resolvePersonCooccurrence(evidenceBundle);
        }
        if ("person_pair_cooccurrence".equals(evidenceBundle.getPlanType())) {
            return resolvePersonPairCooccurrence(evidenceBundle);
        }
        if ("year_compare".equals(evidenceBundle.getPlanType())) {
            return resolveYearCompare(evidenceBundle);
        }
        if ("body_change".equals(evidenceBundle.getPlanType())) {
            return resolveBodyChange(evidenceBundle);
        }
        if ("month_overview".equals(evidenceBundle.getPlanType())) {
            return resolveMonthOverview(evidenceBundle);
        }
        if ("count_overview".equals(evidenceBundle.getPlanType())) {
            return resolveCountOverview(evidenceBundle);
        }
        if ("theme_overview".equals(evidenceBundle.getPlanType())) {
            return resolveThemeOverview(evidenceBundle);
        }
        if ("location_overview".equals(evidenceBundle.getPlanType())) {
            return resolveLocationOverview(evidenceBundle);
        }
        if ("album_overview".equals(evidenceBundle.getPlanType())) {
            return resolveAlbumOverview(evidenceBundle);
        }
        if ("day_overview".equals(evidenceBundle.getPlanType())) {
            return resolveDayOverview(evidenceBundle);
        }
        if ("tag_overview".equals(evidenceBundle.getPlanType())) {
            return resolveTagOverview(evidenceBundle);
        }
        return null;
    }

    private String resolveRelativeNewPersons(AiSearchEvidenceBundle evidenceBundle) {
        Object targetYear = evidenceBundle.getSummary().get("targetYear");
        int matchCount = asInt(evidenceBundle.getSummary().get("matchCount"));
        @SuppressWarnings("unchecked")
        List<String> topNames = (List<String>) evidenceBundle.getSummary().getOrDefault("topNames", Collections.emptyList());

        String period = targetYear == null ? "该年份" : String.valueOf(targetYear) + " 年";
        if ("none".equals(evidenceBundle.getEvidenceStatus()) || matchCount <= 0) {
            return period + "没有找到符合“此前未出现、在该年首次出现”的人物。";
        }
        if (topNames.isEmpty()) {
            return period + "共找到 " + matchCount + " 位符合条件的人物。";
        }
        return period + "共找到 " + matchCount + " 位符合条件的人物，优先包括 " + String.join("、", topNames) + "。";
    }

    private String resolvePersonOverview(AiSearchEvidenceBundle evidenceBundle) {
        int matchCount = asInt(evidenceBundle.getSummary().get("matchCount"));
        @SuppressWarnings("unchecked")
        List<String> topNames = (List<String>) evidenceBundle.getSummary().getOrDefault("topNames", Collections.emptyList());

        if ("none".equals(evidenceBundle.getEvidenceStatus()) || matchCount <= 0) {
            return "检索结论：未找到符合当前条件的人物结果。";
        }
        if (topNames.isEmpty()) {
            return "检索结论：共找到 " + matchCount + " 位符合条件的人物。";
        }
        return "检索结论：共找到 " + matchCount + " 位符合条件的人物，优先包括 " + String.join("、", topNames) + "。";
    }

    private String resolvePersonCooccurrence(AiSearchEvidenceBundle evidenceBundle) {
        int matchCount = asInt(evidenceBundle.getSummary().get("matchCount"));
        String anchorPersonName = String.valueOf(evidenceBundle.getSummary().getOrDefault("anchorPersonName", "该人物"));
        @SuppressWarnings("unchecked")
        List<String> topNames = (List<String>) evidenceBundle.getSummary().getOrDefault("topNames", Collections.emptyList());

        if ("none".equals(evidenceBundle.getEvidenceStatus()) || matchCount <= 0) {
            return "检索结论：未找到与" + anchorPersonName + "稳定共同出现的人物结果。";
        }
        if (topNames.isEmpty()) {
            return "检索结论：共找到 " + matchCount + " 位与" + anchorPersonName + "共同出现频率较高的人物。";
        }
        return "检索结论：与" + anchorPersonName + "共同出现频率较高的人物包括 " + String.join("、", topNames)
            + "，共 " + matchCount + " 位。";
    }

    private String resolvePersonPairCooccurrence(AiSearchEvidenceBundle evidenceBundle) {
        int matchCount = asInt(evidenceBundle.getSummary().get("matchCount"));
        @SuppressWarnings("unchecked")
        List<String> topNames = (List<String>) evidenceBundle.getSummary().getOrDefault("topNames", Collections.emptyList());

        if ("none".equals(evidenceBundle.getEvidenceStatus()) || matchCount <= 0) {
            return "检索结论：未找到稳定高频的同框人物组合。";
        }
        if (topNames.isEmpty()) {
            return "检索结论：共找到 " + matchCount + " 组共同出现频率较高的人物组合。";
        }
        return "检索结论：共同出现频率较高的人物组合包括 " + String.join("、", topNames)
            + "，共 " + matchCount + " 组。";
    }

    private int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private String resolveYearCompare(AiSearchEvidenceBundle evidenceBundle) {
        int leftYear = asInt(evidenceBundle.getSummary().get("leftYear"));
        int rightYear = asInt(evidenceBundle.getSummary().get("rightYear"));
        long leftCount = asLong(evidenceBundle.getSummary().get("leftCount"));
        long rightCount = asLong(evidenceBundle.getSummary().get("rightCount"));
        String subject = String.valueOf(evidenceBundle.getSummary().getOrDefault("subject", "相关内容"));

        if ("none".equals(evidenceBundle.getEvidenceStatus()) || (leftCount == 0 && rightCount == 0)) {
            return "检索结论：" + leftYear + " 年和 " + rightYear + " 年都未找到相关公开照片。";
        }
        if (leftCount == rightCount) {
            return "检索结论：" + leftYear + " 年与 " + rightYear + " 年关于 " + subject
                + " 的公开照片数量相同，都是 " + leftCount + " 张。";
        }

        boolean leftMore = leftCount > rightCount;
        long more = leftMore ? leftCount : rightCount;
        long less = leftMore ? rightCount : leftCount;
        int moreYear = leftMore ? leftYear : rightYear;
        int lessYear = leftMore ? rightYear : leftYear;
        String ratioText = less == 0 ? "明显更多" : String.format(java.util.Locale.ROOT, "约 %.1f 倍", (double) more / (double) less);

        return "检索结论：" + leftYear + " 年找到 " + leftCount + " 张，"
            + rightYear + " 年找到 " + rightCount + " 张；"
            + moreYear + " 年关于 " + subject + " 的拍摄更多，相比 " + lessYear + " 年" + ratioText + "。";
    }

    private long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private String resolveBodyChange(AiSearchEvidenceBundle evidenceBundle) {
        String personName = String.valueOf(evidenceBundle.getSummary().getOrDefault("personName", "该人物"));
        int startYear = asInt(evidenceBundle.getSummary().get("startYear"));
        int endYear = asInt(evidenceBundle.getSummary().get("endYear"));
        String trend = String.valueOf(evidenceBundle.getSummary().getOrDefault("trend", "unknown"));
        int totalPhotos = asInt(evidenceBundle.getSummary().get("totalPhotos"));
        double changePercent = asDouble(evidenceBundle.getSummary().get("changePercent"));
        String firstPeriod = String.valueOf(evidenceBundle.getSummary().getOrDefault("firstPeriod", ""));
        String lastPeriod = String.valueOf(evidenceBundle.getSummary().getOrDefault("lastPeriod", ""));
        double firstRatio = asDouble(evidenceBundle.getSummary().get("firstRatio"));
        double lastRatio = asDouble(evidenceBundle.getSummary().get("lastRatio"));

        if ("error".equals(trend)) {
            return personName + "在 " + startYear + "-" + endYear + " 年间的体型变化分析过程中出现错误。";
        }
        if (totalPhotos <= 0 || "unknown".equals(trend)) {
            return personName + "在 " + startYear + "-" + endYear + " 年间没有足够的人脸照片，无法分析体型变化。";
        }
        if ("single_period".equals(trend)) {
            return firstPeriod + " 期间共 " + totalPhotos + " 张照片，只有单个月份数据，暂时无法判断 " + personName + " 的变化趋势。";
        }
        if ("insufficient_data".equals(trend)) {
            return personName + "在 " + startYear + "-" + endYear + " 年间数据不足，无法判断变化趋势。";
        }
        if ("gained_weight".equals(trend)) {
            return firstPeriod + " 至 " + lastPeriod + " 期间，" + personName + " 的面部宽高比从 "
                + formatRatio(firstRatio) + " 变为 " + formatRatio(lastRatio) + "，增加 " + formatPercent(changePercent)
                + "，整体看起来更圆。";
        }
        if ("lost_weight".equals(trend)) {
            return firstPeriod + " 至 " + lastPeriod + " 期间，" + personName + " 的面部宽高比从 "
                + formatRatio(firstRatio) + " 变为 " + formatRatio(lastRatio) + "，减少 " + formatPercent(Math.abs(changePercent))
                + "，整体看起来更瘦长。";
        }
        if ("stable".equals(trend)) {
            return firstPeriod + " 至 " + lastPeriod + " 期间，" + personName + " 的面部宽高比变化不大（"
                + formatPercent(changePercent) + "），体型基本保持稳定。";
        }
        return personName + "在 " + startYear + "-" + endYear + " 年间的体型变化结论暂时无法确定。";
    }

    private double asDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0D;
    }

    private String formatRatio(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private String formatPercent(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f%%", value);
    }

    @SuppressWarnings("unchecked")
    private String resolveMonthOverview(AiSearchEvidenceBundle evidenceBundle) {
        String periodLabel = String.valueOf(evidenceBundle.getSummary().getOrDefault("periodLabel", "当前图库中"));
        long totalMatched = asLong(evidenceBundle.getSummary().get("totalMatched"));
        List<String> summaryItems = (List<String>) evidenceBundle.getSummary().getOrDefault("summaryItems", Collections.emptyList());
        if (totalMatched <= 0 || summaryItems.isEmpty()) {
            return "检索结论：" + periodLabel + "未找到可统计月份的公开照片。";
        }
        return "检索结论：" + periodLabel + "拍摄较集中的月份有：" + String.join("、", summaryItems) + "。";
    }

    private String resolveCountOverview(AiSearchEvidenceBundle evidenceBundle) {
        String periodLabel = String.valueOf(evidenceBundle.getSummary().getOrDefault("periodLabel", "当前图库中"));
        long totalMatched = asLong(evidenceBundle.getSummary().get("totalMatched"));
        int albumSize = asInt(evidenceBundle.getSummary().get("albumSize"));
        if (totalMatched <= 0) {
            return "检索结论：" + periodLabel + "未找到相关公开照片。";
        }
        return "检索结论：" + periodLabel + "共找到 " + totalMatched + " 张相关公开照片，分布在 "
            + albumSize + " 个相册中。";
    }

    @SuppressWarnings("unchecked")
    private String resolveThemeOverview(AiSearchEvidenceBundle evidenceBundle) {
        String periodLabel = String.valueOf(evidenceBundle.getSummary().getOrDefault("periodLabel", "当前图库中"));
        long totalMatched = asLong(evidenceBundle.getSummary().get("totalMatched"));
        List<String> summaryItems = (List<String>) evidenceBundle.getSummary().getOrDefault("summaryItems", Collections.emptyList());
        if (totalMatched <= 0) {
            return "检索结论：" + periodLabel + "未找到可统计的公开照片。";
        }
        if (summaryItems.isEmpty()) {
            return "检索结论：" + periodLabel + "共找到 " + totalMatched
                + " 张公开照片，但暂时难以仅根据相册名和标签归纳出明显主题。";
        }
        return "检索结论：" + periodLabel + "拍得较多的主题有：" + String.join("、", summaryItems)
            + "。统计主要基于相册名与照片标签，属于粗略归纳。";
    }

    @SuppressWarnings("unchecked")
    private String resolveLocationOverview(AiSearchEvidenceBundle evidenceBundle) {
        String periodLabel = String.valueOf(evidenceBundle.getSummary().getOrDefault("periodLabel", "当前图库中"));
        long totalMatched = asLong(evidenceBundle.getSummary().get("totalMatched"));
        List<String> summaryItems = (List<String>) evidenceBundle.getSummary().getOrDefault("summaryItems", Collections.emptyList());
        if (totalMatched <= 0) {
            return "检索结论：" + periodLabel + "未找到可统计地点的公开照片。";
        }
        if (summaryItems.isEmpty()) {
            return "检索结论：" + periodLabel + "共找到 " + totalMatched
                + " 张公开照片，但暂时难以仅根据相册名和路径稳定归纳出拍摄地点。";
        }
        return "检索结论：" + periodLabel + "相关内容主要拍摄于：" + String.join("、", summaryItems)
            + "。地点主要依据相册名与路径推断，属于粗略归纳。";
    }

    @SuppressWarnings("unchecked")
    private String resolveAlbumOverview(AiSearchEvidenceBundle evidenceBundle) {
        String periodLabel = String.valueOf(evidenceBundle.getSummary().getOrDefault("periodLabel", "当前图库中"));
        List<String> summaryItems = (List<String>) evidenceBundle.getSummary().getOrDefault("summaryItems", Collections.emptyList());
        long totalMatched = asLong(evidenceBundle.getSummary().get("totalMatched"));
        if (totalMatched <= 0 || summaryItems.isEmpty()) {
            return "检索结论：" + periodLabel + "未找到可统计的公开相册结果。";
        }
        return "检索结论：" + periodLabel + "拍得较多的相册有：" + String.join("、", summaryItems) + "。";
    }

    @SuppressWarnings("unchecked")
    private String resolveDayOverview(AiSearchEvidenceBundle evidenceBundle) {
        String periodLabel = String.valueOf(evidenceBundle.getSummary().getOrDefault("periodLabel", "当前图库中"));
        List<String> summaryItems = (List<String>) evidenceBundle.getSummary().getOrDefault("summaryItems", Collections.emptyList());
        long totalMatched = asLong(evidenceBundle.getSummary().get("totalMatched"));
        if (totalMatched <= 0 || summaryItems.isEmpty()) {
            return "检索结论：" + periodLabel + "未找到可统计具体日期的公开照片。";
        }
        return "检索结论：" + periodLabel + "拍摄主要集中在：" + String.join("、", summaryItems) + "。";
    }

    @SuppressWarnings("unchecked")
    private String resolveTagOverview(AiSearchEvidenceBundle evidenceBundle) {
        String periodLabel = String.valueOf(evidenceBundle.getSummary().getOrDefault("periodLabel", "当前图库中"));
        List<String> summaryItems = (List<String>) evidenceBundle.getSummary().getOrDefault("summaryItems", Collections.emptyList());
        long totalMatched = asLong(evidenceBundle.getSummary().get("totalMatched"));
        if (totalMatched <= 0 || summaryItems.isEmpty()) {
            return "检索结论：" + periodLabel + "未找到可统计标签的公开照片。";
        }
        return "检索结论：" + periodLabel + "高频标签主要有：" + String.join("、", summaryItems) + "。";
    }
}
