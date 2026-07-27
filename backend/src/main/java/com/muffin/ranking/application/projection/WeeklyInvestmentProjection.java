package com.muffin.ranking.application.projection;

/** TOP 10 사용자의 지난주 총 투자금 집계값이다. */
public record WeeklyInvestmentProjection(Long userId, Long totalInvestment) {}
