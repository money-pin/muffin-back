package com.muffin.auth.domain.exception.code;

import com.muffin.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_003", "유효하지 않은 refresh token입니다."),
    INVALID_GOOGLE_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_004", "유효하지 않은 구글 로그인 토큰입니다."),
    WITHDRAWN_ACCOUNT(HttpStatus.FORBIDDEN, "AUTH_403_002", "탈퇴한 계정입니다."),
    SUSPENDED_ACCOUNT(HttpStatus.FORBIDDEN, "AUTH_403_003", "정지된 계정입니다."),
    EMAIL_ALREADY_IN_USE(HttpStatus.CONFLICT, "AUTH_409_001", "이미 사용 중인 이메일입니다."),
    RECENTLY_DELETED_EMAIL(HttpStatus.CONFLICT, "AUTH_409_003", "최근 탈퇴한 이메일입니다. 30일 후 다시 가입할 수 있습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
