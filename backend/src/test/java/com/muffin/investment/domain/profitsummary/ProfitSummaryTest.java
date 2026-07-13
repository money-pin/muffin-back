package com.muffin.investment.domain.profitsummary;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProfitSummaryTest {

    @Test
    @DisplayName("생성하면 전달한 손익 필드가 그대로 채워진다")
    void create_fillsProfitFields() {
        ProfitSummary summary =
                ProfitSummary.create(1L, LocalDate.of(2026, 5, 7), 15_000L, BigDecimal.valueOf(1.5), 128_000L);

        assertEquals(1L, summary.getUserId());
        assertEquals(LocalDate.of(2026, 5, 7), summary.getSummaryDate());
        assertEquals(15_000L, summary.getDailyProfitLoss());
        assertEquals(0, BigDecimal.valueOf(1.5).compareTo(summary.getDailyProfitLossRate()));
        assertEquals(128_000L, summary.getCumulativeProfitLoss());
    }

    @Test
    @DisplayName("갱신하면 손익 필드가 바뀌고 요약 일자는 유지된다")
    void update_changesProfitFieldsKeepingSummaryDate() {
        ProfitSummary summary =
                ProfitSummary.create(1L, LocalDate.of(2026, 5, 7), 15_000L, BigDecimal.valueOf(1.5), 128_000L);

        summary.update(-5_000L, BigDecimal.valueOf(-0.5), 108_000L);

        assertEquals(LocalDate.of(2026, 5, 7), summary.getSummaryDate());
        assertEquals(-5_000L, summary.getDailyProfitLoss());
        assertEquals(0, BigDecimal.valueOf(-0.5).compareTo(summary.getDailyProfitLossRate()));
        assertEquals(108_000L, summary.getCumulativeProfitLoss());
    }
}
