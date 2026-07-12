package com.muffin.investment.application;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.InvestmentSector;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.investment.domain.profitsummary.ProfitSummary;
import com.muffin.investment.domain.profitsummary.ProfitSummaryRepository;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.sector.domain.etfprice.EtfPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산의 정합성 경계인 "유저 1명" 단위 처리기.
 *
 * <p>{@link #settle}는 한 트랜잭션 안에서 investment_sector + investment + user_asset + profit_summary를 원자적으로 갱신한다. 손익은
 * investment_sector에서 한 번만 계산해(investment.settle로 합산) 나머지 테이블로 팬아웃한다. 실패 격리를 위해 오케스트레이터가 유저별로 이 메서드를 호출하며,
 * 실패 시 {@link #markFailed}로 해당 건만 FAILED 처리한다. (별도 빈으로 두어야 트랜잭션 프록시가 유저별로 적용된다.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementUserProcessor {

    private final InvestmentRepository investmentRepository;
    private final UserAssetRepository userAssetRepository;
    private final ProfitSummaryRepository profitSummaryRepository;

    /**
     * 유저 1명의 확정 투자를 정산한다.
     *
     * @param investmentId 정산 대상 Investment id
     * @param sectorToEtfId sectorId → etfId 매핑
     * @param etfPriceByEtfId etfId → 당일 ETF 시세(해당 정산 일자)
     */
    @Transactional
    public void settle(Long investmentId, Map<Long, Long> sectorToEtfId, Map<Long, EtfPrice> etfPriceByEtfId) {
        Investment investment = investmentRepository
                .findWithSectorsById(investmentId)
                .orElseThrow(() -> new IllegalStateException("정산 대상 투자를 찾을 수 없습니다: id=" + investmentId));

        // 멱등: 이미 종료된 건은 재실행/경쟁 상황에서도 다시 처리하지 않는다.
        if (isTerminal(investment)) {
            return;
        }

        for (InvestmentSector sector : investment.getSectors()) {
            Long etfId = sectorToEtfId.get(sector.getSectorId());
            EtfPrice price = (etfId == null) ? null : etfPriceByEtfId.get(etfId);
            if (isUsable(price)) {
                investment.settleSector(sector.getSectorId(), BigDecimal.valueOf(price.getStartPrice()));
            } else {
                // ETF 시세 미적재/거래정지 → 0% 폴백
                investment.settleSectorFallback(sector.getSectorId());
            }
        }

        LocalDateTime settledAt = LocalDateTime.now();
        investment.settle(settledAt);

        applyToUserAsset(investment, settledAt);
        upsertProfitSummary(investment);
    }

    /** 정산 창을 놓친 확정 투자를 취소한다. 자산은 건드리지 않고 profit_summary에 0행만 남긴다. */
    @Transactional
    public void cancel(Long investmentId) {
        Investment investment = investmentRepository
                .findById(investmentId)
                .orElseThrow(() -> new IllegalStateException("취소 대상 투자를 찾을 수 없습니다: id=" + investmentId));
        if (isTerminal(investment)) {
            return;
        }
        investment.cancelSettlement(LocalDateTime.now());
        upsertProfitSummary(investment); // 손익 0행
    }

    /** 투자하지 않은 날(NO_INVEST)을 종료 처리한다. 자산은 그대로, profit_summary에 0행만 남긴다. */
    @Transactional
    public void recordNoSettlement(Long investmentId) {
        Investment investment = investmentRepository
                .findById(investmentId)
                .orElseThrow(() -> new IllegalStateException("미투자 처리 대상을 찾을 수 없습니다: id=" + investmentId));
        if (isTerminal(investment)) {
            return;
        }
        investment.markNoSettlement(LocalDateTime.now());
        upsertProfitSummary(investment); // 손익 0행
    }

    /** 정산 실패 건을 FAILED로 표시한다. settle 트랜잭션이 롤백된 뒤 별도 트랜잭션으로 커밋한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long investmentId) {
        investmentRepository.findById(investmentId).ifPresent(Investment::failSettlement);
    }

    private boolean isTerminal(Investment investment) {
        SettlementStatus status = investment.getSettlementStatus();
        return status == SettlementStatus.SETTLED
                || status == SettlementStatus.CANCELLED
                || status == SettlementStatus.NO_SETTLEMENT;
    }

    private void applyToUserAsset(Investment investment, LocalDateTime settledAt) {
        UserAsset asset = userAssetRepository
                .findById(investment.getUserAssetId())
                .orElseThrow(
                        () -> new IllegalStateException("사용자 자산이 없습니다: userAssetId=" + investment.getUserAssetId()));
        long before = asset.getTotalAsset();
        asset.applySettlement(investment.getTotalProfitLoss(), investment.getTotalProfitLossRate(), settledAt);

        // 사후조건: 자산 증가분은 정확히 총손익과 같아야 한다(테이블 간 정합성 검증).
        long actualDelta = asset.getTotalAsset() - before;
        if (actualDelta != investment.getTotalProfitLoss()) {
            log.error(
                    "[settlement] asset mismatch userId={} expectedDelta={} actualDelta={}",
                    investment.getUserId(),
                    investment.getTotalProfitLoss(),
                    actualDelta);
        }
    }

    private void upsertProfitSummary(Investment investment) {
        LocalDate summaryDate = investment.getInvestDate();
        long prevCumulative = profitSummaryRepository
                .findTopByUserIdAndSummaryDateLessThanOrderBySummaryDateDesc(investment.getUserId(), summaryDate)
                .map(ProfitSummary::getCumulativeProfitLoss)
                .orElse(0L);
        long cumulative = prevCumulative + investment.getTotalProfitLoss();

        profitSummaryRepository
                .findByUserIdAndSummaryDate(investment.getUserId(), summaryDate)
                .ifPresentOrElse(
                        existing -> existing.update(
                                investment.getTotalProfitLoss(), investment.getTotalProfitLossRate(), cumulative),
                        () -> profitSummaryRepository.save(ProfitSummary.create(
                                investment.getUserId(),
                                summaryDate,
                                investment.getTotalProfitLoss(),
                                investment.getTotalProfitLossRate(),
                                cumulative)));
    }

    private boolean isUsable(EtfPrice price) {
        return price != null && !price.isFallback() && price.getStartPrice() != null && price.getStartPrice() > 0;
    }
}
