package com.muffin.investment.domain.investment;

import com.muffin.global.entity.BaseEntity;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.PriceDataSource;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 투자(일 단위) 애그리거트 루트. 하루치 투자 확정 내역과 섹터별 상세(InvestmentSector)를 한 애그리거트로 관리한다. */
@Entity
@Getter
@Table(
        name = "investment",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_investment_user_invest_date",
                        columnNames = {"user_id", "invest_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Investment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "investment_id")
    private Long id;

    // 다른 애그리거트(UserAsset)는 ID로만 참조한다.
    @Column(name = "user_asset_id", nullable = false)
    private Long userAssetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "invest_date", nullable = false)
    private LocalDate investDate;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Column(name = "total_profit_loss", nullable = false)
    private Long totalProfitLoss;

    @Column(name = "total_profit_loss_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal totalProfitLossRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InvestmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_status", nullable = false, length = 20)
    private SettlementStatus settlementStatus;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    // InvestmentSector는 이 애그리거트 내부 엔티티이므로 루트가 관리한다.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "investment_id", nullable = false)
    private final List<InvestmentSector> sectors = new ArrayList<>();

    private Investment(Long userId, Long userAssetId, LocalDate investDate, InvestmentStatus status) {
        this.userId = userId;
        this.userAssetId = userAssetId;
        this.investDate = investDate;
        this.status = status;
        this.settlementStatus = SettlementStatus.PENDING;
        this.totalAmount = 0L;
        this.totalProfitLoss = 0L;
        this.totalProfitLossRate = BigDecimal.ZERO;
    }

    /** 투자 확정 레코드 생성. 섹터는 addSector로 추가한다. */
    public static Investment confirm(Long userId, Long userAssetId, LocalDate investDate) {
        return new Investment(userId, userAssetId, investDate, InvestmentStatus.CONFIRMED);
    }

    /** 해당 일자에 투자하지 않은 경우의 레코드 생성. */
    public static Investment noInvest(Long userId, Long userAssetId, LocalDate investDate) {
        return new Investment(userId, userAssetId, investDate, InvestmentStatus.NO_INVEST);
    }

    /** 섹터 추가: 항상 이 메서드를 통해서만 추가해 "총 투자금 = 섹터 금액 합" 불변식을 유지한다. */
    public void addSector(Long sectorId, int quantity, long amount, BigDecimal buyPrice) {
        sectors.add(new InvestmentSector(sectorId, quantity, amount, buyPrice));
        this.totalAmount =
                sectors.stream().mapToLong(InvestmentSector::getAmount).sum();
    }

    /** 섹터별 정산 결과를 루트를 통해 반영한다. */
    public void applySectorResult(
            Long sectorId,
            BigDecimal sellPrice,
            Long profitLoss,
            BigDecimal profitLossRate,
            PriceDataSource priceDataSource) {
        InvestmentSector sector = sectors.stream()
                .filter(s -> s.getSectorId().equals(sectorId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("해당 섹터 투자 내역이 없습니다: sectorId=" + sectorId));
        sector.applyResult(sellPrice, profitLoss, profitLossRate, priceDataSource);
    }

    /** 정산 반영: 각 섹터 결과가 채워진 뒤 호출해 총 손익/손익률을 재계산하고 상태를 SETTLED로 만든다. */
    public void settle(LocalDateTime settledAt) {
        long profit = sectors.stream()
                .mapToLong(s -> s.getProfitLoss() == null ? 0L : s.getProfitLoss())
                .sum();
        this.totalProfitLoss = profit;
        this.totalProfitLossRate = (totalAmount == 0L)
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(profit)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(totalAmount), 4, RoundingMode.HALF_UP);
        this.settlementStatus = SettlementStatus.SETTLED;
        this.settledAt = settledAt;
    }

    /** 정산 실패 처리. */
    public void failSettlement() {
        this.settlementStatus = SettlementStatus.FAILED;
    }

    /** 읽기 전용 뷰를 반환한다. */
    public List<InvestmentSector> getSectors() {
        return Collections.unmodifiableList(sectors);
    }
}
