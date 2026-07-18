package com.muffin.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code muffin.auth.google.*} 설정값 바인딩. clientId는 ID Token의 audience(aud) 검증에 사용한다. */
@ConfigurationProperties(prefix = "muffin.auth.google")
public record GoogleProperties(String clientId) {}
