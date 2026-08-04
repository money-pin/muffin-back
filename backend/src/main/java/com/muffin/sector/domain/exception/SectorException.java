package com.muffin.sector.domain.exception;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.sector.domain.exception.code.SectorErrorCode;

/** 섹터 API의 비즈니스 오류를 공통 오류 응답으로 변환하기 위한 전용 예외. */
public class SectorException extends GeneralException {

    public SectorException(SectorErrorCode errorCode) {
        super(errorCode);
    }
}
