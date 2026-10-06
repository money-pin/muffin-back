package com.muffin.briefing.application.exception;

import com.muffin.global.apiPayload.exception.GeneralException;

/** 브리핑 조회 오류를 전역 예외 처리기의 표준 API 응답으로 변환하기 위한 전용 예외. */
public class BriefingException extends GeneralException {

    public BriefingException(BriefingErrorCode errorCode) {
        super(errorCode);
    }
}
