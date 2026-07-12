package com.muffin.news.application.rss;

import java.util.List;

public interface RssFeedClient {

    /** 지정한 RSS 피드를 조회하고 기사 목록으로 변환한다. */
    List<RssArticle> fetch(String feedUrl);
}
