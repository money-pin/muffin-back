package com.muffin.investment.domain.investment;

import com.muffin.global.entity.BaseEntity;
import com.muffin.investment.domain.investment.enums.PriceDataSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 섹터별 투자 상세. Investment 애그리거트의 내부 엔티티로, 루트(Investment)를 통해서만 생성/수정된다. */
@Entity
@Getter
@Table(name = "investment_sector")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvestmentSector extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "investment_sector_id")
    private Long id;

    // 다른 애그리거트(Sector)는 ID로만 참조한다.
    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "buy_price", precision = 19, scale = 4)
    private BigDecimal buyPrice;

    @Column(name = "sell_price", precision = 19, scale = 4)
    private BigDecimal sellPrice;

    @Column(name = "profit_loss")
    private Long profitLoss;

    @Column(name = "profit_loss_rate", precision = 9, scale = 4)
    private BigDecimal profitLossRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_data_source", length = 20)
    private PriceDataSource priceDataSource;

    // 루트(Investment.addSector)를 통해서만 생성된다.
    InvestmentSector(Long sectorId, int quantity, Long amount, BigDecimal buyPrice) {
        this.sectorId = sectorId;
        this.quantity = quantity;
        this.amount = amount;
        this.buyPrice = buyPrice;
    }

    // 정산 결과 반영은 루트(Investment.applySectorResult)를 통해서만 호출된다.
    void applyResult(
            BigDecimal sellPrice, Long profitLoss, BigDecimal profitLossRate, PriceDataSource priceDataSource) {
        this.sellPrice = sellPrice;
        this.profitLoss = profitLoss;
        this.profitLossRate = profitLossRate;
        this.priceDataSource = priceDataSource;
    }

    /**
     * 당일 시가(sellPrice)로 손익을 직접 계산해 반영한다(정상 정산). 루트(Investment.settleSector)를 통해서만 호출된다.
     *
     * <p>매수가(전일 종가 스냅샷)가 없거나 0 이하이면 거래정지(폴백)와 다른 상류 데이터 결함이므로, 조용히 0% 처리하지 않고 예외를 던져 정산을 실패시킨다. (거래정지 0% 폴백은
     * 매도가 부재로 판단해 {@link Investment#settleSectorFallback}로 별도 처리한다.)
     *
     * <p>손익률 = (당일 시가 - 매수가) / 매수가 × 100, 손익금 = round(투자금 × (당일 시가 - 매수가) / 매수가)
     */
    void settle(BigDecimal sellPrice) {
        if (buyPrice == null || buyPrice.signum() <= 0) {
            throw new IllegalStateException("매수가 스냅샷이 없어 정산할 수 없습니다: sectorId=" + sectorId + ", buyPrice=" + buyPrice);
        }
        BigDecimal diff = sellPrice.subtract(buyPrice);
        BigDecimal rate = diff.multiply(BigDecimal.valueOf(100)).divide(buyPrice, 4, RoundingMode.HALF_UP);
        long profit = diff.multiply(BigDecimal.valueOf(amount))
                .divide(buyPrice, 0, RoundingMode.HALF_UP)
                .longValueExact();
        this.sellPrice = sellPrice;
        this.profitLoss = profit;
        this.profitLossRate = rate;
        this.priceDataSource = PriceDataSource.NORMAL;
    }

    /** ETF 시세를 사용할 수 없을 때 0%(손익 0)로 정산한다. 매도가는 매수가와 동일하게 두어 변동 없음을 표현한다. */
    void settleFallback() {
        this.sellPrice = this.buyPrice;
        this.profitLoss = 0L;
        this.profitLossRate = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        this.priceDataSource = PriceDataSource.FALLBACK_ZERO;
    }
}
