package com.muffin.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code muffin.auth.apple.*} 설정값 바인딩. bundleId는 ID Token의 audience(aud) 검증에 사용한다. */
@ConfigurationProperties(prefix = "muffin.auth.apple")
public record AppleProperties(String bundleId, String jwkSetUri, String issuer) {}
