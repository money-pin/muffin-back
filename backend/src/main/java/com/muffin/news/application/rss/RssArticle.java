package com.muffin.news.application.rss;

import java.time.LocalDateTime;

/** RSS 피드에서 파싱한 기사 한 건. thumbnailUrl은 피드가 이미지를 제공하지 않으면 null이다. */
public record RssArticle(
        String title, String url, String description, String thumbnailUrl, LocalDateTime publishedAt) {}
