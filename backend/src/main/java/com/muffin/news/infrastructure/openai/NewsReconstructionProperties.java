package com.muffin.news.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "muffin.news.ai.reconstruction")
public record NewsReconstructionProperties(String model, int maxSourceLength) {}
