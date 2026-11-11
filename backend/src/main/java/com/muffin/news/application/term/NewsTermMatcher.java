package com.muffin.news.application.term;

import com.muffin.news.domain.term.TermDictionary;
import java.util.Comparator;
import java.util.List;

/**
 * 용어 사전에서 본문에 등장하는 용어를 고른다.
 *
 * <p>최초 매핑({@link NewsTermMappingService})과 과거 뉴스 재매핑({@link NewsTermRemappingService})이 같은 규칙을
 * 써야 하므로 여기 한 곳에 둔다. 두 곳에 같은 로직을 두면 매칭 규칙을 고칠 때 한쪽만 고쳐져, 새 뉴스와 옛 뉴스의 하이라이트가 서로
 * 다른 기준으로 붙는다.
 */
public final class NewsTermMatcher {

    /** 이보다 짧은 용어는 본문에서 낱말로 식별되지 않는다. */
    private static final int MIN_TERM_LENGTH = 2;

    private NewsTermMatcher() {}

    /**
     * 매칭 대상이 되는 용어만 길이 내림차순으로 추린다.
     *
     * <p>긴 용어를 먼저 두는 이유는 조회 단계에서 같은 위치에 걸린 매칭 중 긴 쪽을 택하기 때문이다. 매핑 순서를 맞춰 두면 저장된
     * 연결과 화면에 그어지는 하이라이트가 어긋나지 않는다.
     */
    public static List<TermDictionary> matchableTerms(List<TermDictionary> dictionary) {
        return dictionary.stream()
                .filter(term -> isMatchable(term.getTerm()))
                .sorted(Comparator.comparingInt(
                                (TermDictionary term) -> term.getTerm().length())
                        .reversed())
                .toList();
    }

    /** 본문에 등장하는 용어의 id를 길이 내림차순으로 반환한다. */
    public static List<Long> matchedTermIds(String content, List<TermDictionary> matchableTerms) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return matchableTerms.stream()
                .filter(term -> TermTextMatcher.containsTerm(content, term.getTerm()))
                .map(TermDictionary::getId)
                .toList();
    }

    private static boolean isMatchable(String term) {
        return term != null && TermTextMatcher.normalizedLength(term) >= MIN_TERM_LENGTH;
    }
}
