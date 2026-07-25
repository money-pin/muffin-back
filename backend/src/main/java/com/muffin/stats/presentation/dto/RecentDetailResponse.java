package com.muffin.stats.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 최근 투자 성과 상세 조회 응답. 가장 최근 정산 완료(SETTLED) 투자 1건을 섹터별로 상세하게 내려준다. 정산 이력이 없으면 date는 null, 금액은 0, sectors는 빈
 * 배열이다.
 */
public record RecentDetailResponse(
        LocalDate date, long totalInvestment, long profitAmount, List<SectorDetailResponse> sectors) {

    /**
     * 섹터별 상세. profitRate는 그 섹터 매수금(totalInvestment) 대비 손익률(%)이다. isFallback이 true면 거래정지 등으로 0%가 적용된 섹터다.
     */
    public record SectorDetailResponse(
            String sectorCode,
            String sectorName,
            long profitAmount,
            BigDecimal profitRate,
            long totalInvestment,
            @JsonProperty("isFallback") boolean isFallback) {}
}
