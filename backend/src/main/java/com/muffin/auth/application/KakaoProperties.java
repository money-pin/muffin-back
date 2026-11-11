package com.muffin.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code muffin.auth.kakao.*} 설정값 바인딩. nativeAppKey는 ID Token의 audience(aud) 검증에, adminKey는 연결 끊기 API
 * 호출에 사용한다.
 */
@ConfigurationProperties(prefix = "muffin.auth.kakao")
public record KakaoProperties(
        String nativeAppKey,
        String jwkSetUri,
        String issuer,
        String adminKey,
        String apiBaseUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout) {}
