package com.muffin.auth.infrastructure.apple;

import com.muffin.auth.application.AppleProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class AppleAuthConfig {

    /** 애플 ID Token 서명 검증용 JwtDecoder. 애플 공개키(JWKS)를 가져와 캐싱하며 서명을 검증한다. */
    @Bean
    public JwtDecoder appleJwtDecoder(AppleProperties appleProperties) {
        return NimbusJwtDecoder.withJwkSetUri(appleProperties.jwkSetUri()).build();
    }
}
