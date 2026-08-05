package com.muffin.sector.domain.exception.code;

import com.muffin.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SectorErrorCode implements BaseErrorCode {
    MARKET_CALENDAR_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE, "SECTOR_503_001", "거래일 정보를 확인할 수 없습니다. 잠시 후 다시 시도해주세요."),
    ETF_PRICE_PROVIDER_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE, "SECTOR_503_002", "ETF 시세 조회에 실패했습니다. 잠시 후 다시 시도해주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
