package com.muffin.user.domain.exception;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.exception.code.UserErrorCode;

/** 사용자 관련 비즈니스 오류를 공통 오류 응답으로 변환하기 위한 전용 예외. */
public class UserException extends GeneralException {

    public UserException(UserErrorCode errorCode) {
        super(errorCode);
    }
}
