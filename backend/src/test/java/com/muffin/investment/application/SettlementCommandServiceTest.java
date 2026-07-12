package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.PriceDataSource;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.investment.domain.profitsummary.ProfitSummary;
import com.muffin.investment.domain.profitsummary.ProfitSummaryRepository;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 정산 배치 오케스트레이터의 유저별 트랜잭션/멱등/실패 격리/폴백 동작을 실제 저장소로 검증한다. */
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
    private ProfitSummaryRepository profitSummaryRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @Autowired
    private EtfPriceRepository etfPriceRepository;

    @AfterEach
    void cleanUp() {
        investmentRepository.deleteAll();
        profitSummaryRepository.deleteAll();
        userAssetRepository.deleteAll();
        etfPriceRepository.deleteAll();
        sectorRepository.deleteAll();
    }

    @Test
    @DisplayName("정산하면 investment/user_asset/profit_summary 세 테이블이 총손익 기준으로 함께 갱신된다")
    void settle_updatesThreeTablesOnProfit() {
        long etfId = 7L;
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "테크", "d", "TECH"));
        etfPriceRepository.save(EtfPrice.create(etfId, SETTLE_DATE, 31_800L, 32_000L)); // 당일 시가 31,800
        UserAsset asset = userAssetRepository.save(UserAsset.create(1L, 1_000_000L));
        Long investmentId = saveInvestment(1L, asset.getId(), sector.getId(), 300_000L, 30_000);

        settlementCommandService.settle(SETTLE_DATE);

        Investment settled = investmentRepository.findById(investmentId).orElseThrow();
        assertEquals(SettlementStatus.SETTLED, settled.getSettlementStatus());
        assertEquals(18_000L, settled.getTotalProfitLoss()); // 300,000 × 6%

        UserAsset updated = userAssetRepository.findById(asset.getId()).orElseThrow();
        assertEquals(1_018_000L, updated.getTotalAsset());
        assertEquals(18_000L, updated.getDailyChangeAmount());
        assertNotNull(updated.getLastSettledAt());

        ProfitSummary summary = profitSummaryRepository
                .findByUserIdAndSummaryDate(1L, INVEST_DATE)
                .orElseThrow();
        assertEquals(18_000L, summary.getDailyProfitLoss());
        assertEquals(18_000L, summary.getCumulativeProfitLoss());
    }

    @Test
    @DisplayName("이미 SETTLED 된 건은 재실행해도 대상에서 제외되어 손익이 이중 반영되지 않는다")
    void settle_isIdempotentOnRerun() {
        long etfId = 7L;
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "테크", "d", "TECH"));
        etfPriceRepository.save(EtfPrice.create(etfId, SETTLE_DATE, 31_800L, 32_000L));
        UserAsset asset = userAssetRepository.save(UserAsset.create(1L, 1_000_000L));
        saveInvestment(1L, asset.getId(), sector.getId(), 300_000L, 30_000);

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
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "테크", "d", "TECH"));
        etfPriceRepository.save(EtfPrice.create(etfId, SETTLE_DATE, 31_800L, 32_000L));
        UserAsset assetA = userAssetRepository.save(UserAsset.create(1L, 1_000_000L));
        Long okId = saveInvestment(1L, assetA.getId(), sector.getId(), 300_000L, 30_000);
        // user_asset_id 가 존재하지 않아 처리 중 예외 → 해당 건만 실패
        Long failId = saveInvestment(2L, 999_999L, sector.getId(), 300_000L, 30_000);

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
    @DisplayName("섹터의 ETF 시세가 없으면 해당 섹터는 FALLBACK_ZERO(0%)로 정산된다")
    void settle_appliesFallbackWhenEtfPriceMissing() {
        // 다른 ETF 시세가 적재돼 있어 적재 가드는 통과하지만, 이 섹터의 ETF 시세는 없음
        etfPriceRepository.save(EtfPrice.create(7L, SETTLE_DATE, 100L, 100L));
        Sector sector = sectorRepository.save(Sector.create(1L, 8L, "바이오", "d", "BIO"));
        UserAsset asset = userAssetRepository.save(UserAsset.create(3L, 1_000_000L));
        Long investmentId = saveInvestment(3L, asset.getId(), sector.getId(), 300_000L, 30_000);

        settlementCommandService.settle(SETTLE_DATE);

        Investment settled =
                investmentRepository.findWithSectorsById(investmentId).orElseThrow();
        assertEquals(SettlementStatus.SETTLED, settled.getSettlementStatus());
        assertEquals(0L, settled.getTotalProfitLoss());
        assertEquals(PriceDataSource.FALLBACK_ZERO, settled.getSectors().getFirst().getPriceDataSource());
        assertEquals(
                1_000_000L,
                userAssetRepository.findById(asset.getId()).orElseThrow().getTotalAsset());
    }

    @Test
    @DisplayName("ETF 시세가 아직 적재되지 않았으면 정산을 건너뛰고 상태는 PENDING 을 유지한다")
    void settle_skipsWhenPricesNotLoaded() {
        UserAsset asset = userAssetRepository.save(UserAsset.create(4L, 1_000_000L));
        Long investmentId = saveInvestment(4L, asset.getId(), createSector(), 300_000L, 30_000);

        SettlementBatchResult result = settlementCommandService.settle(SETTLE_DATE);

        assertFalse(result.ready());
        assertEquals(
                SettlementStatus.PENDING,
                investmentRepository.findById(investmentId).orElseThrow().getSettlementStatus());
    }

    @Test
    @DisplayName("투자하지 않은 날(NO_INVEST)은 NO_SETTLEMENT로 종료되고 profit_summary에 0행이 남으며 자산은 그대로다")
    void settle_recordsZeroForNoInvest() {
        etfPriceRepository.save(EtfPrice.create(7L, SETTLE_DATE, 100L, 100L)); // 적재 가드 통과용
        UserAsset asset = userAssetRepository.save(UserAsset.create(5L, 1_000_000L));
        Long investmentId = investmentRepository
                .save(Investment.noInvest(5L, asset.getId(), INVEST_DATE))
                .getId();

        settlementCommandService.settle(SETTLE_DATE);

        Investment processed = investmentRepository.findById(investmentId).orElseThrow();
        assertEquals(SettlementStatus.NO_SETTLEMENT, processed.getSettlementStatus());
        ProfitSummary summary = profitSummaryRepository
                .findByUserIdAndSummaryDate(5L, INVEST_DATE)
                .orElseThrow();
        assertEquals(0L, summary.getDailyProfitLoss());
        assertEquals(
                1_000_000L,
                userAssetRepository.findById(asset.getId()).orElseThrow().getTotalAsset());
    }

    @Test
    @DisplayName("정산 창을 놓친 오래된 확정 투자는 CANCELLED 되고 자산은 그대로, profit_summary에 0행이 남는다")
    void settle_cancelsStaleConfirmed() {
        long etfId = 7L;
        Sector sector = sectorRepository.save(Sector.create(1L, etfId, "테크", "d", "TECH"));
        etfPriceRepository.save(EtfPrice.create(etfId, SETTLE_DATE, 100L, 100L)); // 05-08 = settlementDate
        etfPriceRepository.save(EtfPrice.create(etfId, INVEST_DATE, 100L, 100L)); // 05-07 = 직전 거래일
        UserAsset asset = userAssetRepository.save(UserAsset.create(6L, 1_000_000L));
        LocalDate staleDate = INVEST_DATE.minusDays(2); // 05-05, 직전 거래일(05-07)보다 이름 → 정산 창 놓침
        Investment stale = Investment.confirm(6L, asset.getId(), staleDate);
        stale.addSector(sector.getId(), 10, 300_000L, BigDecimal.valueOf(30_000));
        Long investmentId = investmentRepository.save(stale).getId();

        settlementCommandService.settle(SETTLE_DATE);

        Investment processed = investmentRepository.findById(investmentId).orElseThrow();
        assertEquals(SettlementStatus.CANCELLED, processed.getSettlementStatus());
        assertEquals(0L, processed.getTotalProfitLoss());
        assertEquals(
                1_000_000L,
                userAssetRepository.findById(asset.getId()).orElseThrow().getTotalAsset());
        ProfitSummary summary = profitSummaryRepository
                .findByUserIdAndSummaryDate(6L, staleDate)
                .orElseThrow();
        assertEquals(0L, summary.getDailyProfitLoss());
    }

    private Long createSector() {
        return sectorRepository.save(Sector.create(1L, 9L, "금융", "d", "FIN")).getId();
    }

    private Long saveInvestment(Long userId, Long userAssetId, Long sectorId, long amount, int buyPrice) {
        Investment investment = Investment.confirm(userId, userAssetId, INVEST_DATE);
        investment.addSector(sectorId, 10, amount, BigDecimal.valueOf(buyPrice));
        return investmentRepository.save(investment).getId();
    }
}
