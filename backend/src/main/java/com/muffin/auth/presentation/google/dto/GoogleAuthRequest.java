package com.muffin.auth.presentation.google.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 구글 OAuth 통합(가입/로그인) 요청. 기존 계정이면 로그인, 없으면 자동 가입한다. 구글 로그인 자체가 동의 절차를
 * 거치므로 서비스 약관도 자동 동의로 간주한다.
 *
 * @param idToken 구글 Sign-In SDK가 발급한 ID Token
 */
public record GoogleAuthRequest(@NotBlank String idToken) {}
