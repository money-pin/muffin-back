package com.muffin.auth.infrastructure.kakao;

import com.muffin.auth.application.KakaoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestClient;

@Configuration
public class KakaoAuthConfig {

    /** 카카오 ID Token 서명 검증용 JwtDecoder. 카카오 공개키(JWKS)를 가져와 캐싱하며 서명을 검증한다. */
    @Bean
    public JwtDecoder kakaoJwtDecoder(KakaoProperties kakaoProperties) {
        return NimbusJwtDecoder.withJwkSetUri(kakaoProperties.jwkSetUri()).build();
    }

    /** 카카오 API(kapi.kakao.com) 호출용 RestClient. */
    @Bean
    public RestClient kakaoApiRestClient(KakaoProperties kakaoProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(kakaoProperties.connectTimeout());
        requestFactory.setReadTimeout(kakaoProperties.readTimeout());

        return RestClient.builder()
                .baseUrl(kakaoProperties.apiBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
