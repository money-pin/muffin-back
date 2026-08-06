package com.muffin.auth.domain.exception;

import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;

/** 인증/인가 관련 비즈니스 오류를 공통 오류 응답으로 변환하기 위한 전용 예외. */
public class AuthException extends GeneralException {

    public AuthException(AuthErrorCode errorCode) {
        super(errorCode);
    }
}
