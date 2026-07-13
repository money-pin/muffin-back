package com.muffin.investment.domain.userasset;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 자산(사용자와 1:1) 애그리거트 루트. 총자산과 최근 정산에 따른 일간 변동을 관리한다.
 */
@Entity
@Getter
@Table(name = "user_asset", uniqueConstraints = @UniqueConstraint(name = "uk_user_asset_user", columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAsset extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_asset_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_asset", nullable = false)
    private Long totalAsset;

    @Column(name = "daily_change_amount", nullable = false)
    private Long dailyChangeAmount;

    @Column(name = "daily_change_rate", nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyChangeRate;

    @Column(name = "last_settled_at")
    private LocalDateTime lastSettledAt;

    // 정산 배치와 퀴즈 보상 등 total_asset을 동시에 가산하는 주체가 생겨, lost update 방지를 위한 낙관적 락.
    @Version
    @Column(name = "version")
    private Long version;

    private UserAsset(Long userId, Long initialAsset) {
        this.userId = userId;
        this.totalAsset = initialAsset;
        this.dailyChangeAmount = 0L;
        this.dailyChangeRate = BigDecimal.ZERO;
    }

    public static UserAsset create(Long userId, Long initialAsset) {
        return new UserAsset(userId, initialAsset);
    }

    /** 정산 결과를 자산에 반영한다. 총자산은 음수가 될 수 없다(손실이 총자산을 초과하면 정산을 거부한다). */
    public void applySettlement(long changeAmount, BigDecimal changeRate, LocalDateTime settledAt) {
        long newTotalAsset = this.totalAsset + changeAmount;
        if (newTotalAsset < 0) {
            throw new IllegalArgumentException("총자산은 음수가 될 수 없습니다: 변동액=" + changeAmount);
        }
        this.totalAsset = newTotalAsset;
        this.dailyChangeAmount = changeAmount;
        this.dailyChangeRate = changeRate;
        this.lastSettledAt = settledAt;
    }

    /**
     * 손익 변화 없이 정산 처리만 반영한다(미투자일/취소일). 총자산은 유지하되 일간 변동을 0으로 갱신하고 최근 정산 시각을 올려, 화면에 이전 수익이 남거나 "정산 중"으로 계속
     * 표시되는 것을 막는다.
     */
    public void markNoChange(LocalDateTime settledAt) {
        this.dailyChangeAmount = 0L;
        this.dailyChangeRate = BigDecimal.ZERO;
        this.lastSettledAt = settledAt;
    }
}
