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

    /**
     * 매칭 구간이 더 긴 낱말의 일부가 아닌지 확인한다.
     *
     * <p>ASCII 키워드는 양옆을 모두 본다. {@code CP}가 {@code ABCP}에 걸리면 안 되기 때문이다.
     *
     * <p>한글 키워드는 <b>앞쪽만</b> 본다. 한국어는 수식어가 앞에 붙어 복합어를 만들고(무+이표채, 금+지로) 조사와 어미는 뒤에
     * 붙기 때문이다(결제+가, 청산+될). 뒤까지 막으면 조사가 붙은 정상 표기를 전부 놓친다. 이 규칙이 없을 때 "마찬가지로"와
     * "규정상 금지로"에서 용어 {@code 지로(GIRO)}의 별칭 {@code 지로}가 걸렸다.
     *
     * <p>앞 글자가 한글이라는 이유로 버려지는 복합어(국부펀드의 {@code 펀드})는 대체로 그 복합어 자체가 사전에 등재되어 있어
     * 더 긴 매칭이 대신 잡는다.
     */
    private static boolean hasValidBoundary(
            String originalContent, NormalizedText normalizedContent, String normalizedKeyword, int start, int end) {
        int originalStart = normalizedContent.originalIndexes().get(start);
        int originalEnd = normalizedContent.originalIndexes().get(end);

        if (startsWithHangul(normalizedKeyword)) {
            return !isHangulAt(originalContent, originalStart - 1);
        }
        if (!isAsciiAlphaNumericKeyword(normalizedKeyword)) {
            return true;
        }
        return !isAsciiAlphaNumericAt(originalContent, originalStart - 1)
                && !isAsciiAlphaNumericAt(originalContent, originalEnd + 1);
    }

    /** 한글로 시작하는 키워드만 앞쪽 경계를 따진다. 한자나 기호로 시작하는 키워드는 종전대로 둔다. */
    private static boolean startsWithHangul(String keyword) {
        return !keyword.isEmpty() && isHangul(keyword.charAt(0));
    }

    private static boolean isHangulAt(String value, int index) {
        return index >= 0 && index < value.length() && isHangul(value.charAt(index));
    }

    /** 완성형 음절(가~힣)만 본다. 자모 단독은 본문에 낱말로 등장하지 않는다. */
    private static boolean isHangul(char ch) {
        return ch >= '\uac00' && ch <= '\ud7a3';
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
