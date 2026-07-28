package com.muffin.ranking.application.projection;

import java.math.BigDecimal;

/** weekly_ranking 스냅샷에서 읽은 사용자별 주간 랭킹 정보다. */
public record WeeklyRankingProjection(
        Long userId,
        String nicknameSnapshot,
        int rankingPosition,
        Long weeklyProfit,
        BigDecimal weeklyProfitRate,
        Integer percentile) {}
