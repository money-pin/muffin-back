package com.muffin.ranking.application.projection;

/** TOP 10 사용자의 지난주 섹터별 투자 및 수익 집계값이다. */
public record WeeklySectorProjection(
        Long userId, String sectorCode, String sectorName, Long totalInvestment, Long profitAmount) {}
