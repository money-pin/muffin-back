package com.muffin.investment.exception;

import com.muffin.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum InvestmentErrorCode implements BaseErrorCode {
    INVESTMENT_WINDOW_CLOSED(HttpStatus.BAD_REQUEST, "INVESTMENT_400_001", "현재는 투자할 수 있는 시간이 아닙니다."),
    INVALID_SECTOR(HttpStatus.BAD_REQUEST, "INVESTMENT_400_002", "투자할 수 없는 섹터가 포함되어 있습니다."),
    BUDGET_EXCEEDED(HttpStatus.BAD_REQUEST, "INVESTMENT_400_003", "투자 금액이 보유 자산을 초과합니다."),
    INVESTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "INVESTMENT_404_001", "오늘 확정한 투자를 찾을 수 없습니다."),
    INVESTMENT_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "INVESTMENT_409_001", "오늘 이미 다른 구성으로 투자를 확정했습니다."),
    USER_ASSET_NOT_INITIALIZED(HttpStatus.CONFLICT, "INVESTMENT_409_002", "투자에 필요한 사용자 자산이 아직 생성되지 않았습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
