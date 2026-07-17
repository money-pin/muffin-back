package com.muffin.auth.application.signup;

/** 회원가입 성공 시 발급된 토큰. refreshToken은 컨트롤러가 HttpOnly Cookie로만 내려보내고 바디에는 담지 않는다. */
public record SignupResult(String accessToken, String refreshToken) {}
