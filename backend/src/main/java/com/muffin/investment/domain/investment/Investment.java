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

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    //    자정 마감 배치 구현할 때 필요할 예정
    //    @Column(name = "settlement_due_date")
    //    private LocalDate settlementDueDate;

    // InvestmentSector는 이 애그리거트 내부 엔티티이므로 루트가 관리한다.
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "investment_id", nullable = false)
    private final List<InvestmentSector> sectors = new ArrayList<>();

    private Investment(Long userId, LocalDate investDate, InvestmentStatus status) {
        this.userId = userId;
        this.investDate = investDate;
        this.status = status;
        this.settlementStatus = SettlementStatus.PENDING;
        this.totalAmount = 0L;
        this.totalProfitLoss = 0L;
        this.totalProfitLossRate = BigDecimal.ZERO;
    }

    /** 투자 확정 레코드 생성. 섹터는 addSector로 추가한다. */
    public static Investment confirm(Long userId, LocalDate investDate) {
        return new Investment(userId, investDate, InvestmentStatus.CONFIRMED);
    }

    /** 해당 일자에 투자하지 않은 경우의 레코드 생성. */
    public static Investment noInvest(Long userId, LocalDate investDate) {
        return new Investment(userId, investDate, InvestmentStatus.NO_INVEST);
    }

    /** 섹터 추가: 항상 이 메서드를 통해서만 추가해 "총 투자금 = 섹터 금액 합" 불변식을 유지한다. */
    public void addSector(Long sectorId, int quantity, long amount, BigDecimal buyPrice) {
        sectors.add(new InvestmentSector(sectorId, quantity, amount, buyPrice));
        recalculateTotalAmount();
    }

    /**
     * 투자 수정 시 전달된 최종 구성으로 섹터를 맞춘다. 매수가는 자정 마감 전까지 비워 둔다.
     *
     * <p>전부 지우고 새로 담지 않고 병합한다. 유지되는 섹터를 지웠다 다시 넣으면 하이버네이트가 orphan removal(DELETE)보다 INSERT를 먼저 flush해서 같은
     * {@code (investment_id, sector_id)}가 순간적으로 겹치고, {@code uk_investment_sector_investment_sector}에 걸린다. 수량만 바꾸는 수정이
     * 가장 흔한 경로라 실제로 터진다. allocations는 섹터별로 이미 합산되어 들어온다.
     */
    public void replaceSectors(List<SectorAllocation> allocations) {
        List<Long> keepSectorIds =
                allocations.stream().map(SectorAllocation::sectorId).toList();
        sectors.removeIf(sector -> !keepSectorIds.contains(sector.getSectorId()));
        for (SectorAllocation allocation : allocations) {
            sectors.stream()
                    .filter(sector -> sector.getSectorId().equals(allocation.sectorId()))
                    .findFirst()
                    .ifPresentOrElse(
                            sector -> sector.changeAllocation(allocation.quantity(), allocation.amount()),
                            () -> sectors.add(new InvestmentSector(
                                    allocation.sectorId(), allocation.quantity(), allocation.amount(), null)));
        }
        recalculateTotalAmount();
    }

    /** 섹터별 정산 결과를 루트를 통해 반영한다(계산된 값 주입형). */
    public void applySectorResult(
            Long sectorId,
            BigDecimal sellPrice,
            Long profitLoss,
            BigDecimal profitLossRate,
            PriceDataSource priceDataSource) {
        findSector(sectorId).applyResult(sellPrice, profitLoss, profitLossRate, priceDataSource);
    }

    /** 정산 phase 1(정상): 매수가·매도가가 모두 확보된 섹터를 NORMAL로 스냅샷한다. 손익 계산은 phase 2({@link #computeSectorResults})에서 한다. */
    public void stampSectorNormal(Long sectorId, BigDecimal buyPrice, BigDecimal sellPrice) {
        findSector(sectorId).stampNormal(buyPrice, sellPrice);
    }

    /** 정산 phase 1(폴백): 시가/종가 미확보 섹터를 0%(원금 그대로, FALLBACK_ZERO)로 스냅샷한다. 매수가는 있으면 표시용으로 남긴다(없으면 null 허용). */
    public void stampSectorFallback(Long sectorId, BigDecimal buyPrice) {
        findSector(sectorId).stampFallback(buyPrice);
    }

    private InvestmentSector findSector(Long sectorId) {
        return sectors.stream()
                .filter(s -> s.getSectorId().equals(sectorId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("해당 섹터 투자 내역이 없습니다: sectorId=" + sectorId));
    }

    /** 정산 phase 2: 각 섹터를 스냅샷(매수가·매도가·출처)으로 손익 계산한다. {@link #settle} 직전에 호출한다. */
    public void computeSectorResults() {
        sectors.forEach(InvestmentSector::computeResult);
    }

    /** 정산 phase 2: 각 섹터 손익이 계산된 뒤 호출해 총 손익/손익률을 집계하고 상태를 SETTLED로 만든다. 미계산 섹터가 있으면 완료할 수 없다. */
    public void settle(LocalDateTime settledAt) {
        long profit = 0L;
        for (InvestmentSector sector : sectors) {
            if (sector.getProfitLoss() == null) {
                throw new IllegalStateException("손익이 계산되지 않은 섹터가 있어 정산을 완료할 수 없습니다: sectorId=" + sector.getSectorId());
            }
            profit += sector.getProfitLoss();
        }
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

    /**
     * 자정 마감 시 최종 투자 구성을 동결한다(멱등). 가격 스냅샷(매수가/매도가)은 정산 배치 phase 1의 책임이므로 여기서는 다루지 않는다. 동결 이후 PATCH를 막는 컷오프 역할만
     * 한다.
     */
    public void finalizeInvestment(LocalDateTime finalizedAt) {
        if (this.finalizedAt == null) {
            this.finalizedAt = finalizedAt;
        }
    }

    /** 미투자 레코드를 자정 마감 완료 상태로 동결한다. */
    public void finalizeNoInvest(LocalDateTime finalizedAt) {
        if (status != InvestmentStatus.NO_INVEST) {
            throw new IllegalStateException("미투자 레코드만 이 방식으로 마감할 수 있습니다.");
        }
        if (this.finalizedAt == null) {
            this.finalizedAt = finalizedAt;
        }
    }

    /** 정산 창(다음 거래일)을 놓친 확정 투자를 취소한다. 자산에 영향을 주지 않으며 손익 0으로 종료한다. */
    public void cancelSettlement(LocalDateTime cancelledAt) {
        this.totalProfitLoss = 0L;
        this.totalProfitLossRate = BigDecimal.ZERO;
        this.settlementStatus = SettlementStatus.CANCELLED;
        this.settledAt = cancelledAt;
    }

    /** 투자하지 않은 날(NO_INVEST)의 정산을 종료 처리한다. 손익 0으로 남겨 일별 시계열에 0행을 만들 수 있게 한다. */
    public void markNoSettlement(LocalDateTime processedAt) {
        this.totalProfitLoss = 0L;
        this.totalProfitLossRate = BigDecimal.ZERO;
        this.settlementStatus = SettlementStatus.NO_SETTLEMENT;
        this.settledAt = processedAt;
    }

    /** 읽기 전용 뷰를 반환한다. */
    public List<InvestmentSector> getSectors() {
        return Collections.unmodifiableList(sectors);
    }

    private void recalculateTotalAmount() {
        this.totalAmount =
                sectors.stream().mapToLong(InvestmentSector::getAmount).sum();
    }

    public record SectorAllocation(Long sectorId, int quantity, long amount) {}
}
