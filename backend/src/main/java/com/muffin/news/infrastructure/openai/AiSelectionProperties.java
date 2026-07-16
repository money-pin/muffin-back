package com.muffin.news.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "muffin.news.ai.ai-selection")
public record AiSelectionProperties(String model, int maxPerCategory) {}
