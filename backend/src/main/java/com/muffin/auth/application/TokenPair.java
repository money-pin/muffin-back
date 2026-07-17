package com.muffin.auth.application;

/**
 * 회원가입/로그인 등 인증 성공 시 발급되는 access/refresh token 쌍. refreshToken은 컨트롤러가 HttpOnly Cookie로만
 * 내려보내고 응답 바디에는 담지 않는다.
 */
public record TokenPair(String accessToken, String refreshToken) {}
