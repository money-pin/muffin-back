package com.muffin.auth.presentation.google.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 구글 OAuth 통합(가입/로그인) 요청. 기존 계정이면 로그인, 없으면 termsAgreed를 검사해 자동 가입한다.
 *
 * @param idToken 구글 Sign-In SDK가 발급한 ID Token
 * @param termsAgreed 약관 동의 여부(신규 가입일 때만 검사, 기존 계정 로그인 시에는 무시)
 */
public record GoogleAuthRequest(@NotBlank String idToken, boolean termsAgreed) {}
