package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.muffin.investment.application.settlement.SettlementBatchResult;
import com.muffin.investment.application.settlement.SettlementCommandService;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.PriceDataSource;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 정산 배치 오케스트레이터를 실제 저장소로 검증한다.
 *
 * <p>매수가는 전일 거래일 종가(EtfPrice[prevTradingDay].endPrice), 매도가는 당일 시가(EtfPrice[settleDate].startPrice)를 phase
 * 1에서 스냅샷으로 찍고, phase 2가 그 스냅샷으로 집계한다. 유저별 트랜잭션/멱등/실패 격리/섹터별 폴백/휴장·미적재 스킵을 다양한 케이스로 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SettlementCommandServiceTest {

    private static final LocalDate INVEST_DATE = LocalDate.of(2026, 5, 7);
    private static final LocalDate SETTLE_DATE = INVEST_DATE.plusDays(1);

    @Autowired
    private SettlementCommandService settlementCommandService;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private UserAssetRepository userAssetRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @Autowired
    private EtfPriceRepository etfPriceRepository;

    @MockitoBean
    private TradingCalendarService tradingCalendarService;

    @BeforeEach
    void setUpCalendar() {
        when(tradingCalendarService.getCalendar(SETTLE_DATE))
                .thenReturn(new TradingCalendar(SETTLE_DATE, true, INVEST_DATE, SETTLE_DATE.plusDays(1)));
    }

    @AfterEach
    void cleanUp() {
        investmentRepository.deleteAll();
        userAssetRepository.deleteAll();
        etfPriceRepository.deleteAll();
        sectorRepository.deleteAll();
    }

    @Test
    @DisplayName("정산하면 investment와 user_asset이 총손익 기준으로 함께 갱신된다")
    void settle_updatesInvestmentAndUserAssetOnProfit() {
        long etfId = 7L;
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "테크", "d", "TECH", 1));
        etfPriceRepository.save(EtfPrice.create(etfId, INVEST_DATE, 30_000L, 30_000L)); // 전일 종가 30,000 = 매수가
        etfPriceRepository.save(EtfPrice.create(etfId, SETTLE_DATE, 31_800L, 32_000L)); // 당일 시가 31,800 = 매도가
        UserAsset asset = userAssetRepository.save(UserAsset.create(1L, 1_000_000L));
        Long investmentId = saveConfirmed(1L, sector.getId(), 300_000L);

        settlementCommandService.settle(SETTLE_DATE);

        Investment settled =
                investmentRepository.findWithSectorsById(investmentId).orElseThrow();
        assertEquals(SettlementStatus.SETTLED, settled.getSettlementStatus());
        assertEquals(18_000L, settled.getTotalProfitLoss()); // 300,000 × 6%
        assertEquals(PriceDataSource.NORMAL, settled.getSectors().getFirst().getPriceDataSource());

        UserAsset updated = userAssetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(1_018_000L, updated.getTotalAsset());
        assertEquals(18_000L, updated.getDailyChangeAmount());
        // 등락률은 전일 총자산(1,000,000) 대비: 18,000/1,000,000 = 1.8000%
        assertEquals(0, new BigDecimal("1.8000").compareTo(updated.getDailyChangeRate()));
        assertNotNull(updated.getLastSettledAt());
    }

    @Test
    @DisplayName("이미 SETTLED 된 건은 재실행해도 대상에서 제외되어 손익이 이중 반영되지 않는다")
    void settle_isIdempotentOnRerun() {
        long etfId = 7L;
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "테크", "d", "TECH", 1));
        etfPriceRepository.save(EtfPrice.create(etfId, INVEST_DATE, 30_000L, 30_000L));
        etfPriceRepository.save(EtfPrice.create(etfId, SETTLE_DATE, 31_800L, 32_000L));
        UserAsset asset = userAssetRepository.save(UserAsset.create(1L, 1_000_000L));
        saveConfirmed(1L, sector.getId(), 300_000L);

        settlementCommandService.settle(SETTLE_DATE);
        SettlementBatchResult rerun = settlementCommandService.settle(SETTLE_DATE);

        assertEquals(0, rerun.total()); // SETTLED 는 재처리 대상이 아님
        assertEquals(
                1_018_000L,
                userAssetRepository.findById(asset.getId()).orElseThrow().getTotalAsset());
    }

    @Test
    @DisplayName("한 유저 정산이 실패해도 다른 유저는 정산되고 실패 건만 FAILED 로 격리된다")
    void settle_isolatesFailurePerUser() {
        long etfId = 7L;
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "테크", "d", "TECH", 1));
        etfPriceRepository.save(EtfPrice.create(etfId, INVEST_DATE, 30_000L, 30_000L));
        etfPriceRepository.save(EtfPrice.create(etfId, SETTLE_DATE, 31_800L, 32_000L));
        UserAsset assetA = userAssetRepository.save(UserAsset.create(1L, 1_000_000L));
        Long okId = saveConfirmed(1L, sector.getId(), 300_000L);
        // userId=2 는 user_asset 행이 없어 phase 2에서 예외 → 해당 건만 실패
        Long failId = saveConfirmed(2L, sector.getId(), 300_000L);

        SettlementBatchResult result = settlementCommandService.settle(SETTLE_DATE);

        assertEquals(2, result.total());
        assertEquals(1, result.success());
        assertEquals(1, result.failed());
        assertEquals(
                SettlementStatus.SETTLED,
                investmentRepository.findById(okId).orElseThrow().getSettlementStatus());
        assertEquals(
                SettlementStatus.FAILED,
                investmentRepository.findById(failId).orElseThrow().getSettlementStatus());
    }

    @Test
    @DisplayName("10시까지 ETF 시가가 미확보(FINAL_MISSING)면 해당 섹터는 FALLBACK_ZERO(0%)로 정산된다")
    void settle_appliesFallbackWhenOpenPriceFinalMissing() {
        long etfId = 8L;
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "바이오", "d", "BIO", 1));
        etfPriceRepository.save(EtfPrice.create(etfId, INVEST_DATE, 100L, 100L)); // 전일 종가 100 = 매수가
        EtfPrice missingOpen = EtfPrice.pending(etfId, SETTLE_DATE);
        missingOpen.markOpenFinalMissing();
        etfPriceRepository.save(missingOpen);
        UserAsset asset = userAssetRepository.save(UserAsset.create(3L, 1_000_000L));
        Long investmentId = saveConfirmed(3L, sector.getId(), 300_000L);

        settlementCommandService.settle(SETTLE_DATE);

        Investment settled =
                investmentRepository.findWithSectorsById(investmentId).orElseThrow();
        assertEquals(SettlementStatus.SETTLED, settled.getSettlementStatus());
        assertEquals(0L, settled.getTotalProfitLoss());
        assertEquals(
                PriceDataSource.FALLBACK_ZERO, settled.getSectors().getFirst().getPriceDataSource());
        assertEquals(
                1_000_000L,
                userAssetRepository.findById(asset.getId()).orElseThrow().getTotalAsset());
    }

    @Test
    @DisplayName("정상 섹터와 폴백 섹터가 섞이면 정상분만 손익에 반영된다")
    void settle_mixedNormalAndFallbackSectors() {
        long techEtf = 7L;
        long bioEtf = 8L;
        Sector tech = sectorRepository.save(Sector.create(1L, techEtf, "테크", "d", "TECH", 1));
        Sector bio = sectorRepository.save(Sector.create(1L, bioEtf, "바이오", "d", "BIO", 2));
        etfPriceRepository.save(EtfPrice.create(techEtf, INVEST_DATE, 30_000L, 30_000L));
        etfPriceRepository.save(EtfPrice.create(techEtf, SETTLE_DATE, 31_800L, 32_000L)); // +6%
        etfPriceRepository.save(EtfPrice.create(bioEtf, INVEST_DATE, 100L, 100L));
        EtfPrice bioOpen = EtfPrice.pending(bioEtf, SETTLE_DATE);
        bioOpen.markOpenFinalMissing(); // 폴백
        etfPriceRepository.save(bioOpen);
        UserAsset asset = userAssetRepository.save(UserAsset.create(1L, 1_000_000L));
        Investment investment = Investment.confirm(1L, INVEST_DATE);
        investment.addSector(tech.getId(), 10, 300_000L, null); // +18,000
        investment.addSector(bio.getId(), 5, 200_000L, null); // 0 (폴백)
        Long investmentId = investmentRepository.save(investment).getId();

        settlementCommandService.settle(SETTLE_DATE);

        Investment settled =
                investmentRepository.findWithSectorsById(investmentId).orElseThrow();
        assertEquals(SettlementStatus.SETTLED, settled.getSettlementStatus());
        assertEquals(18_000L, settled.getTotalProfitLoss());
    }

    @Test
    @DisplayName("전일 종가(매수가)를 확보하지 못한 섹터는 FAILED가 아니라 0% 폴백으로 정산된다")
    void settle_fallbackWhenPrevCloseMissing() {
        long etfId = 7L;
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "테크", "d", "TECH", 1));
        etfPriceRepository.save(EtfPrice.create(etfId, SETTLE_DATE, 31_800L, 32_000L)); // 당일 시가만 있고
        // 전일 종가(EtfPrice[INVEST_DATE])는 적재되지 않음 → 매수가 결손 → 그 섹터 0% 폴백
        UserAsset asset = userAssetRepository.save(UserAsset.create(1L, 1_000_000L));
        Long investmentId = saveConfirmed(1L, sector.getId(), 300_000L);

        settlementCommandService.settle(SETTLE_DATE);

        Investment settled =
                investmentRepository.findWithSectorsById(investmentId).orElseThrow();
        assertEquals(SettlementStatus.SETTLED, settled.getSettlementStatus());
        assertEquals(0L, settled.getTotalProfitLoss());
        assertEquals(
                PriceDataSource.FALLBACK_ZERO, settled.getSectors().getFirst().getPriceDataSource());
        assertEquals(
                1_000_000L,
                userAssetRepository.findById(asset.getId()).orElseThrow().getTotalAsset());
    }

    @Test
    @DisplayName("ETF 시세가 아직 적재되지 않았으면(시가 행 없음) 정산을 건너뛰고 상태는 PENDING 을 유지한다")
    void settle_skipsWhenPricesNotLoaded() {
        UserAsset asset = userAssetRepository.save(UserAsset.create(4L, 1_000_000L));
        Long investmentId = saveConfirmed(4L, createSector(), 300_000L);

        SettlementBatchResult result = settlementCommandService.settle(SETTLE_DATE);

        assertFalse(result.ready());
        assertEquals(
                SettlementStatus.PENDING,
                investmentRepository.findById(investmentId).orElseThrow().getSettlementStatus());
    }

    @Test
    @DisplayName("휴장일이면 시가가 전부 MARKET_CLOSED라 정산을 건너뛰고 상태는 PENDING 을 유지한다")
    void settle_skipsOnHoliday() {
        long etfId = 7L;
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "테크", "d", "TECH", 1));
        EtfPrice marketClosed = EtfPrice.pending(etfId, SETTLE_DATE);
        marketClosed.markOpenMarketClosed();
        etfPriceRepository.save(marketClosed);
        UserAsset asset = userAssetRepository.save(UserAsset.create(4L, 1_000_000L));
        Long investmentId = saveConfirmed(4L, sector.getId(), 300_000L);

        SettlementBatchResult result = settlementCommandService.settle(SETTLE_DATE);

        assertFalse(result.ready());
        assertEquals(
                SettlementStatus.PENDING,
                investmentRepository.findById(investmentId).orElseThrow().getSettlementStatus());
    }

    @Test
    @DisplayName("시가 상태가 NO_DATA인 섹터는 스킵이 아니라 0% 폴백으로 정산된다(휴장 아님)")
    void settle_fallbackWhenOpenPriceNoData() {
        long etfId = 9L;
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "금융", "d", "FIN", 1));
        etfPriceRepository.save(EtfPrice.create(etfId, INVEST_DATE, 100L, 100L)); // 전일 종가
        EtfPrice noData = EtfPrice.pending(etfId, SETTLE_DATE);
        noData.markOpenNoData();
        etfPriceRepository.save(noData);
        UserAsset asset = userAssetRepository.save(UserAsset.create(4L, 1_000_000L));
        Long investmentId = saveConfirmed(4L, sector.getId(), 300_000L);

        settlementCommandService.settle(SETTLE_DATE);

        Investment settled =
                investmentRepository.findWithSectorsById(investmentId).orElseThrow();
        assertEquals(SettlementStatus.SETTLED, settled.getSettlementStatus());
        assertEquals(0L, settled.getTotalProfitLoss());
        assertEquals(
                PriceDataSource.FALLBACK_ZERO, settled.getSectors().getFirst().getPriceDataSource());
    }

    @Test
    @DisplayName("투자하지 않은 날(NO_INVEST)은 NO_SETTLEMENT로 종료되고 자산은 그대로다")
    void settle_recordsNoSettlementForNoInvest() {
        sectorRepository.save(Sector.create(1L, 7L, "테크", "d", "TECH", 1)); // 적재 가드용 활성 섹터
        etfPriceRepository.save(EtfPrice.create(7L, SETTLE_DATE, 100L, 100L)); // 적재 가드 통과용
        UserAsset asset = userAssetRepository.save(UserAsset.create(5L, 1_000_000L));
        Long investmentId =
                investmentRepository.save(Investment.noInvest(5L, INVEST_DATE)).getId();

        settlementCommandService.settle(SETTLE_DATE);

        Investment processed = investmentRepository.findById(investmentId).orElseThrow();
        assertEquals(SettlementStatus.NO_SETTLEMENT, processed.getSettlementStatus());
        UserAsset updatedAsset = userAssetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(1_000_000L, updatedAsset.getTotalAsset()); // 총자산 유지
        assertEquals(0L, updatedAsset.getDailyChangeAmount()); // 일간 변동 0으로 갱신
        assertNotNull(updatedAsset.getLastSettledAt()); // 정산 시각 갱신(정산 중 표시 해제)
    }

    @Test
    @DisplayName("정산 창을 놓친 오래된 확정 투자는 CANCELLED 되고 자산은 그대로다")
    void settle_cancelsStaleConfirmed() {
        long etfId = 7L;
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "테크", "d", "TECH", 1));
        etfPriceRepository.save(EtfPrice.create(etfId, SETTLE_DATE, 100L, 100L)); // 05-08 = settlementDate
        etfPriceRepository.save(EtfPrice.create(etfId, INVEST_DATE, 100L, 100L)); // 05-07 = 직전 거래일
        UserAsset asset = userAssetRepository.save(UserAsset.create(6L, 1_000_000L));
        LocalDate staleDate = INVEST_DATE.minusDays(2); // 05-05, 직전 거래일(05-07)보다 이름 → 정산 창 놓침
        Investment stale = Investment.confirm(6L, staleDate);
        stale.addSector(sector.getId(), 10, 300_000L, null);
        Long investmentId = investmentRepository.save(stale).getId();

        settlementCommandService.settle(SETTLE_DATE);

        Investment processed = investmentRepository.findById(investmentId).orElseThrow();
        assertEquals(SettlementStatus.CANCELLED, processed.getSettlementStatus());
        assertEquals(0L, processed.getTotalProfitLoss());
        UserAsset updatedAsset = userAssetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(1_000_000L, updatedAsset.getTotalAsset()); // 총자산 유지
        assertEquals(0L, updatedAsset.getDailyChangeAmount()); // 일간 변동 0으로 갱신
        assertNotNull(updatedAsset.getLastSettledAt()); // 정산 시각 갱신
    }

    private Long createSector() {
        return sectorRepository.save(Sector.create(1L, 9L, "금융", "d", "FIN", 1)).getId();
    }

    private Long saveConfirmed(Long userId, Long sectorId, long amount) {
        Investment investment = Investment.confirm(userId, INVEST_DATE);
        investment.addSector(sectorId, 10, amount, null); // 매수가는 정산 phase 1이 EtfPrice 종가로 스탬프
        return investmentRepository.save(investment).getId();
    }
}
