package com.muffin.auth.infrastructure.jwt;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.application.JwtProperties;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtAccessTokenProviderTest {

    private static final String SECRET = "unit-test-jwt-secret-key-32bytes-minimum-length";

    private final JwtAccessTokenProvider provider = new JwtAccessTokenProvider(new JwtProperties(SECRET, 60, 30));

    @Test
    @DisplayName("발급한 토큰을 파싱하면 원래 userId가 그대로 나온다")
    void issueThenParse_returnsSameUserId() {
        String token = provider.issue(42L, "USER");

        assertThat(provider.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("서명이 다른 시크릿으로 발급된 토큰은 파싱 시 UNAUTHORIZED 예외")
    void parse_rejectsTokenSignedWithDifferentSecret() {
        JwtAccessTokenProvider otherProvider =
                new JwtAccessTokenProvider(new JwtProperties("another-unit-test-jwt-secret-key-32bytes-min", 60, 30));
        String tokenFromOtherSecret = otherProvider.issue(1L, "USER");

        assertThatThrownBy(() -> provider.parseUserId(tokenFromOtherSecret))
                .isInstanceOf(GeneralException.class)
                .satisfies(e ->
                        assertThat(((GeneralException) e).getErrorCode()).isEqualTo(GeneralErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("만료된 토큰은 파싱 시 UNAUTHORIZED 예외")
    void parse_rejectsExpiredToken() {
        JwtAccessTokenProvider expiredIssuingProvider = new JwtAccessTokenProvider(new JwtProperties(SECRET, -1, 30));
        String expiredToken = expiredIssuingProvider.issue(1L, "USER");

        assertThatThrownBy(() -> provider.parseUserId(expiredToken))
                .isInstanceOf(GeneralException.class)
                .satisfies(e ->
                        assertThat(((GeneralException) e).getErrorCode()).isEqualTo(GeneralErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("형식이 깨진 문자열은 파싱 시 UNAUTHORIZED 예외")
    void parse_rejectsMalformedToken() {
        assertThatThrownBy(() -> provider.parseUserId("not-a-jwt"))
                .isInstanceOf(GeneralException.class)
                .satisfies(e ->
                        assertThat(((GeneralException) e).getErrorCode()).isEqualTo(GeneralErrorCode.UNAUTHORIZED));
    }
}
