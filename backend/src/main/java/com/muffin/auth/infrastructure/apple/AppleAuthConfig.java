package com.muffin.auth.infrastructure.apple;

import com.muffin.auth.application.AppleProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;

@Configuration
public class AppleAuthConfig {

    /** 애플 ID Token 서명 검증용 JwtDecoder. 애플 공개키(JWKS)를 가져와 캐싱하며 서명을 검증한다. */
    @Bean
    public JwtDecoder appleJwtDecoder(AppleProperties appleProperties) {
        return NimbusJwtDecoder.withJwkSetUri(appleProperties.jwkSetUri()).build();
    }

    /** 애플 토큰 API(appleid.apple.com) 호출용 RestClient. */
    @Bean
    public RestClient appleApiRestClient(AppleProperties appleProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(appleProperties.connectTimeout());
        requestFactory.setReadTimeout(appleProperties.readTimeout());

        return RestClient.builder()
                .baseUrl(appleProperties.apiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
