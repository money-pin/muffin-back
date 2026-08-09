package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.muffin.investment.application.settlement.SettlementSnapshotProcessor;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.InvestmentSector;
import com.muffin.investment.domain.investment.enums.PriceDataSource;
import com.muffin.sector.domain.etfprice.EtfPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 정산 phase 1: 섹터별로 매수가·매도가가 모두 SUCCESS면 NORMAL, 그 외에는 전부 FALLBACK(원금 0%)로 스탬프하는지 검증한다. 가격 문제로 예외를 던지지
 * 않는다.
 */
@ExtendWith(MockitoExtension.class)
class SettlementSnapshotProcessorTest {

    private static final Long INVESTMENT_ID = 1L;
    private static final Long SECTOR_ID = 100L;
    private static final Long ETF_ID = 7L;
    private static final LocalDate SETTLE_DATE = LocalDate.of(2026, 5, 8);
    private static final Map<Long, Long> SECTOR_TO_ETF = Map.of(SECTOR_ID, ETF_ID);
    private static final Map<Long, BigDecimal> CLOSE_30000 = Map.of(ETF_ID, BigDecimal.valueOf(30_000));

    @Mock
    private InvestmentRepository investmentRepository;

    private SettlementSnapshotProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SettlementSnapshotProcessor(investmentRepository);
    }

    private Investment confirmedWithSector(Long sectorId) {
        Investment investment = Investment.confirm(1L, LocalDate.of(2026, 5, 7));
        investment.addSector(sectorId, 10, 300_000L, null);
        return investment;
    }

    @Test
    @DisplayName("매수가·매도가가 모두 SUCCESS면 NORMAL로 스탬프한다")
    void stamp_normalWhenBothSuccess() {
        Investment investment = confirmedWithSector(SECTOR_ID);
        when(investmentRepository.findWithSectorsById(INVESTMENT_ID)).thenReturn(Optional.of(investment));
        Map<Long, EtfPrice> open = Map.of(ETF_ID, EtfPrice.open(ETF_ID, SETTLE_DATE, 31_800L));

        processor.stamp(INVESTMENT_ID, SECTOR_TO_ETF, CLOSE_30000, open);

        InvestmentSector sector = investment.getSectors().getFirst();
        assertEquals(BigDecimal.valueOf(30_000), sector.getBuyPrice());
        assertEquals(BigDecimal.valueOf(31_800), sector.getSellPrice());
        assertEquals(PriceDataSource.NORMAL, sector.getPriceDataSource());
        assertNull(sector.getProfitLoss()); // 손익 계산은 phase 2
    }

    @Test
    @DisplayName("매도가 FINAL_MISSING이면 그 섹터만 FALLBACK(매수가 유지)로 스탬프한다")
    void stamp_fallbackWhenFinalMissing() {
        Investment investment = confirmedWithSector(SECTOR_ID);
        when(investmentRepository.findWithSectorsById(INVESTMENT_ID)).thenReturn(Optional.of(investment));
        EtfPrice finalMissing = EtfPrice.pending(ETF_ID, SETTLE_DATE);
        finalMissing.markOpenFinalMissing();

        processor.stamp(INVESTMENT_ID, SECTOR_TO_ETF, CLOSE_30000, Map.of(ETF_ID, finalMissing));

        InvestmentSector sector = investment.getSectors().getFirst();
        assertEquals(BigDecimal.valueOf(30_000), sector.getBuyPrice());
        assertEquals(PriceDataSource.FALLBACK_ZERO, sector.getPriceDataSource());
    }

    @Test
    @DisplayName("매도가 NO_DATA면 FALLBACK으로 스탬프한다")
    void stamp_fallbackWhenOpenNoData() {
        Investment investment = confirmedWithSector(SECTOR_ID);
        when(investmentRepository.findWithSectorsById(INVESTMENT_ID)).thenReturn(Optional.of(investment));
        EtfPrice noData = EtfPrice.pending(ETF_ID, SETTLE_DATE);
        noData.markOpenNoData();

        processor.stamp(INVESTMENT_ID, SECTOR_TO_ETF, CLOSE_30000, Map.of(ETF_ID, noData));

        assertEquals(
                PriceDataSource.FALLBACK_ZERO,
                investment.getSectors().getFirst().getPriceDataSource());
    }

    @Test
    @DisplayName("당일 시가 행이 아예 없으면 FALLBACK으로 스탬프한다")
    void stamp_fallbackWhenOpenRowAbsent() {
        Investment investment = confirmedWithSector(SECTOR_ID);
        when(investmentRepository.findWithSectorsById(INVESTMENT_ID)).thenReturn(Optional.of(investment));

        processor.stamp(INVESTMENT_ID, SECTOR_TO_ETF, CLOSE_30000, Map.of());

        assertEquals(
                PriceDataSource.FALLBACK_ZERO,
                investment.getSectors().getFirst().getPriceDataSource());
    }

    @Test
    @DisplayName("전일 종가(매수가)가 결손이면 매수가 없이 FALLBACK으로 스탬프한다(전체 정산 실패 아님)")
    void stamp_fallbackWhenCloseMissing() {
        Investment investment = confirmedWithSector(SECTOR_ID);
        when(investmentRepository.findWithSectorsById(INVESTMENT_ID)).thenReturn(Optional.of(investment));
        Map<Long, EtfPrice> open = Map.of(ETF_ID, EtfPrice.open(ETF_ID, SETTLE_DATE, 31_800L));

        processor.stamp(INVESTMENT_ID, SECTOR_TO_ETF, Map.of(), open); // 종가 맵 비어 있음

        InvestmentSector sector = investment.getSectors().getFirst();
        assertNull(sector.getBuyPrice());
        assertEquals(PriceDataSource.FALLBACK_ZERO, sector.getPriceDataSource());
    }

    @Test
    @DisplayName("한 투자 안에서도 정상 섹터는 NORMAL, 미확보 섹터는 FALLBACK으로 각각 스탬프한다")
    void stamp_perSectorMix() {
        Investment investment = Investment.confirm(1L, LocalDate.of(2026, 5, 7));
        investment.addSector(100L, 10, 300_000L, null); // etf 7 정상
        investment.addSector(200L, 5, 200_000L, null); // etf 8 미확보
        when(investmentRepository.findWithSectorsById(INVESTMENT_ID)).thenReturn(Optional.of(investment));
        Map<Long, Long> sectorToEtf = Map.of(100L, 7L, 200L, 8L);
        Map<Long, BigDecimal> close = Map.of(7L, BigDecimal.valueOf(30_000), 8L, BigDecimal.valueOf(100));
        EtfPrice etf8FinalMissing = EtfPrice.pending(8L, SETTLE_DATE);
        etf8FinalMissing.markOpenFinalMissing();
        Map<Long, EtfPrice> open = Map.of(7L, EtfPrice.open(7L, SETTLE_DATE, 31_800L), 8L, etf8FinalMissing);

        processor.stamp(INVESTMENT_ID, sectorToEtf, close, open);

        assertEquals(PriceDataSource.NORMAL, investment.getSectors().get(0).getPriceDataSource());
        assertEquals(
                PriceDataSource.FALLBACK_ZERO, investment.getSectors().get(1).getPriceDataSource());
    }

    @Test
    @DisplayName("이미 종료된(SETTLED) 건은 다시 스탬프하지 않는다(멱등)")
    void stamp_skipsTerminalInvestment() {
        Investment settled = confirmedWithSector(SECTOR_ID);
        settled.stampSectorNormal(SECTOR_ID, BigDecimal.valueOf(30_000), BigDecimal.valueOf(31_800));
        settled.computeSectorResults();
        settled.settle(LocalDateTime.of(2026, 5, 8, 9, 30)); // SETTLED
        when(investmentRepository.findWithSectorsById(INVESTMENT_ID)).thenReturn(Optional.of(settled));

        // 빈 맵으로 호출해도 terminal이라 조기 반환하고 스냅샷을 덮어쓰지 않는다.
        processor.stamp(INVESTMENT_ID, SECTOR_TO_ETF, Map.of(), Map.of());

        assertEquals(31_800L, settled.getSectors().getFirst().getSellPrice().longValue());
    }
}
