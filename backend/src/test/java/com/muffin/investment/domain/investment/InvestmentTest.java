package com.muffin.investment.domain.investment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.PriceDataSource;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InvestmentTest {

    private static final Long USER_ID = 1L;
    private static final Long USER_ASSET_ID = 10L;
    private static final LocalDate INVEST_DATE = LocalDate.of(2026, 5, 7);

    @Test
    @DisplayName("섹터를 추가하면 총투자금은 섹터 금액의 합이 된다")
    void addSector_totalAmountEqualsSumOfSectorAmounts() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);

        investment.addSector(100L, 10, 300_000L, BigDecimal.valueOf(30_000));
        investment.addSector(200L, 5, 200_000L, BigDecimal.valueOf(40_000));

        assertEquals(500_000L, investment.getTotalAmount());
        assertEquals(2, investment.getSectors().size());
    }

    @Test
    @DisplayName("정산하면 총손익은 섹터 손익의 합이고 손익률과 상태가 갱신된다")
    void settle_aggregatesProfitAndUpdatesStatus() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, BigDecimal.valueOf(30_000));
        investment.addSector(200L, 5, 200_000L, BigDecimal.valueOf(40_000));

        investment.applySectorResult(
                100L, BigDecimal.valueOf(31_800), 18_000L, BigDecimal.valueOf(6.0), PriceDataSource.NORMAL);
        investment.applySectorResult(
                200L, BigDecimal.valueOf(40_400), 2_000L, BigDecimal.valueOf(1.0), PriceDataSource.NORMAL);

        LocalDateTime settledAt = LocalDateTime.of(2026, 5, 8, 9, 0);
        investment.settle(settledAt);

        // 총손익 20,000 / 총투자금 500,000 = 4.0000%
        assertEquals(20_000L, investment.getTotalProfitLoss());
        assertEquals(0, new BigDecimal("4.0000").compareTo(investment.getTotalProfitLossRate()));
        assertEquals(SettlementStatus.SETTLED, investment.getSettlementStatus());
        assertEquals(settledAt, investment.getSettledAt());
    }

    @Test
    @DisplayName("당일 시가로 섹터를 정산하면 손익률과 손익금이 계산되고 총손익이 합산된다")
    void settleSector_computesProfitFromSellPrice() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, BigDecimal.valueOf(30_000));
        investment.addSector(200L, 5, 200_000L, BigDecimal.valueOf(40_000));

        // (31,800-30,000)/30,000 = 6.0000%, 손익 300,000×6% = 18,000
        investment.settleSector(100L, BigDecimal.valueOf(31_800));
        // (40,400-40,000)/40,000 = 1.0000%, 손익 200,000×1% = 2,000
        investment.settleSector(200L, BigDecimal.valueOf(40_400));

        InvestmentSector first = investment.getSectors().getFirst();
        assertEquals(18_000L, first.getProfitLoss());
        assertEquals(0, new BigDecimal("6.0000").compareTo(first.getProfitLossRate()));
        assertEquals(PriceDataSource.NORMAL, first.getPriceDataSource());

        investment.settle(LocalDateTime.of(2026, 5, 8, 9, 30));
        assertEquals(20_000L, investment.getTotalProfitLoss());
        assertEquals(0, new BigDecimal("4.0000").compareTo(investment.getTotalProfitLossRate()));
        assertEquals(SettlementStatus.SETTLED, investment.getSettlementStatus());
    }

    @Test
    @DisplayName("폴백 정산하면 손익 0, 손익률 0, 출처 FALLBACK_ZERO 로 처리된다")
    void settleSectorFallback_appliesZeroWithFallbackSource() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, BigDecimal.valueOf(30_000));

        investment.settleSectorFallback(100L);

        InvestmentSector sector = investment.getSectors().getFirst();
        assertEquals(0L, sector.getProfitLoss());
        assertEquals(0, BigDecimal.ZERO.compareTo(sector.getProfitLossRate()));
        assertEquals(PriceDataSource.FALLBACK_ZERO, sector.getPriceDataSource());

        investment.settle(LocalDateTime.of(2026, 5, 8, 9, 30));
        assertEquals(0L, investment.getTotalProfitLoss());
        assertEquals(0, BigDecimal.ZERO.compareTo(investment.getTotalProfitLossRate()));
    }

    @Test
    @DisplayName("매수가 스냅샷이 없는 섹터를 시가로 정산하면 데이터 결함으로 예외가 발생한다")
    void settleSector_throwsWhenBuyPriceMissing() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, null); // 매수가 스냅샷 결손

        assertThrows(IllegalStateException.class, () -> investment.settleSector(100L, BigDecimal.valueOf(31_800)));
    }

    @Test
    @DisplayName("미정산 섹터가 있는 상태로 정산하면 예외가 발생한다")
    void settle_throwsWhenSectorNotResolved() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, BigDecimal.valueOf(30_000));
        // applySectorResult 미호출 → 섹터 profitLoss 가 null 인 상태

        assertThrows(IllegalStateException.class, () -> investment.settle(LocalDateTime.of(2026, 5, 8, 9, 0)));
    }

    @Test
    @DisplayName("존재하지 않는 섹터에 정산 결과를 반영하면 예외가 발생한다")
    void applySectorResult_throwsWhenSectorNotFound() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, BigDecimal.valueOf(30_000));

        assertThrows(
                IllegalArgumentException.class,
                () -> investment.applySectorResult(
                        999L, BigDecimal.valueOf(10), 0L, BigDecimal.ZERO, PriceDataSource.NORMAL));
    }

    @Test
    @DisplayName("투자하지 않은 경우 상태는 NO_INVEST, 정산 상태는 PENDING, 총투자금은 0 이다")
    void noInvest_hasNoInvestStatusAndZeroTotalAmount() {
        Investment investment = Investment.noInvest(USER_ID, USER_ASSET_ID, INVEST_DATE);

        assertEquals(InvestmentStatus.NO_INVEST, investment.getStatus());
        assertEquals(SettlementStatus.PENDING, investment.getSettlementStatus());
        assertEquals(0L, investment.getTotalAmount());
    }

    @Test
    @DisplayName("정산에 실패하면 상태가 FAILED 가 된다")
    void failSettlement_setsFailedStatus() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);

        investment.failSettlement();

        assertEquals(SettlementStatus.FAILED, investment.getSettlementStatus());
    }

    @Test
    @DisplayName("getSectors 로 얻은 컬렉션은 외부에서 직접 수정할 수 없다")
    void getSectors_returnsUnmodifiableView() {
        Investment investment = Investment.confirm(USER_ID, USER_ASSET_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, BigDecimal.valueOf(30_000));

        List<InvestmentSector> sectors = investment.getSectors();

        assertThrows(UnsupportedOperationException.class, () -> sectors.add(null));
    }
}
