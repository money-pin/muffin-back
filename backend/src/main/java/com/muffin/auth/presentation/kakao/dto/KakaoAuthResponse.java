package com.muffin.auth.presentation.kakao.dto;

/**
 * 카카오 로그인 통합 응답. Refresh Token은 HttpOnly Cookie로만 전달하고 바디에는 노출하지 않는다.
 *
 * @param accessToken 발급된 access token
 */
public record KakaoAuthResponse(String accessToken) {}
