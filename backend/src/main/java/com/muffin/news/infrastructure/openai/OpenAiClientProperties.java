package com.muffin.news.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI API 호출에 공통으로 사용하는 클라이언트 설정을 제공한다.
 *
 * @param apiKey OpenAI API 인증에 사용할 키
 * @param endpoint OpenAI Responses API 요청 주소
 */
@ConfigurationProperties(prefix = "muffin.news.ai")
public record OpenAiClientProperties(String apiKey, String endpoint) {}
