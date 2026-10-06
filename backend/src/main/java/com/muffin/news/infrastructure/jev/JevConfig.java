package com.muffin.news.infrastructure.jev;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class JevConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** 기사 한 건 평가는 수백 ms로 끝난다. 길게 잡으면 느린 응답 하나가 아침 수집 전체를 붙잡는다. */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    @Bean
    @ConditionalOnExpression("${muffin.news.ai.enabled:false} and '${muffin.news.ai.unified-selector:OPENAI}' == 'JEV'")
    RestClient jevRestClient(JevProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new IllegalStateException("JEV_API_KEY is required when the Jev unified selector is enabled");
        }

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
