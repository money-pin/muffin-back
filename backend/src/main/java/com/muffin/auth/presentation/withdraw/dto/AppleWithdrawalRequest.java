package com.muffin.auth.presentation.withdraw.dto;

/**
 * 애플 계정 탈퇴 요청 body. 애플 계정만 보내고, 구글·카카오 계정은 body 없이 탈퇴한다.
 *
 * @param authorizationCode 탈퇴 직전 애플 로그인을 다시 띄워 받은 authorization code(5분 유효, 1회용)
 */
public record AppleWithdrawalRequest(String authorizationCode) {}
