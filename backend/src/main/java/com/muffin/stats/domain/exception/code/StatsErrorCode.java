package com.muffin.stats.domain.exception.code;

import com.muffin.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 통계 조회 요청 값 검증 에러 코드.
 */
@Getter
@AllArgsConstructor
public enum StatsErrorCode implements BaseErrorCode {
    INVALID_PERIOD(HttpStatus.BAD_REQUEST, "STATS_400_001", "조회 기간(period) 값이 올바르지 않습니다."),
    INVALID_SORT(HttpStatus.BAD_REQUEST, "STATS_400_002", "정렬 기준(sort) 값이 올바르지 않습니다."),
    INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "STATS_400_003", "조회 기준 시점(date) 형식이 조회 기간과 맞지 않습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
