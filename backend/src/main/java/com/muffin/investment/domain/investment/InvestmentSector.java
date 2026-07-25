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
     * 정산 phase 1(정상): 매수가(전일 종가)·매도가(당일 시가)가 모두 확보된 섹터를 NORMAL로 스냅샷한다. 손익 계산은 phase 2({@link
     * #computeResult})의 책임이다. 정산 집계는 EtfPrice가 아니라 이 스냅샷만 신뢰한다.
     */
    void stampNormal(BigDecimal buyPrice, BigDecimal sellPrice) {
        if (buyPrice == null || buyPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "정상 정산 매수가는 0보다 커야 합니다: sectorId=" + sectorId + ", buyPrice=" + buyPrice);
        }
        if (sellPrice == null || sellPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "정상 정산 매도가는 0보다 커야 합니다: sectorId=" + sectorId + ", sellPrice=" + sellPrice);
        }
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.priceDataSource = PriceDataSource.NORMAL;
    }

    /**
     * 정산 phase 1(폴백): 시가/종가 중 하나라도 확보하지 못한 섹터를 0%(원금 그대로, FALLBACK_ZERO)로 스냅샷한다. 손익은 어차피 0이라 매수가가 없어도(전일 종가
     * 결손) 처리할 수 있다. 매수가가 있으면 표시용으로 남기고 매도가는 그와 동일하게 둔다.
     */
    void stampFallback(BigDecimal buyPrice) {
        this.buyPrice = buyPrice;
        this.sellPrice = buyPrice;
        this.priceDataSource = PriceDataSource.FALLBACK_ZERO;
    }

    /**
     * 정산 phase 2: 스냅샷(매수가·매도가·출처)만으로 섹터 손익을 계산한다. EtfPrice를 보지 않는다.
     *
     * <p>{@code FALLBACK_ZERO}는 가격을 신뢰할 수 없어 0%로 확정된 경우라 매수가 유무와 무관하게 손익 0으로 둔다. {@code NORMAL}은 손익률 = (매도가 -
     * 매수가) / 매수가 × 100, 손익금 = round(투자금 × (매도가 - 매수가) / 매수가).
     */
    void computeResult() {
        if (priceDataSource == null) {
            throw new IllegalStateException("정산 스냅샷 출처가 없어 손익을 계산할 수 없습니다: sectorId=" + sectorId);
        }
        if (priceDataSource == PriceDataSource.FALLBACK_ZERO) {
            this.profitLoss = 0L;
            this.profitLossRate = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            return;
        }
        if (buyPrice == null || buyPrice.signum() <= 0 || sellPrice == null) {
            throw new IllegalStateException("정상 정산 스냅샷(매수가/매도가)이 채워지지 않았습니다: sectorId=" + sectorId);
        }
        BigDecimal diff = sellPrice.subtract(buyPrice);
        this.profitLossRate = diff.multiply(BigDecimal.valueOf(100)).divide(buyPrice, 4, RoundingMode.HALF_UP);
        this.profitLoss = diff.multiply(BigDecimal.valueOf(amount))
                .divide(buyPrice, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
