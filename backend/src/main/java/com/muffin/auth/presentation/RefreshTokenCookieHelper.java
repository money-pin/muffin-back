package com.muffin.auth.presentation;

import com.muffin.auth.application.JwtProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Refresh Token을 HttpOnly Cookie로 굽고 읽는 유틸. 응답 바디에는 절대 노출하지 않는다(탈취 시 XSS로 못 훔치도록).
 * 회원가입/로그인/재발급/로그아웃에서 공유한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCookieHelper {

    private static final String COOKIE_NAME = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    private final JwtProperties jwtProperties;

    public ResponseCookie build(String rawRefreshToken) {
        return baseCookie(rawRefreshToken)
                .maxAge(Duration.ofDays(jwtProperties.refreshTokenExpireDays()))
                .build();
    }

    public ResponseCookie expire() {
        return baseCookie("").maxAge(0).build();
    }

    public Optional<String> extract(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH);
    }
}
