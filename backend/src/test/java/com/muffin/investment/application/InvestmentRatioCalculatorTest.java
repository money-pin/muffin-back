package com.muffin.investment.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InvestmentRatioCalculatorTest {

    @Test
    void calculate_returnsZeroWhenTotalAmountIsZero() {
        assertEquals(new BigDecimal("0.00"), InvestmentRatioCalculator.calculate(0L, 0L));
    }

    @Test
    void calculate_returnsPercentageRoundedHalfUp() {
        assertEquals(new BigDecimal("33.33"), InvestmentRatioCalculator.calculate(100_000L, 300_000L));
    }
}
