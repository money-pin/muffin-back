package com.muffin.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code muffin.auth.apple.*} 설정값 바인딩. bundleId는 ID Token의 audience(aud) 검증과 토큰 API의 client_id에 쓴다.
 * privateKey는 .p8 파일 전체를 base64 한 줄로 인코딩한 값이다(배포 env 파일은 여러 줄 값을 담을 수 없음).
 */
@ConfigurationProperties(prefix = "muffin.auth.apple")
public record AppleProperties(
        String bundleId,
        String jwkSetUri,
        String issuer,
        String teamId,
        String keyId,
        String privateKey,
        String apiBaseUrl,
        @DefaultValue("3s") Duration connectTimeout,
        @DefaultValue("5s") Duration readTimeout) {}
