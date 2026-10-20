package com.muffin.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code muffin.auth.kakao.*} 설정값 바인딩. nativeAppKey는 ID Token의 audience(aud) 검증에 사용한다. */
@ConfigurationProperties(prefix = "muffin.auth.kakao")
public record KakaoProperties(String nativeAppKey, String jwkSetUri, String issuer) {}
