package com.muffin.ranking.domain.weeklyranking;

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

/** 주간 수익률 랭킹 애그리거트 루트. 배치가 주 단위로 산출하는 스냅샷이다. */
@Getter
@Entity
@Table(
        name = "weekly_ranking",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_weekly_ranking_user_week_start_date",
                        columnNames = {"user_id", "week_start_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyRanking extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "weekly_ranking_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "nickname_snapshot", nullable = false, length = 20)
    private String nicknameSnapshot;

    @Column(name = "ranking_position", nullable = false)
    private int rankingPosition;

    @Column(name = "weekly_profit", nullable = false)
    private Long weeklyProfit;

    @Column(name = "weekly_profit_rate", precision = 5, scale = 2)
    private BigDecimal weeklyProfitRate;

    @Column(name = "percentile")
    private Integer percentile;

    // 주차가 시작되는 월요일. 같은 week_of_year라도 연도가 다르면 값이 달라져 유니크 제약의 실제 기준이 된다.
    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_of_year", nullable = false)
    private int weekOfYear;

    private WeeklyRanking(
            Long userId,
            String nicknameSnapshot,
            int rankingPosition,
            Long weeklyProfit,
            BigDecimal weeklyProfitRate,
            Integer percentile,
            LocalDate weekStartDate,
            int weekOfYear) {
        if (nicknameSnapshot == null || nicknameSnapshot.isBlank()) {
            throw new IllegalArgumentException("nicknameSnapshot은 필수입니다.");
        }
        if (rankingPosition <= 0) {
            throw new IllegalArgumentException("rankingPosition은 1 이상이어야 합니다: " + rankingPosition);
        }
        if (percentile != null && (percentile < 0 || percentile > 100)) {
            throw new IllegalArgumentException("percentile은 0~100 사이여야 합니다: " + percentile);
        }
        if (weekOfYear < 1 || weekOfYear > 53) {
            throw new IllegalArgumentException("weekOfYear는 1~53 사이여야 합니다: " + weekOfYear);
        }
        this.userId = userId;
        this.nicknameSnapshot = nicknameSnapshot;
        this.rankingPosition = rankingPosition;
        this.weeklyProfit = weeklyProfit;
        this.weeklyProfitRate = weeklyProfitRate;
        this.percentile = percentile;
        this.weekStartDate = weekStartDate;
        this.weekOfYear = weekOfYear;
    }

    /** 주간 랭킹 산출 결과 레코드를 생성한다. weeklyProfit은 손실일 수 있어 음수를 허용한다. */
    public static WeeklyRanking create(
            Long userId,
            String nicknameSnapshot,
            int rankingPosition,
            Long weeklyProfit,
            BigDecimal weeklyProfitRate,
            Integer percentile,
            LocalDate weekStartDate,
            int weekOfYear) {
        return new WeeklyRanking(
                userId,
                nicknameSnapshot,
                rankingPosition,
                weeklyProfit,
                weeklyProfitRate,
                percentile,
                weekStartDate,
                weekOfYear);
    }
}
