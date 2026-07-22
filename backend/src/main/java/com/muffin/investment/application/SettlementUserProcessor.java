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
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
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
 *
 * <p>모든 쓰기 메서드는 {@code REQUIRES_NEW}로 각 사용자마다 독립 트랜잭션에서 커밋한다. 이벤트 트리거가 {@code AFTER_COMMIT}로 실행될 때 발행 측의
 * 이미 커밋된 트랜잭션 리소스에 참여해 쓰기가 반영되지 않는 문제를 피하고, 사용자별 실패 격리도 보장한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementUserProcessor {

    private final InvestmentRepository investmentRepository;
    private final UserAssetRepository userAssetRepository;
    private final ProfitSummaryRepository profitSummaryRepository;
    private final Clock clock;

    /**
     * 유저 1명의 확정 투자를 정산한다.
     *
     * @param investmentId 정산 대상 Investment id
     * @param sectorToEtfId sectorId → etfId 매핑
     * @param etfPriceByEtfId etfId → 당일 ETF 시세(해당 정산 일자)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
            } else if (isFinalMissing(price)) {
                // 10:00까지 시가 미확보 → 0% 폴백
                investment.settleSectorFallback(sector.getSectorId());
            } else {
                throw new IllegalStateException("정산 가능한 ETF 시가 상태가 아닙니다: etfId=" + etfId + ", status="
                        + (price == null ? null : price.getStartPriceStatus()));
            }
        }

        LocalDateTime settledAt = LocalDateTime.now(clock);
        investment.settle(settledAt);

        applyToUserAsset(investment, settledAt);
        upsertProfitSummary(investment);
    }

    /** 정산 창을 놓친 확정 투자를 취소한다. 자산은 건드리지 않고 profit_summary에 0행만 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancel(Long investmentId) {
        Investment investment = investmentRepository
                .findById(investmentId)
                .orElseThrow(() -> new IllegalStateException("취소 대상 투자를 찾을 수 없습니다: id=" + investmentId));
        if (isTerminal(investment)) {
            return;
        }
        LocalDateTime settledAt = LocalDateTime.now(clock);
        investment.cancelSettlement(settledAt);
        applyNoChangeToUserAsset(investment, settledAt); // 총자산 유지, 일간 변동 0/정산 시각 갱신
        upsertProfitSummary(investment); // 손익 0행
    }

    /** 투자하지 않은 날(NO_INVEST)을 종료 처리한다. 자산은 그대로, profit_summary에 0행만 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordNoSettlement(Long investmentId) {
        Investment investment = investmentRepository
                .findById(investmentId)
                .orElseThrow(() -> new IllegalStateException("미투자 처리 대상을 찾을 수 없습니다: id=" + investmentId));
        if (isTerminal(investment)) {
            return;
        }
        LocalDateTime settledAt = LocalDateTime.now(clock);
        investment.markNoSettlement(settledAt);
        applyNoChangeToUserAsset(investment, settledAt); // 총자산 유지, 일간 변동 0/정산 시각 갱신
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
        UserAsset asset = loadUserAsset(investment);
        long before = asset.getTotalAsset();
        long profit = investment.getTotalProfitLoss();
        // 일간 등락률은 "투자금 대비"(investment.totalProfitLossRate)가 아니라 "전일 총자산 대비"여야 한다.
        BigDecimal changeRate = (before == 0)
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(profit)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(before), 4, RoundingMode.HALF_UP);
        asset.applySettlement(profit, changeRate, settledAt);

        // 사후조건: 자산 증가분은 정확히 총손익과 같아야 한다(테이블 간 정합성 검증).
        long actualDelta = asset.getTotalAsset() - before;
        if (actualDelta != profit) {
            log.error(
                    "[settlement] asset mismatch userId={} expectedDelta={} actualDelta={}",
                    investment.getUserId(),
                    profit,
                    actualDelta);
        }
    }

    /** 손익 없는 종료(취소/미투자)에도 자산의 일간 변동을 0으로, 최근 정산 시각을 갱신한다(총자산은 유지). */
    private void applyNoChangeToUserAsset(Investment investment, LocalDateTime settledAt) {
        loadUserAsset(investment).markNoChange(settledAt);
    }

    private UserAsset loadUserAsset(Investment investment) {
        return userAssetRepository
                .findById(investment.getUserAssetId())
                .orElseThrow(
                        () -> new IllegalStateException("사용자 자산이 없습니다: userAssetId=" + investment.getUserAssetId()));
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
        return price != null
                && price.getStartPriceStatus() == PriceCollectionStatus.SUCCESS
                && price.getStartPrice() != null
                && price.getStartPrice() > 0;
    }

    private boolean isFinalMissing(EtfPrice price) {
        return price != null && price.getStartPriceStatus() == PriceCollectionStatus.FINAL_MISSING;
    }
}
