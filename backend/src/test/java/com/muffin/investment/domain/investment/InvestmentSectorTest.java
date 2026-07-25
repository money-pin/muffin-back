package com.muffin.investment.domain.investment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.investment.domain.investment.enums.PriceDataSource;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** InvestmentSector의 스냅샷 스탬프(phase 1)와 손익 계산(phase 2)을 세밀하게 검증한다. */
class InvestmentSectorTest {

    private static final Long SECTOR_ID = 100L;

    private InvestmentSector sector(long amount) {
        return new InvestmentSector(SECTOR_ID, 10, amount, null);
    }

    @Nested
    @DisplayName("stampNormal(phase 1)")
    class StampNormal {

        @Test
        @DisplayName("매수가·매도가·NORMAL을 기록하고 손익은 아직 비어 있다")
        void stampsPricesWithoutComputingProfit() {
            InvestmentSector s = sector(300_000L);

            s.stampNormal(BigDecimal.valueOf(30_000), BigDecimal.valueOf(31_800));

            assertEquals(BigDecimal.valueOf(30_000), s.getBuyPrice());
            assertEquals(BigDecimal.valueOf(31_800), s.getSellPrice());
            assertEquals(PriceDataSource.NORMAL, s.getPriceDataSource());
            assertNull(s.getProfitLoss()); // 손익 계산은 phase 2
        }

        @Test
        @DisplayName("매수가가 null/0/음수면 예외")
        void rejectsInvalidBuyPrice() {
            InvestmentSector s = sector(300_000L);
            assertThrows(IllegalArgumentException.class, () -> s.stampNormal(null, BigDecimal.valueOf(31_800)));
            assertThrows(
                    IllegalArgumentException.class, () -> s.stampNormal(BigDecimal.ZERO, BigDecimal.valueOf(31_800)));
            assertThrows(
                    IllegalArgumentException.class, () -> s.stampNormal(BigDecimal.valueOf(-1), BigDecimal.valueOf(1)));
        }

        @Test
        @DisplayName("매도가가 null/0/음수면 예외")
        void rejectsInvalidSellPrice() {
            InvestmentSector s = sector(300_000L);
            assertThrows(IllegalArgumentException.class, () -> s.stampNormal(BigDecimal.valueOf(30_000), null));
            assertThrows(
                    IllegalArgumentException.class, () -> s.stampNormal(BigDecimal.valueOf(30_000), BigDecimal.ZERO));
        }
    }

    @Nested
    @DisplayName("stampFallback(phase 1)")
    class StampFallback {

        @Test
        @DisplayName("매수가가 있으면 매도가를 그와 동일하게 두고 FALLBACK_ZERO로 기록한다")
        void stampsWithBuyPrice() {
            InvestmentSector s = sector(300_000L);

            s.stampFallback(BigDecimal.valueOf(30_000));

            assertEquals(BigDecimal.valueOf(30_000), s.getBuyPrice());
            assertEquals(BigDecimal.valueOf(30_000), s.getSellPrice());
            assertEquals(PriceDataSource.FALLBACK_ZERO, s.getPriceDataSource());
        }

        @Test
        @DisplayName("전일 종가(매수가)가 결손이어도 null로 FALLBACK_ZERO 기록이 가능하다")
        void stampsWithoutBuyPrice() {
            InvestmentSector s = sector(300_000L);

            s.stampFallback(null);

            assertNull(s.getBuyPrice());
            assertNull(s.getSellPrice());
            assertEquals(PriceDataSource.FALLBACK_ZERO, s.getPriceDataSource());
        }
    }

    @Nested
    @DisplayName("computeResult(phase 2)")
    class ComputeResult {

        @Test
        @DisplayName("NORMAL: 손익률=(매도-매수)/매수×100, 손익=round(투자금×(매도-매수)/매수)")
        void computesNormalProfit() {
            InvestmentSector s = sector(300_000L);
            s.stampNormal(BigDecimal.valueOf(30_000), BigDecimal.valueOf(31_800));

            s.computeResult();

            assertEquals(18_000L, s.getProfitLoss());
            assertEquals(0, new BigDecimal("6.0000").compareTo(s.getProfitLossRate()));
        }

        @Test
        @DisplayName("NORMAL: 나누어떨어지지 않으면 손익금은 HALF_UP 반올림된다")
        void roundsProfitHalfUp() {
            // buy=30,000 / sell=30,001 / amount=100,000 → 손익 = 100,000×1/30,000 = 3.333 → 3
            InvestmentSector s = sector(100_000L);
            s.stampNormal(BigDecimal.valueOf(30_000), BigDecimal.valueOf(30_001));

            s.computeResult();

            assertEquals(3L, s.getProfitLoss());
            assertEquals(0, new BigDecimal("0.0033").compareTo(s.getProfitLossRate()));
        }

        @Test
        @DisplayName("NORMAL: 매도가가 매수가보다 낮으면 음수 손익")
        void computesNegativeProfit() {
            // buy=30,000 / sell=28,500 / amount=300,000 → -5% → -15,000
            InvestmentSector s = sector(300_000L);
            s.stampNormal(BigDecimal.valueOf(30_000), BigDecimal.valueOf(28_500));

            s.computeResult();

            assertEquals(-15_000L, s.getProfitLoss());
            assertEquals(0, new BigDecimal("-5.0000").compareTo(s.getProfitLossRate()));
        }

        @Test
        @DisplayName("FALLBACK_ZERO(매수가 있음): 손익 0, 손익률 0")
        void fallbackWithBuyYieldsZero() {
            InvestmentSector s = sector(300_000L);
            s.stampFallback(BigDecimal.valueOf(30_000));

            s.computeResult();

            assertEquals(0L, s.getProfitLoss());
            assertEquals(0, BigDecimal.ZERO.compareTo(s.getProfitLossRate()));
        }

        @Test
        @DisplayName("FALLBACK_ZERO(매수가 결손): 매수가 없이도 손익 0으로 계산된다")
        void fallbackWithoutBuyYieldsZero() {
            InvestmentSector s = sector(300_000L);
            s.stampFallback(null);

            s.computeResult();

            assertEquals(0L, s.getProfitLoss());
            assertEquals(0, BigDecimal.ZERO.compareTo(s.getProfitLossRate()));
        }

        @Test
        @DisplayName("스냅샷이 채워지지 않았으면 예외")
        void throwsWhenSnapshotMissing() {
            InvestmentSector s = sector(300_000L); // 스탬프 안 함

            assertThrows(IllegalStateException.class, s::computeResult);
        }
    }
}
