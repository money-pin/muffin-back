package com.muffin.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code muffin.auth.jwt.*} 설정값 바인딩. */
@ConfigurationProperties(prefix = "muffin.auth.jwt")
public record JwtProperties(String secret, long accessTokenExpireMinutes, long refreshTokenExpireDays) {}
