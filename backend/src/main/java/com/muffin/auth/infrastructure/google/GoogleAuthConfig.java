package com.muffin.auth.infrastructure.google;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** 구글 ID Token 서명 검증용 JwtDecoder. 구글 공개키(JWKS)를 가져와 캐싱하며 서명을 검증한다. */
@Configuration
public class GoogleAuthConfig {

    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    @Bean
    public JwtDecoder googleJwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
    }
}
