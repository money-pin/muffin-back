package com.muffin.news.application.rss.selection;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "muffin.news.ai-selection")
public record AiSelectionProperties(String apiKey, String model, String endpoint, int maxPerCategory) {}
