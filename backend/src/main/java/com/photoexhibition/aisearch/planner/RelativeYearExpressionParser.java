package com.photoexhibition.aisearch.planner;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class RelativeYearExpressionParser {

    private static final Map<String, Integer> YEAR_OFFSETS = new LinkedHashMap<>();

    static {
        YEAR_OFFSETS.put("大前年", -3);
        YEAR_OFFSETS.put("前年", -2);
        YEAR_OFFSETS.put("去年", -1);
        YEAR_OFFSETS.put("今年", 0);
    }

    private RelativeYearExpressionParser() {
    }

    static Integer resolveSingleYear(String query) {
        return extractYearMentions(query).stream()
            .map(YearMention::getYear)
            .findFirst()
            .orElse(null);
    }

    static List<Integer> extractDistinctYearsInOrder(String query) {
        LinkedHashSet<Integer> years = new LinkedHashSet<>();
        for (YearMention mention : extractYearMentions(query)) {
            years.add(mention.getYear());
        }
        return new ArrayList<>(years);
    }

    static TemporalYearRoles resolveTemporalSetRoles(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        Integer absentYear = null;
        Integer presentYear = null;
        Integer missingAgainYear = null;

        for (String segment : splitSegments(query)) {
            Integer year = resolveSingleYear(segment);
            if (year == null) {
                continue;
            }
            if (absentYear == null && refersToAbsent(segment) && !refersToDisappearAgain(segment)) {
                absentYear = year;
                continue;
            }
            if (presentYear == null && refersToPresent(segment) && !refersToDisappearAgain(segment)) {
                presentYear = year;
                continue;
            }
            if (missingAgainYear == null && refersToDisappearAgain(segment)) {
                missingAgainYear = year;
            }
        }

        if (absentYear == null || presentYear == null || missingAgainYear == null) {
            return null;
        }
        return new TemporalYearRoles(absentYear, presentYear, missingAgainYear);
    }

    static ActivityYearRoles resolveActivityRoles(String query,
                                                  java.util.function.Predicate<String> targetCue,
                                                  java.util.function.Predicate<String> activityCue) {
        if (query == null || query.isBlank()) {
            return null;
        }
        Integer targetYear = null;
        Integer activeYear = null;
        for (String segment : splitSegments(query)) {
            Integer year = resolveSingleYear(segment);
            if (year == null) {
                continue;
            }
            if (targetYear == null && targetCue.test(segment)) {
                targetYear = year;
            }
            if (activeYear == null && activityCue.test(segment)) {
                activeYear = year;
            }
        }
        if (targetYear == null || activeYear == null) {
            List<Integer> years = extractDistinctYearsInOrder(query);
            if (targetYear == null && !years.isEmpty()) {
                targetYear = years.get(0);
            }
            if (activeYear == null && years.size() >= 2) {
                activeYear = years.get(1);
            }
        }
        if (targetYear == null || activeYear == null) {
            return null;
        }
        return new ActivityYearRoles(targetYear, activeYear);
    }

    static BodyChangeYearRoles resolveBodyChangeRoles(String query,
                                                      java.util.function.Predicate<String> targetCue) {
        if (query == null || query.isBlank()) {
            return null;
        }
        Integer targetYear = null;
        for (String segment : splitSegments(query)) {
            Integer year = resolveSingleYear(segment);
            if (year != null && targetYear == null && targetCue.test(segment)) {
                targetYear = year;
            }
        }

        List<Integer> years = extractDistinctYearsInOrder(query);
        if (targetYear == null && !years.isEmpty()) {
            targetYear = years.get(0);
        }
        if (years.size() < 2 || targetYear == null) {
            return null;
        }

        Integer startYear = null;
        Integer endYear = null;
        for (String segment : splitSegments(query)) {
            if (!refersToComparison(segment)) {
                continue;
            }
            List<Integer> segmentYears = extractDistinctYearsInOrder(segment);
            if (segmentYears.size() >= 2) {
                startYear = Math.min(segmentYears.get(0), segmentYears.get(1));
                endYear = Math.max(segmentYears.get(0), segmentYears.get(1));
                break;
            }
        }
        if (startYear == null || endYear == null) {
            startYear = Math.min(years.get(0), years.get(1));
            endYear = Math.max(years.get(0), years.get(1));
        }
        return new BodyChangeYearRoles(targetYear, startYear, endYear);
    }

    private static List<YearMention> extractYearMentions(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int currentYear = LocalDate.now().getYear();
        List<YearMention> mentions = new ArrayList<>();
        for (int index = 0; index < query.length(); index++) {
            for (String phrase : YEAR_OFFSETS.keySet()) {
                if (query.startsWith(phrase, index)) {
                    mentions.add(new YearMention(index, currentYear + YEAR_OFFSETS.get(phrase), phrase));
                    index += phrase.length() - 1;
                    break;
                }
            }
        }
        return mentions;
    }

    private static List<String> splitSegments(String query) {
        String normalized = query == null ? "" : query
            .replace("，", "|")
            .replace(",", "|")
            .replace("。", "|")
            .replace("；", "|")
            .replace(";", "|")
            .replace("、", "|")
            .replace("但", "|")
            .replace("并且", "|")
            .replace("而且", "|");
        List<String> segments = new ArrayList<>();
        for (String part : normalized.split("\\|")) {
            String segment = part.trim();
            if (!segment.isBlank()) {
                segments.add(segment);
            }
        }
        return segments;
    }

    private static boolean refersToAbsent(String query) {
        return query.contains("不存在") || query.contains("没出现")
            || query.contains("没有出现") || query.contains("未出现");
    }

    private static boolean refersToPresent(String query) {
        return query.contains("存在") || query.contains("出现") || query.contains("有");
    }

    private static boolean refersToDisappearAgain(String query) {
        return query.contains("没再出现") || query.contains("又没再出现")
            || query.contains("没有再出现") || query.contains("未再出现");
    }

    private static boolean refersToComparison(String query) {
        return query.contains("比") || query.contains("相比");
    }

    static final class TemporalYearRoles {
        private final int absentYear;
        private final int presentYear;
        private final int missingAgainYear;

        TemporalYearRoles(int absentYear, int presentYear, int missingAgainYear) {
            this.absentYear = absentYear;
            this.presentYear = presentYear;
            this.missingAgainYear = missingAgainYear;
        }

        int getAbsentYear() {
            return absentYear;
        }

        int getPresentYear() {
            return presentYear;
        }

        int getMissingAgainYear() {
            return missingAgainYear;
        }
    }

    static final class ActivityYearRoles {
        private final int targetYear;
        private final int activeYear;

        ActivityYearRoles(int targetYear, int activeYear) {
            this.targetYear = targetYear;
            this.activeYear = activeYear;
        }

        int getTargetYear() {
            return targetYear;
        }

        int getActiveYear() {
            return activeYear;
        }
    }

    static final class BodyChangeYearRoles {
        private final int targetYear;
        private final int startYear;
        private final int endYear;

        BodyChangeYearRoles(int targetYear, int startYear, int endYear) {
            this.targetYear = targetYear;
            this.startYear = startYear;
            this.endYear = endYear;
        }

        int getTargetYear() {
            return targetYear;
        }

        int getStartYear() {
            return startYear;
        }

        int getEndYear() {
            return endYear;
        }
    }

    private static final class YearMention {
        private final int index;
        private final int year;
        private final String phrase;

        private YearMention(int index, int year, String phrase) {
            this.index = index;
            this.year = year;
            this.phrase = phrase;
        }

        int getIndex() {
            return index;
        }

        int getYear() {
            return year;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof YearMention)) {
                return false;
            }
            YearMention that = (YearMention) other;
            return index == that.index && year == that.year && Objects.equals(phrase, that.phrase);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index, year, phrase);
        }
    }
}
