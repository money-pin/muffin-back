package com.muffin.news.application.rss;

import java.time.LocalDateTime;

/** RSS 피드에서 파싱한 기사 한 건. 사진 정책상 기사 원본 이미지는 수집하지 않으므로 thumbnailUrl은 항상 null이다. */
public record RssArticle(
        String title, String url, String description, String thumbnailUrl, LocalDateTime publishedAt) {}
