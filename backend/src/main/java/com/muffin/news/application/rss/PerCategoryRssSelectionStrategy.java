package com.muffin.news.application.rss;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 카테고리별로 따로 평가해 각각 최대 N건을 고르는 기존 방식.
 *
 * <p>한 카테고리의 선별 실패가 다른 카테고리까지 버리지 않도록 카테고리 단위로 예외를 막는다. 통합 선별이 실패했을 때의 폴백으로도 쓰인다.
 */
@Slf4j
@RequiredArgsConstructor
public class PerCategoryRssSelectionStrategy implements RssSelectionStrategy {

    private final RssArticleSelector articleSelector;

    @Override
    public Map<String, List<RssArticle>> select(Map<String, List<RssArticle>> candidatesByCategory) {
        Map<String, List<RssArticle>> selected = new LinkedHashMap<>();
        for (Map.Entry<String, List<RssArticle>> entry : candidatesByCategory.entrySet()) {
            try {
                List<RssArticle> articles = articleSelector.select(entry.getKey(), entry.getValue());
                if (!articles.isEmpty()) {
                    selected.put(entry.getKey(), articles);
                }
            } catch (Exception exception) {
                log.error("RSS selection failed: category={}", entry.getKey(), exception);
            }
        }
        return selected;
    }
}
