package com.muffin.news.application.rss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 모든 카테고리 후보를 한 번에 평가해 전체에서 최대 N건을 고르는 방식.
 *
 * <p>카테고리 균형을 맞추지 않으므로 특정 카테고리가 0건일 수 있다. 하루 전체에서 가장 중요한 기사를 고르는 것이 목적이고, 카테고리별
 * 할당은 중요도와 무관한 제약이기 때문이다.
 *
 * <p>AI 호출이 하루 한 번뿐이라 그 한 번이 실패하면 아무 기사도 수집되지 않는다. 카테고리별 선별은 카테고리마다 독립적인 기회가
 * 있었으므로 그대로는 가용성이 떨어진다. 그래서 실패하면 카테고리별 선별로 떨어뜨린다.
 */
@Slf4j
@RequiredArgsConstructor
public class UnifiedRssSelectionStrategy implements RssSelectionStrategy {

    private final UnifiedRssArticleSelector unifiedSelector;
    private final RssSelectionStrategy fallback;

    @Override
    public Map<String, List<RssArticle>> select(Map<String, List<RssArticle>> candidatesByCategory) {
        List<CategorizedRssArticle> candidates = flatten(candidatesByCategory);
        if (candidates.isEmpty()) {
            return Map.of();
        }

        try {
            return groupByCategory(unifiedSelector.select(candidates));
        } catch (Exception exception) {
            log.error("Unified RSS selection failed, falling back to per-category selection", exception);
            return fallback.select(candidatesByCategory);
        }
    }

    private static List<CategorizedRssArticle> flatten(Map<String, List<RssArticle>> candidatesByCategory) {
        List<CategorizedRssArticle> candidates = new ArrayList<>();
        candidatesByCategory.forEach((category, articles) ->
                articles.forEach(article -> candidates.add(new CategorizedRssArticle(category, article))));
        return candidates;
    }

    private static Map<String, List<RssArticle>> groupByCategory(List<CategorizedRssArticle> selected) {
        Map<String, List<RssArticle>> grouped = new LinkedHashMap<>();
        for (CategorizedRssArticle candidate : selected) {
            grouped.computeIfAbsent(candidate.category(), key -> new ArrayList<>())
                    .add(candidate.article());
        }
        return grouped;
    }
}
