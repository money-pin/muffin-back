package com.muffin.investment.domain.investment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private static final LocalDate INVEST_DATE = LocalDate.of(2026, 5, 7);

    @Test
    @DisplayName("섹터를 추가하면 총투자금은 섹터 금액의 합이 된다")
    void addSector_totalAmountEqualsSumOfSectorAmounts() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);

        investment.addSector(100L, 10, 300_000L, BigDecimal.valueOf(30_000));
        investment.addSector(200L, 5, 200_000L, BigDecimal.valueOf(40_000));

        assertEquals(500_000L, investment.getTotalAmount());
        assertEquals(2, investment.getSectors().size());
    }

    @Test
    @DisplayName("스냅샷을 찍고 정산하면 섹터 손익이 계산되고 총손익이 합산되며 상태가 SETTLED가 된다")
    void settle_computesFromSnapshotAndAggregates() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, null);
        investment.addSector(200L, 5, 200_000L, null);

        // buy=30,000 / sell=31,800 → +6% → 300,000×6% = 18,000
        investment.stampSectorNormal(100L, BigDecimal.valueOf(30_000), BigDecimal.valueOf(31_800));
        // buy=40,000 / sell=40,400 → +1% → 200,000×1% = 2,000
        investment.stampSectorNormal(200L, BigDecimal.valueOf(40_000), BigDecimal.valueOf(40_400));

        LocalDateTime settledAt = LocalDateTime.of(2026, 5, 8, 9, 30);
        investment.computeSectorResults();
        investment.settle(settledAt);

        InvestmentSector first = investment.getSectors().getFirst();
        assertEquals(18_000L, first.getProfitLoss());
        assertEquals(0, new BigDecimal("6.0000").compareTo(first.getProfitLossRate()));
        assertEquals(PriceDataSource.NORMAL, first.getPriceDataSource());
        // 총손익 20,000 / 총투자금 500,000 = 4.0000%
        assertEquals(20_000L, investment.getTotalProfitLoss());
        assertEquals(0, new BigDecimal("4.0000").compareTo(investment.getTotalProfitLossRate()));
        assertEquals(SettlementStatus.SETTLED, investment.getSettlementStatus());
        assertEquals(settledAt, investment.getSettledAt());
    }

    @Test
    @DisplayName("하락 스냅샷은 음수 손익으로 정산된다")
    void settle_negativeProfitWhenSellBelowBuy() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, null);

        // buy=30,000 / sell=28,500 → -5% → 300,000×-5% = -15,000
        investment.stampSectorNormal(100L, BigDecimal.valueOf(30_000), BigDecimal.valueOf(28_500));
        investment.computeSectorResults();
        investment.settle(LocalDateTime.of(2026, 5, 8, 9, 30));

        assertEquals(-15_000L, investment.getTotalProfitLoss());
        assertEquals(0, new BigDecimal("-5.0000").compareTo(investment.getTotalProfitLossRate()));
    }

    @Test
    @DisplayName("FALLBACK_ZERO 스냅샷은 손익 0/손익률 0으로 정산된다")
    void settle_fallbackSectorYieldsZero() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, null);

        investment.stampSectorFallback(100L, BigDecimal.valueOf(30_000));
        investment.computeSectorResults();
        investment.settle(LocalDateTime.of(2026, 5, 8, 9, 30));

        InvestmentSector sector = investment.getSectors().getFirst();
        assertEquals(0L, sector.getProfitLoss());
        assertEquals(0, BigDecimal.ZERO.compareTo(sector.getProfitLossRate()));
        assertEquals(PriceDataSource.FALLBACK_ZERO, sector.getPriceDataSource());
        assertEquals(0L, investment.getTotalProfitLoss());
    }

    @Test
    @DisplayName("정상 섹터와 폴백 섹터가 섞이면 정상분만 손익에 반영된다")
    void settle_mixedNormalAndFallback() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, null);
        investment.addSector(200L, 5, 200_000L, null);

        investment.stampSectorNormal(100L, BigDecimal.valueOf(30_000), BigDecimal.valueOf(31_800)); // +18,000
        investment.stampSectorFallback(200L, BigDecimal.valueOf(40_000)); // 0
        investment.computeSectorResults();
        investment.settle(LocalDateTime.of(2026, 5, 8, 9, 30));

        assertEquals(18_000L, investment.getTotalProfitLoss());
    }

    @Test
    @DisplayName("정상 스냅샷을 매수가 없이 찍으려 하면 예외가 발생한다")
    void stampSectorNormal_throwsWhenBuyPriceMissing() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> investment.stampSectorNormal(100L, null, BigDecimal.valueOf(31_800)));
    }

    @Test
    @DisplayName("스냅샷이 채워지지 않은 섹터로 정산하면 예외가 발생한다")
    void settle_throwsWhenSnapshotMissing() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, null); // 스냅샷 미기록

        assertThrows(IllegalStateException.class, () -> investment.settle(LocalDateTime.of(2026, 5, 8, 9, 0)));
    }

    @Test
    @DisplayName("존재하지 않는 섹터에 스냅샷을 반영하면 예외가 발생한다")
    void stampSectorNormal_throwsWhenSectorNotFound() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, BigDecimal.valueOf(30_000));

        assertThrows(
                IllegalArgumentException.class,
                () -> investment.stampSectorNormal(999L, BigDecimal.valueOf(30_000), BigDecimal.valueOf(31_800)));
    }

    @Test
    @DisplayName("투자하지 않은 경우 상태는 NO_INVEST, 정산 상태는 PENDING, 총투자금은 0 이다")
    void noInvest_hasNoInvestStatusAndZeroTotalAmount() {
        Investment investment = Investment.noInvest(USER_ID, INVEST_DATE);

        assertEquals(InvestmentStatus.NO_INVEST, investment.getStatus());
        assertEquals(SettlementStatus.PENDING, investment.getSettlementStatus());
        assertEquals(0L, investment.getTotalAmount());
    }

    @Test
    @DisplayName("정산에 실패하면 상태가 FAILED 가 된다")
    void failSettlement_setsFailedStatus() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);

        investment.failSettlement();

        assertEquals(SettlementStatus.FAILED, investment.getSettlementStatus());
    }

    @Test
    @DisplayName("정산 창을 놓친 확정 투자를 취소하면 손익 0/상태 CANCELLED가 된다")
    void cancelSettlement_zeroProfitAndCancelledStatus() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, null);
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 5, 10, 9, 30);

        investment.cancelSettlement(cancelledAt);

        assertEquals(0L, investment.getTotalProfitLoss());
        assertEquals(SettlementStatus.CANCELLED, investment.getSettlementStatus());
        assertEquals(cancelledAt, investment.getSettledAt());
    }

    @Test
    @DisplayName("getSectors 로 얻은 컬렉션은 외부에서 직접 수정할 수 없다")
    void getSectors_returnsUnmodifiableView() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 10, 300_000L, BigDecimal.valueOf(30_000));

        List<InvestmentSector> sectors = investment.getSectors();

        assertThrows(UnsupportedOperationException.class, () -> sectors.add(null));
    }

    @Test
    @DisplayName("투자 수정은 기존 섹터를 전부 교체하고 총투자금을 다시 계산한다")
    void replaceSectors_replacesAllAndRecalculatesTotal() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 1, 100_000L, null);

        investment.replaceSectors(List.of(
                new Investment.SectorAllocation(200L, 2, 200_000L),
                new Investment.SectorAllocation(300L, 3, 300_000L)));

        assertEquals(2, investment.getSectors().size());
        assertEquals(500_000L, investment.getTotalAmount());
        assertEquals(
                List.of(200L, 300L),
                investment.getSectors().stream()
                        .map(InvestmentSector::getSectorId)
                        .toList());
    }

    @Test
    @DisplayName("자정 마감은 finalizedAt만 기록하고 가격은 건드리지 않는다(멱등)")
    void finalizeInvestment_freezesOnly() {
        Investment investment = Investment.confirm(USER_ID, INVEST_DATE);
        investment.addSector(100L, 1, 100_000L, null);
        LocalDateTime finalizedAt = INVEST_DATE.plusDays(1).atStartOfDay();

        investment.finalizeInvestment(finalizedAt);

        assertEquals(finalizedAt, investment.getFinalizedAt());
        assertNull(investment.getSectors().getFirst().getBuyPrice()); // 가격 스냅샷은 정산 phase 1의 책임
        assertEquals(SettlementStatus.PENDING, investment.getSettlementStatus());

        // 멱등: 재호출해도 최초 finalizedAt을 유지한다.
        investment.finalizeInvestment(finalizedAt.plusMinutes(10));
        assertEquals(finalizedAt, investment.getFinalizedAt());
    }

    @Test
    @DisplayName("미투자 레코드 마감은 finalizedAt만 기록한다")
    void finalizeNoInvest_recordsFinalizedAt() {
        Investment investment = Investment.noInvest(USER_ID, INVEST_DATE);
        LocalDateTime finalizedAt = INVEST_DATE.plusDays(1).atStartOfDay();

        investment.finalizeNoInvest(finalizedAt);

        assertEquals(finalizedAt, investment.getFinalizedAt());
        assertEquals(InvestmentStatus.NO_INVEST, investment.getStatus());
    }
}
