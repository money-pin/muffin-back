package com.muffin.news.application.rss;

import java.time.LocalDateTime;

public record RssArticle(String title, String url, String description, LocalDateTime publishedAt) {}
