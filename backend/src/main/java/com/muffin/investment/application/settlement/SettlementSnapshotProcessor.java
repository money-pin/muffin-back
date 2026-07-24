package com.muffin.investment.application.settlement;

import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.InvestmentSector;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 phase 1(스냅샷 스탬프) 처리기.
 *
 * <p>EtfPrice의 매수가(전일 종가)·매도가(당일 시가) status를 판단해 investment_sector에
 * buy_price/sell_price/price_data_source를 기록한다. 정산의 EtfPrice 의존은 이 단계에만 격리되고, phase 2 집계는 여기서 남긴 스냅샷만
 * 읽는다.
 *
 * <p>섹터별 판정: 매수가(전일 종가 SUCCESS)와 매도가(당일 시가 SUCCESS)가 <b>모두</b> 있으면 NORMAL, 그 외에는 전부
 * FALLBACK_ZERO(원금 그대로 0%)로 스탬프한다. 즉 매도가 FINAL_MISSING/미확보(NO_DATA/FAILED/PENDING)든 매수가 결손이든 그 섹터만 0%가 되고,
 * 나머지 정상 섹터는 그대로 정상 정산되어 합산된다. 가격 문제로 투자 전체가 실패하거나 취소되지 않는다.
 *
 * <p>휴장일(시가 MARKET_CLOSED)은 오케스트레이터가 대상에서 제외하므로 여기까지 오지 않는다.
 *
 * <p>유저별 독립 트랜잭션(REQUIRES_NEW): 한 유저 실패가 다른 유저에 번지지 않게 하고, 이벤트 트리거가 AFTER_COMMIT로 실행될 때 발행 측 트랜잭션에 얽히지 않게
 * 한다.
 */
@Component
@RequiredArgsConstructor
public class SettlementSnapshotProcessor {

    private final InvestmentRepository investmentRepository;

    /**
     * @param investmentId 스냅샷 대상 Investment id
     * @param sectorToEtfId sectorId → etfId
     * @param closePriceByEtfId etfId → 전일 종가(SUCCESS 확정분만). 매수가로 스탬프
     * @param openPriceByEtfId etfId → 당일 시가 EtfPrice(status 포함). 매도가로 판단
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stamp(
            Long investmentId,
            Map<Long, Long> sectorToEtfId,
            Map<Long, BigDecimal> closePriceByEtfId,
            Map<Long, EtfPrice> openPriceByEtfId) {
        Investment investment = investmentRepository
                .findWithSectorsById(investmentId)
                .orElseThrow(() -> new IllegalStateException("정산 대상 투자를 찾을 수 없습니다: id=" + investmentId));

        // 멱등: 이미 종료된 건은 재스탬프하지 않는다.
        if (isTerminal(investment)) {
            return;
        }

        for (InvestmentSector sector : investment.getSectors()) {
            Long etfId = sectorToEtfId.get(sector.getSectorId());
            BigDecimal buyPrice = (etfId == null) ? null : closePriceByEtfId.get(etfId);
            EtfPrice open = (etfId == null) ? null : openPriceByEtfId.get(etfId);

            if (buyPrice != null && isOpenSuccess(open)) {
                investment.stampSectorNormal(sector.getSectorId(), buyPrice, BigDecimal.valueOf(open.getStartPrice()));
            } else {
                // 매수가(전일 종가) 결손 또는 매도가(당일 시가) 미확보(FINAL_MISSING/NO_DATA/FAILED/PENDING) → 그 섹터만 0% 폴백.
                investment.stampSectorFallback(sector.getSectorId(), buyPrice);
            }
        }
    }

    private boolean isTerminal(Investment investment) {
        SettlementStatus status = investment.getSettlementStatus();
        return status == SettlementStatus.SETTLED
                || status == SettlementStatus.CANCELLED
                || status == SettlementStatus.NO_SETTLEMENT;
    }

    private boolean isOpenSuccess(EtfPrice open) {
        return open != null
                && open.getStartPriceStatus() == PriceCollectionStatus.SUCCESS
                && open.getStartPrice() != null
                && open.getStartPrice() > 0;
    }
}
