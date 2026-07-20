package com.muffin.investment.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class InvestmentRatioCalculator {

    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private InvestmentRatioCalculator() {}

    static BigDecimal calculate(long amount, long totalAmount) {
        if (totalAmount == 0L) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(amount)
                .multiply(PERCENT)
                .divide(BigDecimal.valueOf(totalAmount), 2, RoundingMode.HALF_UP);
    }
}
