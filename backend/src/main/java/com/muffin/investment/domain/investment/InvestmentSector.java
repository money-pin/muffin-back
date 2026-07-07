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
}
