package com.muffin.stats.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/**
 * 누적 수익 내역 조회 응답. 선택한 기간 윈도우 안에서 발생한 손익 요약 + 섹터별 내역을 내려준다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfitHistoryResponse(
        String period,
        String date,
        boolean hasPrev,
        boolean hasNext,
        SummaryResponse summary,
        String sort,
        List<SectorHistoryResponse> sectors) {

    /** 기간 합계. profitRate는 그 기간 누적 매수금(totalInvestment) 대비 손익률(%)이다. */
    public record SummaryResponse(long profitAmount, BigDecimal profitRate, long totalInvestment) {}

    /** 섹터별 내역. profitRate는 그 섹터 누적 매수금(totalInvestment) 대비 손익률(%)이다. */
    public record SectorHistoryResponse(
            String sectorCode, String sectorName, long profitAmount, BigDecimal profitRate, long totalInvestment) {}
}
