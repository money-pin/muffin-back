package com.muffin.news.application.exception;

import com.muffin.global.apiPayload.exception.GeneralException;

/** 뉴스 파이프라인 오류를 전역 예외 처리기의 표준 API 응답으로 변환하기 위한 전용 예외. */
public class NewsException extends GeneralException {

    public NewsException(NewsErrorCode errorCode) {
        super(errorCode);
    }

    public NewsException(NewsErrorCode errorCode, Throwable cause) {
        super(errorCode);
        initCause(cause);
    }
}
