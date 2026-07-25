package com.muffin.investment.application.settlement;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 phase 2(집계) 처리기.
 *
 * <p>investment_sector 스냅샷(매수가·매도가·출처)만 읽어 섹터 손익을 계산·합산하고 user_asset에 반영한다. EtfPrice를 보지 않는다. 정합성 경계 = 유저
 * 1명 = 단일 트랜잭션(investment_sector 손익 + investment 총손익 + user_asset을 원자적으로 갱신). 유저별 REQUIRES_NEW로 실패를 격리한다.
 *
 * <p>손익 없는 종료(취소/미투자)는 자산을 유지하고 일간 변동만 0으로 갱신한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementAggregationProcessor {

    private final InvestmentRepository investmentRepository;
    private final UserAssetRepository userAssetRepository;
    private final Clock clock;

    /** 스냅샷이 채워진 확정 투자를 집계 정산한다(phase 1 이후 호출). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void settle(Long investmentId) {
        Investment investment = investmentRepository
                .findWithSectorsById(investmentId)
                .orElseThrow(() -> new IllegalStateException("정산 대상 투자를 찾을 수 없습니다: id=" + investmentId));
        if (isTerminal(investment)) {
            return;
        }
        LocalDateTime settledAt = LocalDateTime.now(clock);
        investment.computeSectorResults(); // 스냅샷으로 섹터 손익 계산
        investment.settle(settledAt); // 총손익 집계 + SETTLED
        applyToUserAsset(investment, settledAt);
    }

    /** 정산 창을 놓친 확정 투자를 취소한다. 자산은 유지하고 일간 변동 0/정산 시각만 갱신한다. */
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
        applyNoChangeToUserAsset(investment, settledAt);
    }

    /** 투자하지 않은 날(NO_INVEST)을 종료 처리한다. 자산은 유지하고 일간 변동 0/정산 시각만 갱신한다. */
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
        applyNoChangeToUserAsset(investment, settledAt);
    }

    /** 정산 실패 건을 FAILED로 표시한다. 실패 트랜잭션이 롤백된 뒤 별도 트랜잭션으로 커밋한다. */
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
        // 일간 등락률은 "투자금 대비"가 아니라 "전일 총자산 대비"여야 한다.
        BigDecimal changeRate = (before == 0)
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(profit)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(before), 4, RoundingMode.HALF_UP);
        asset.applySettlement(profit, changeRate, settledAt);

        // 사후조건: 자산 증가분은 정확히 총손익과 같아야 한다(테이블 간 정합성 검증). 위반 시 잘못된 자산이 조용히 커밋되지 않도록
        // 예외를 던져 이 유저 트랜잭션을 롤백하고 FAILED 경로로 넘긴다.
        long actualDelta = asset.getTotalAsset() - before;
        if (actualDelta != profit) {
            log.error(
                    "[settlement] asset mismatch userId={} expectedDelta={} actualDelta={}",
                    investment.getUserId(),
                    profit,
                    actualDelta);
            throw new IllegalStateException("자산 반영 정합성 위반: userId=" + investment.getUserId() + ", expectedDelta="
                    + profit + ", actualDelta=" + actualDelta);
        }
    }

    private void applyNoChangeToUserAsset(Investment investment, LocalDateTime settledAt) {
        loadUserAsset(investment).markNoChange(settledAt);
    }

    private UserAsset loadUserAsset(Investment investment) {
        return userAssetRepository
                .findById(investment.getUserAssetId())
                .orElseThrow(
                        () -> new IllegalStateException("사용자 자산이 없습니다: userAssetId=" + investment.getUserAssetId()));
    }
}
