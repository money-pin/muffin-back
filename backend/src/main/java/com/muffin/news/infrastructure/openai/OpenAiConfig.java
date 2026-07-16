package com.muffin.news.infrastructure.openai;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAiConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    /** 뉴스 AI 기능에서 공통으로 사용할 OpenAI HTTP 클라이언트를 등록한다. */
    @Bean
    @ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
    RestClient openAiRestClient(OpenAiClientProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required when a news AI feature is enabled");
        }

        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);

        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
