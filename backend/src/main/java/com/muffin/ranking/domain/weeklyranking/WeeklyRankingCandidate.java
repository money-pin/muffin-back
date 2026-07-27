package com.muffin.ranking.domain.weeklyranking;

/** 지난주 정산 완료 투자에서 집계한 사용자별 랭킹 후보 값이다. */
public record WeeklyRankingCandidate(
        Long userId, String nickname, String userUuid, Long totalInvestment, Long weeklyProfit) {}
