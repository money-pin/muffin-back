package com.muffin.stats.presentation.dto;

import com.muffin.stats.domain.HistorySort;
import com.muffin.stats.domain.StatsPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 누적 수익 내역 조회 쿼리 요청 파라미터.
 * @param period 기간 탭. DAY/WEEK/MONTH/YEAR/ALL (필수)
 * @param date 조회 기준 시점. period 형식에 맞춰 지정, 미지정 시 현재. ALL이면 무시
 * @param sort 종목 정렬 기준. 미지정 시 RATE_DESC
 */
public record ProfitHistoryRequest(
        @Schema(
                        description = "기간 탭. DAY/WEEK/MONTH/YEAR/ALL",
                        example = "MONTH",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                String period,
        @Schema(description = "조회 기준 시점(period 형식). 미지정 시 현재, ALL이면 무시", example = "2026-06") String date,
        @Schema(
                        description = "종목 정렬. AMOUNT_DESC/AMOUNT_ASC/RATE_DESC/RATE_ASC",
                        example = "RATE_DESC",
                        defaultValue = "RATE_DESC")
                String sort) {

    public StatsPeriod toPeriod() {
        return StatsPeriod.from(period);
    }

    public HistorySort toSort() {
        return HistorySort.from(sort);
    }
}
