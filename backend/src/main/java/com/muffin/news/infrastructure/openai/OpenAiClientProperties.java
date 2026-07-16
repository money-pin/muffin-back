package com.muffin.news.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "muffin.news.ai")
public record OpenAiClientProperties(String apiKey, String endpoint) {}
