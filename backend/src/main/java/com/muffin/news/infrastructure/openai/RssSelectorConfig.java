package com.muffin.news.infrastructure.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muffin.news.application.rss.RssArticleSelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RssSelectorConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    /** AI 응답 직렬화와 역직렬화에 사용할 Jackson 매퍼를 등록한다. */
    @Bean
    @ConditionalOnProperty(name = "muffin.news.ai-selection.enabled", havingValue = "true")
    ObjectMapper openAiObjectMapper() {
        return new ObjectMapper();
    }

    /** OpenAI 호출에 연결 및 응답 제한 시간을 적용한 HTTP 클라이언트를 등록한다. */
    @Bean
    @ConditionalOnProperty(name = "muffin.news.ai-selection.enabled", havingValue = "true")
    RestClient openAiRestClient(AiSelectionProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is required when news AI selection is enabled");
        }
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    /** AI 선별이 비활성화되면 빈 결과를 반환하여 RSS 후보가 news 테이블에 저장되지 않게 한다. */
    @Bean
    @ConditionalOnProperty(name = "muffin.news.ai-selection.enabled", havingValue = "false", matchIfMissing = true)
    RssArticleSelector noSelectionRssArticleSelector() {
        return (category, candidates) -> List.of();
    }
}
