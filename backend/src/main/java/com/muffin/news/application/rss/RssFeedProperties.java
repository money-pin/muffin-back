package com.muffin.news.application.rss;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RSS 수집 설정.
 *
 * @param selectionMode 기사 선별 방식. 두 방식의 구현을 모두 두고 이 값으로 고른다
 */
@ConfigurationProperties(prefix = "muffin.news.rss")
public record RssFeedProperties(String publisher, List<RssFeedSource> feeds, RssSelectionMode selectionMode) {

    /** 바인딩된 피드 목록을 불변 목록으로 보관한다. */
    public RssFeedProperties {
        feeds = feeds == null ? List.of() : List.copyOf(feeds);
        selectionMode = selectionMode == null ? RssSelectionMode.UNIFIED : selectionMode;
    }
}
