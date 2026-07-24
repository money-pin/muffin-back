package com.muffin.news.infrastructure.openai;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 뉴스 본문 재구성에 사용하는 AI 설정을 제공한다.
 *
 * @param model 뉴스 재구성에 사용할 AI 모델명
 * @param maxSourceLength AI에 전달할 원문 본문의 최대 길이. 1 미만 값은 {@code @Min(1)}에 의해 설정 바인딩
 *     시점에 거부된다. 유효하지 않은 값이 본문 절단 단계에 도달하면 명시적으로 IllegalArgumentException을 던진다.
 */
@Validated
@ConfigurationProperties(prefix = "muffin.news.ai.reconstruction")
public record NewsReconstructionProperties(String model, @Min(1) int maxSourceLength) {}
