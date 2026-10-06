package com.muffin.news.application.rss;

/**
 * 카테고리를 함께 들고 있는 RSS 기사 후보.
 *
 * <p>통합 선별은 여러 카테고리의 후보를 한 번에 평가하므로, 선택된 기사를 저장할 때 어느 카테고리였는지 알아야 한다.
 */
public record CategorizedRssArticle(String category, RssArticle article) {}
