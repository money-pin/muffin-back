package com.muffin.auth.presentation.refresh.dto;

/**
 * Access/Refresh Token 재발급 응답. 새 Refresh Token은 HttpOnly Cookie로만 전달하고 바디에는 노출하지 않는다.
 *
 * @param accessToken 새로 발급된 access token
 */
public record RefreshResponse(String accessToken) {}
