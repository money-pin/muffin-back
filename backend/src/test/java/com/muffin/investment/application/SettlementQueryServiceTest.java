package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.muffin.investment.application.projection.SettlementResultProjection;
import com.muffin.investment.application.settlement.SettlementQueryRepository;
import com.muffin.investment.application.settlement.SettlementQueryService;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.investment.presentation.dto.SettlementReason;
import com.muffin.investment.presentation.dto.SettlementResultResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 조회 결과를 상태별 응답(결과/사유)으로 매핑하는 로직을 검증한다. 저장소는 정적 stub으로 대체한다. */
class SettlementQueryServiceTest {

    private static final LocalDate SETTLE_DATE = LocalDate.of(2026, 5, 8);
    private static final Clock CLOCK =
            Clock.fixed(SETTLE_DATE.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("SETTLED 이면 손익 결과를 반환하고 reason 은 없다")
    void getRecentSettlementResult_returnsSettledResult() {
        SettlementResultResponse response =
                serviceReturning(projection(SettlementStatus.SETTLED, 45_000L)).getRecentSettlementResult(1L);

        assertEquals(SETTLE_DATE, response.investDate());
        assertEquals(45_000L, response.totalProfitLoss());
        assertEquals(1_000_000L, response.totalAmount());
        assertEquals(1_045_000L, response.totalAsset());
        assertNull(response.reason());
    }

    @Test
    @DisplayName("정산 중(PENDING/FAILED)이면 SETTLEMENT_PENDING 사유만 반환한다")
    void getRecentSettlementResult_returnsPendingReason() {
        SettlementResultResponse response =
                serviceReturning(projection(SettlementStatus.FAILED, 0L)).getRecentSettlementResult(1L);

        assertEquals(SettlementReason.SETTLEMENT_PENDING, response.reason());
        assertNull(response.totalProfitLoss());
    }

    @Test
    @DisplayName("취소/미투자(CANCELLED/NO_SETTLEMENT)면 NO_INVESTMENT 사유를 반환한다")
    void getRecentSettlementResult_returnsNoInvestmentForNoSettlement() {
        SettlementResultResponse response =
                serviceReturning(projection(SettlementStatus.NO_SETTLEMENT, 0L)).getRecentSettlementResult(1L);

        assertEquals(SettlementReason.NO_INVESTMENT, response.reason());
    }

    @Test
    @DisplayName("정산 대상 투자가 없으면 NO_INVESTMENT 사유를 반환한다")
    void getRecentSettlementResult_returnsNoInvestmentWhenEmpty() {
        SettlementResultResponse response = serviceReturning(null).getRecentSettlementResult(1L);

        assertEquals(SettlementReason.NO_INVESTMENT, response.reason());
    }

    private SettlementResultProjection projection(SettlementStatus status, long profitLoss) {
        return new SettlementResultProjection(
                status, SETTLE_DATE, 1_000_000L, profitLoss, BigDecimal.valueOf(4.5), 1_045_000L);
    }

    private SettlementQueryService serviceReturning(SettlementResultProjection projection) {
        SettlementQueryRepository stubRepository = (userId, today) -> {
            // 서비스가 주입된 Clock으로 "오늘(KST)"을 계산해 넘기는지 확인한다.
            assertEquals(SETTLE_DATE, today);
            return Optional.ofNullable(projection);
        };
        return new SettlementQueryService(stubRepository, CLOCK);
    }
}
