package com.muffin.investment.domain.profitsummary;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자-요약일자(1:1) 단위의 손익 스냅샷 애그리거트. 일일/누적 손익을 담아 통계 그래프의 시계열 소스로 쓰인다.
 */
@Entity
@Getter
@Table(
        name = "profit_summary",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_profit_summary_user_summary_date",
                        columnNames = {"user_id", "summary_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfitSummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profit_summary_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "summary_date", nullable = false)
    private LocalDate summaryDate;

    @Column(name = "daily_profit_loss", nullable = false)
    private Long dailyProfitLoss;

    @Column(name = "daily_profit_loss_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal dailyProfitLossRate;

    @Column(name = "cumulative_profit_loss", nullable = false)
    private Long cumulativeProfitLoss;

    private ProfitSummary(
            Long userId,
            LocalDate summaryDate,
            Long dailyProfitLoss,
            BigDecimal dailyProfitLossRate,
            Long cumulativeProfitLoss) {
        this.userId = userId;
        this.summaryDate = summaryDate;
        this.dailyProfitLoss = dailyProfitLoss;
        this.dailyProfitLossRate = dailyProfitLossRate;
        this.cumulativeProfitLoss = cumulativeProfitLoss;
    }

    public static ProfitSummary create(
            Long userId,
            LocalDate summaryDate,
            Long dailyProfitLoss,
            BigDecimal dailyProfitLossRate,
            Long cumulativeProfitLoss) {
        return new ProfitSummary(userId, summaryDate, dailyProfitLoss, dailyProfitLossRate, cumulativeProfitLoss);
    }

    /** 정산 재실행 시 기존 요약을 갱신한다(요약 일자는 불변). */
    public void update(Long dailyProfitLoss, BigDecimal dailyProfitLossRate, Long cumulativeProfitLoss) {
        this.dailyProfitLoss = dailyProfitLoss;
        this.dailyProfitLossRate = dailyProfitLossRate;
        this.cumulativeProfitLoss = cumulativeProfitLoss;
    }
}
