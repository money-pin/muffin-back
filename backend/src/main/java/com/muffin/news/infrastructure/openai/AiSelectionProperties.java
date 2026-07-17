package com.muffin.news.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카테고리별 RSS 기사 선별에 사용하는 AI 설정을 제공한다.
 *
 * @param model 기사 선별에 사용할 AI 모델명
 * @param maxPerCategory 카테고리별로 선별할 최대 기사 수
 */
@ConfigurationProperties(prefix = "muffin.news.ai.ai-selection")
public record AiSelectionProperties(String model, int maxPerCategory) {}
