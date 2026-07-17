package com.muffin.auth.presentation.signup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로컬(이메일/비밀번호) 회원가입 요청. 비밀번호 형식은 Auth.createLocal()이 도메인 계층에서 검증한다(단일 소스).
 *
 * @param email 로그인에 사용할 이메일
 * @param password 비밀번호
 * @param name 이름
 * @param termsAgreed 약관 동의 여부(false면 TERMS_NOT_AGREED로 가입 거부, service 계층에서 검증)
 */
public record LocalSignupRequest(
        @NotBlank @Email String email, @NotBlank String password, @NotBlank String name, boolean termsAgreed) {}
