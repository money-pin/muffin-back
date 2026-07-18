package com.muffin.auth.presentation.login.dto;

/**
 * 로그인 응답. Refresh Token은 HttpOnly Cookie로만 전달하고 바디에는 노출하지 않는다.
 *
 * @param accessToken 발급된 access token
 */
public record LoginResponse(String accessToken) {}
