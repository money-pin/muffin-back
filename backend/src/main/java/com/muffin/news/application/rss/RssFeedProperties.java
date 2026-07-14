package com.muffin.news.application.rss;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "muffin.news.rss")
public record RssFeedProperties(String publisher, List<RssFeedSource> feeds) {

    /** 바인딩된 피드 목록을 불변 목록으로 보관한다. */
    public RssFeedProperties {
        feeds = feeds == null ? List.of() : List.copyOf(feeds);
    }
}
