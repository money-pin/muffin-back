package com.muffin.news.application.rss;

import java.util.List;

/** RSS 후보 중 news 테이블에 저장할 기사만 선별하는 경계 인터페이스. */
public interface RssArticleSelector {

    List<RssArticle> select(String category, List<RssArticle> candidates);
}
