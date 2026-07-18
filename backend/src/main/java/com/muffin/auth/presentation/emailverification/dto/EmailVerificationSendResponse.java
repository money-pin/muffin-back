package com.muffin.auth.presentation.emailverification.dto;

/**
 * 이메일 인증번호 발송 응답.
 *
 * @param expiresIn 인증번호 만료까지 남은 시간(초)
 */
public record EmailVerificationSendResponse(long expiresIn) {}
