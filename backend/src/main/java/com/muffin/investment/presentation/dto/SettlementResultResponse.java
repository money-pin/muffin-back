package com.muffin.investment.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 정산 결과 팝업 응답(INVEST-06). SETTLED면 결과 필드가, 그 외에는 {@code reason}만 채워지며, null 필드는 응답에서 생략된다. 손익 방향(수익/손실)은 손익금 부호로
 * 클라이언트가 판단한다.
 *
 * @param investDate 정산 기준 일자(투자 일자)
 * @param totalProfitLoss 최종 손익금(손실 시 음수)
 * @param totalProfitLossRate 투자 원금 대비 손익률(%)
 * @param totalAmount 투자 원금
 * @param totalAsset 정산 반영 후 최종 총자산
 * @param reason 결과가 없을 때의 사유(NO_INVESTMENT/SETTLEMENT_PENDING)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SettlementResultResponse(
        LocalDate investDate,
        Long totalProfitLoss,
        BigDecimal totalProfitLossRate,
        Long totalAmount,
        Long totalAsset,
        SettlementReason reason) {

    /** 정산 완료(SETTLED) 결과를 담은 응답. */
    public static SettlementResultResponse settled(
            LocalDate investDate,
            long totalProfitLoss,
            BigDecimal totalProfitLossRate,
            long totalAmount,
            long totalAsset) {
        return new SettlementResultResponse(
                investDate, totalProfitLoss, totalProfitLossRate, totalAmount, totalAsset, null);
    }

    /** 결과가 없을 때의 사유만 담은 응답. */
    public static SettlementResultResponse reason(SettlementReason reason) {
        return new SettlementResultResponse(null, null, null, null, null, reason);
    }
}
