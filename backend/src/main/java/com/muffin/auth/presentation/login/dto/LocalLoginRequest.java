package com.muffin.auth.presentation.login.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로컬(이메일/비밀번호) 로그인 요청.
 *
 * @param email 가입 시 사용한 이메일
 * @param password 비밀번호
 */
public record LocalLoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
