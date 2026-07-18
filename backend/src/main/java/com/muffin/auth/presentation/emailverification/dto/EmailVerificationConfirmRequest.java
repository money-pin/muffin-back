package com.muffin.auth.presentation.emailverification.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 이메일 인증번호 확인 요청.
 *
 * @param code 사용자가 입력한 인증번호
 */
public record EmailVerificationConfirmRequest(@NotBlank String code) {}
