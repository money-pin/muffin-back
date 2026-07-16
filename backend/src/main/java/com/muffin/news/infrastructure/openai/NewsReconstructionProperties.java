package com.muffin.news.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 뉴스 본문 재구성에 사용하는 AI 설정을 제공한다.
 *
 * @param model 뉴스 재구성에 사용할 AI 모델명
 * @param maxSourceLength AI에 전달할 원문 본문의 최대 길이
 */
@ConfigurationProperties(prefix = "muffin.news.ai.reconstruction")
public record NewsReconstructionProperties(String model, int maxSourceLength) {}
