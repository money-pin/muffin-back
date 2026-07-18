package com.muffin.news.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 뉴스 경제 상식 해설카드 AI 생성 설정. */
@ConfigurationProperties(prefix = "muffin.news.ai.explanation")
public record NewsExplanationProperties(String model) {}
