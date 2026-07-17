package com.muffin.auth.application.exception;

import com.muffin.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    EMAIL_VERIFICATION_CODE_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH_400_001", "인증번호가 일치하지 않습니다."),
    EMAIL_VERIFICATION_EXPIRED(HttpStatus.GONE, "AUTH_410_001", "인증번호가 만료되었습니다."),
    EMAIL_VERIFICATION_LOCKED(HttpStatus.LOCKED, "AUTH_423_001", "인증 시도 횟수를 초과하여 잠겼습니다. 인증번호를 다시 요청해 주세요."),
    EMAIL_ALREADY_IN_USE(HttpStatus.CONFLICT, "AUTH_409_001", "이미 사용 중인 이메일입니다."),
    EMAIL_ALREADY_VERIFIED(HttpStatus.CONFLICT, "AUTH_409_002", "이미 인증이 완료된 이메일입니다."),
    EMAIL_VERIFICATION_RESEND_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "AUTH_429_001", "잠시 후 다시 시도해주세요."),
    EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_429_002", "오늘 인증번호 전송 횟수를 초과했습니다."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_500_001", "인증 메일 발송에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
