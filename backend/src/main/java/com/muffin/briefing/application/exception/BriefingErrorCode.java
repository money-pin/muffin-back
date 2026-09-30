package com.muffin.briefing.application.exception;

import com.muffin.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 모닝 브리핑 조회에서 사용하는 에러 코드. */
@Getter
@AllArgsConstructor
public enum BriefingErrorCode implements BaseErrorCode {
    BRIEFING_NOT_FOUND(HttpStatus.NOT_FOUND, "BRIEFING_404_001", "존재하지 않는 브리핑입니다."),
    BRIEFING_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "BRIEFING_400_001", "다시 보기로 제공하지 않는 날짜입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
