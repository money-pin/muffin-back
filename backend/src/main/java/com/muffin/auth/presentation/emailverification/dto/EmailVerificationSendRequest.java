package com.muffin.auth.presentation.emailverification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 이메일 인증번호 발송 요청.
 *
 * @param email 인증번호를 받을 이메일
 */
public record EmailVerificationSendRequest(@NotBlank @Email String email) {}
