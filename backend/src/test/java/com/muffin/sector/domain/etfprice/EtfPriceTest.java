package com.muffin.sector.domain.etfprice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EtfPriceTest {

    private static final Long ETF_ID = 1L;
    private static final LocalDate PRICE_DATE = LocalDate.of(2026, 7, 8);

    @Test
    @DisplayName("정상 시세를 생성하면 폴백 여부가 false다")
    void create_isNotFallback() {
        EtfPrice etfPrice = EtfPrice.create(ETF_ID, PRICE_DATE, 10_000L, 10_500L);

        assertEquals(10_000L, etfPrice.getStartPrice());
        assertEquals(10_500L, etfPrice.getEndPrice());
        assertFalse(etfPrice.isFallback());
    }

    @Test
    @DisplayName("폴백 시세를 생성하면 시작가와 종가가 0이고 폴백 여부가 true다")
    void fallback_hasZeroPricesAndFallbackTrue() {
        EtfPrice etfPrice = EtfPrice.fallback(ETF_ID, PRICE_DATE);

        assertEquals(0L, etfPrice.getStartPrice());
        assertEquals(0L, etfPrice.getEndPrice());
        assertTrue(etfPrice.isFallback());
    }
}
