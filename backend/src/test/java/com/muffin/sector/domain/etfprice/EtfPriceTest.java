package com.muffin.sector.domain.etfprice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EtfPriceTest {

    private static final Long ETF_ID = 1L;
    private static final LocalDate PRICE_DATE = LocalDate.of(2026, 7, 8);

    @Test
    @DisplayName("시가와 종가를 모두 주면 둘 다 저장된다")
    void create_storesStartAndEndPrice() {
        EtfPrice etfPrice = EtfPrice.create(ETF_ID, PRICE_DATE, 10_000L, 10_500L);

        assertEquals(10_000L, etfPrice.getStartPrice());
        assertEquals(10_500L, etfPrice.getEndPrice());
    }

    @Test
    @DisplayName("시가만 주고 생성하면 종가는 비어있다")
    void open_hasStartPriceOnlyAndNoEndPrice() {
        EtfPrice etfPrice = EtfPrice.open(ETF_ID, PRICE_DATE, 10_000L);

        assertEquals(10_000L, etfPrice.getStartPrice());
        assertNull(etfPrice.getEndPrice());
    }

    @Test
    @DisplayName("recordOpen을 호출하면 시가가 반영된다")
    void recordOpen_updatesStartPrice() {
        EtfPrice etfPrice = EtfPrice.open(ETF_ID, PRICE_DATE, 10_000L);

        etfPrice.recordOpen(10_100L);

        assertEquals(10_100L, etfPrice.getStartPrice());
    }

    @Test
    @DisplayName("recordOpen을 같은 값으로 여러 번 호출해도 결과는 같다")
    void recordOpen_isIdempotent() {
        EtfPrice etfPrice = EtfPrice.open(ETF_ID, PRICE_DATE, 10_000L);

        etfPrice.recordOpen(10_100L);
        etfPrice.recordOpen(10_100L);

        assertEquals(10_100L, etfPrice.getStartPrice());
    }

    @Test
    @DisplayName("recordClose를 호출하면 종가가 반영된다")
    void recordClose_updatesEndPrice() {
        EtfPrice etfPrice = EtfPrice.open(ETF_ID, PRICE_DATE, 10_000L);

        etfPrice.recordClose(10_500L);

        assertEquals(10_500L, etfPrice.getEndPrice());
    }

    @Test
    @DisplayName("recordClose를 같은 값으로 여러 번 호출해도 결과는 같다")
    void recordClose_isIdempotent() {
        EtfPrice etfPrice = EtfPrice.create(ETF_ID, PRICE_DATE, 10_000L, 10_500L);

        etfPrice.recordClose(10_500L);
        etfPrice.recordClose(10_500L);

        assertEquals(10_500L, etfPrice.getEndPrice());
    }
}
