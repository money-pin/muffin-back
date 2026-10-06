package com.muffin.news.application.rss;

import java.util.List;

/**
 * 모든 카테고리의 후보를 한 번에 평가해 전체에서 중요도순으로 고른다.
 *
 * <p>카테고리별 선별({@link RssArticleSelector})과 달리 호출이 하루 한 번이고, 총 선별 건수가 카테고리 수와 무관하게 고정된다.
 */
public interface UnifiedRssArticleSelector {

    /**
     * 후보 전체에서 설정된 상한까지 고른다.
     *
     * @return 중요도순으로 선택된 기사. 카테고리 균형은 보장하지 않으므로 특정 카테고리가 0건일 수 있다
     */
    List<CategorizedRssArticle> select(List<CategorizedRssArticle> candidates);
}
