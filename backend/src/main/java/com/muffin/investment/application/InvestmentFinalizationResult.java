package com.muffin.investment.application;

import java.time.LocalDate;

public record InvestmentFinalizationResult(
        LocalDate investDate, boolean tradingDay, int targetCount, int successCount, int failureCount) {

    public static InvestmentFinalizationResult marketClosed(LocalDate investDate) {
        return new InvestmentFinalizationResult(investDate, false, 0, 0, 0);
    }
}
