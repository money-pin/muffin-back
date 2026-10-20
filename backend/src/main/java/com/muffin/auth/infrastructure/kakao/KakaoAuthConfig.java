package com.muffin.auth.infrastructure.kakao;

import com.muffin.auth.application.KakaoProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** 카카오 ID Token 서명 검증용 JwtDecoder. 카카오 공개키(JWKS)를 가져와 캐싱하며 서명을 검증한다. */
@Configuration
public class KakaoAuthConfig {

    @Bean
    public JwtDecoder kakaoJwtDecoder(KakaoProperties kakaoProperties) {
        return NimbusJwtDecoder.withJwkSetUri(kakaoProperties.jwkSetUri()).build();
    }
}
