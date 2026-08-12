package com.muffin.news.application.term;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TermTextMatcher {

    private static final int MIN_MATCH_KEYWORD_LENGTH = 2;

    private TermTextMatcher() {}

    public static boolean containsTerm(String content, String term) {
        return findMatches(content, term).stream().findAny().isPresent();
    }

    public static List<TermTextMatch> findMatches(String content, String term) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        NormalizedText normalizedContent = normalizeContent(content);
        if (normalizedContent.text().isBlank()) {
            return List.of();
        }

        List<TermTextMatch> matches = new ArrayList<>();
        for (String keyword : keywords(term)) {
            String normalizedKeyword = normalizeKeyword(keyword);
            if (normalizedKeyword.length() < MIN_MATCH_KEYWORD_LENGTH) {
                continue;
            }

            int fromIndex = 0;
            while (fromIndex < normalizedContent.text().length()) {
                int start = normalizedContent.text().indexOf(normalizedKeyword, fromIndex);
                if (start < 0) {
                    break;
                }

                int end = start + normalizedKeyword.length() - 1;
                if (hasValidBoundary(content, normalizedContent, normalizedKeyword, start, end)) {
                    matches.add(new TermTextMatch(
                            normalizedContent.originalIndexes().get(start),
                            normalizedContent.originalIndexes().get(end) + 1));
                }
                fromIndex = start + normalizedKeyword.length();
            }
        }
        return matches;
    }

    public static Set<String> keywords(String term) {
        if (term == null || term.isBlank()) {
            return Set.of();
        }

        Set<String> keywords = new LinkedHashSet<>();
        String stripped = term.strip();
        keywords.add(stripped);

        int openIndex = stripped.indexOf('(');
        int closeIndex = stripped.lastIndexOf(')');
        if (openIndex > 0 && closeIndex > openIndex) {
            keywords.add(stripped.substring(0, openIndex).strip());
            keywords.add(stripped.substring(openIndex + 1, closeIndex).strip());
        }

        return keywords;
    }

    public static int normalizedLength(String value) {
        return normalizeKeyword(value).length();
    }

    private static NormalizedText normalizeContent(String content) {
        StringBuilder builder = new StringBuilder();
        List<Integer> originalIndexes = new ArrayList<>();
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (Character.isWhitespace(ch)) {
                continue;
            }
            builder.append(ch);
            originalIndexes.add(i);
        }
        return new NormalizedText(builder.toString(), originalIndexes);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < keyword.length(); i++) {
            char ch = keyword.charAt(i);
            if (!Character.isWhitespace(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static boolean hasValidBoundary(
            String originalContent, NormalizedText normalizedContent, String normalizedKeyword, int start, int end) {
        if (!isAsciiAlphaNumericKeyword(normalizedKeyword)) {
            return true;
        }

        int originalStart = normalizedContent.originalIndexes().get(start);
        int originalEnd = normalizedContent.originalIndexes().get(end);
        return !isAsciiAlphaNumericAt(originalContent, originalStart - 1)
                && !isAsciiAlphaNumericAt(originalContent, originalEnd + 1);
    }

    private static boolean isAsciiAlphaNumericKeyword(String keyword) {
        for (int i = 0; i < keyword.length(); i++) {
            if (!isAsciiAlphaNumeric(keyword.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiAlphaNumericAt(String value, int index) {
        return index >= 0 && index < value.length() && isAsciiAlphaNumeric(value.charAt(index));
    }

    private static boolean isAsciiAlphaNumeric(char ch) {
        return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9');
    }

    private record NormalizedText(String text, List<Integer> originalIndexes) {}

    public record TermTextMatch(int start, int end) {

        public int length() {
            return end - start;
        }
    }
}
