package com.muffin.investment.domain.exception;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.investment.domain.exception.code.InvestmentErrorCode;

/** 모의투자 API의 비즈니스 오류를 공통 오류 응답으로 변환하기 위한 전용 예외. */
public class InvestmentException extends GeneralException {

    public InvestmentException(InvestmentErrorCode errorCode) {
        super(errorCode);
    }
}
