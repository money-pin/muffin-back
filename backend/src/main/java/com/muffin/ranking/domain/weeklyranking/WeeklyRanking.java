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
                        name = "uk_weekly_ranking_user_week",
                        columnNames = {"user_id", "week_of_year"}))
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

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "weekly_profit", nullable = false)
    private Long weeklyProfit;

    @Column(name = "weekly_profit_rate", precision = 5, scale = 2)
    private BigDecimal weeklyProfitRate;

    @Column(name = "percentile")
    private Integer percentile;

    @Column(name = "week_of_year", nullable = false)
    private int weekOfYear;

    private WeeklyRanking(
            Long userId,
            String nicknameSnapshot,
            int rank,
            Long weeklyProfit,
            BigDecimal weeklyProfitRate,
            Integer percentile,
            int weekOfYear) {
        this.userId = userId;
        this.nicknameSnapshot = nicknameSnapshot;
        this.rank = rank;
        this.weeklyProfit = weeklyProfit;
        this.weeklyProfitRate = weeklyProfitRate;
        this.percentile = percentile;
        this.weekOfYear = weekOfYear;
    }

    /** 주간 랭킹 산출 결과 레코드를 생성한다. */
    public static WeeklyRanking create(
            Long userId,
            String nicknameSnapshot,
            int rank,
            Long weeklyProfit,
            BigDecimal weeklyProfitRate,
            Integer percentile,
            int weekOfYear) {
        return new WeeklyRanking(
                userId, nicknameSnapshot, rank, weeklyProfit, weeklyProfitRate, percentile, weekOfYear);
    }
}
