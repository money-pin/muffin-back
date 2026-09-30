package com.muffin.briefing.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 브리핑 생성에 사용하는 AI 설정.
 *
 * @param model 브리핑 문장 생성에 사용할 AI 모델명
 */
@ConfigurationProperties(prefix = "muffin.news.ai.briefing")
public record BriefingGenerationProperties(String model) {}
