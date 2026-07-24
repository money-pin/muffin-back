package com.muffin.sector.domain.etfprice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertEquals(PriceCollectionStatus.SUCCESS, etfPrice.getStartPriceStatus());
        assertEquals(PriceCollectionStatus.SUCCESS, etfPrice.getEndPriceStatus());
    }

    @Test
    @DisplayName("시가만 주고 생성하면 종가는 비어있다")
    void open_hasStartPriceOnlyAndNoEndPrice() {
        EtfPrice etfPrice = EtfPrice.open(ETF_ID, PRICE_DATE, 10_000L);

        assertEquals(10_000L, etfPrice.getStartPrice());
        assertNull(etfPrice.getEndPrice());
        assertEquals(PriceCollectionStatus.SUCCESS, etfPrice.getStartPriceStatus());
        assertEquals(PriceCollectionStatus.PENDING, etfPrice.getEndPriceStatus());
    }

    @Test
    @DisplayName("recordOpen을 호출하면 시가가 반영된다")
    void recordOpen_updatesStartPrice() {
        EtfPrice etfPrice = EtfPrice.open(ETF_ID, PRICE_DATE, 10_000L);

        etfPrice.recordOpen(10_100L);

        assertEquals(10_100L, etfPrice.getStartPrice());
        assertEquals(PriceCollectionStatus.SUCCESS, etfPrice.getStartPriceStatus());
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
        assertEquals(PriceCollectionStatus.SUCCESS, etfPrice.getEndPriceStatus());
    }

    @Test
    @DisplayName("recordClose를 같은 값으로 여러 번 호출해도 결과는 같다")
    void recordClose_isIdempotent() {
        EtfPrice etfPrice = EtfPrice.create(ETF_ID, PRICE_DATE, 10_000L, 10_500L);

        etfPrice.recordClose(10_500L);
        etfPrice.recordClose(10_500L);

        assertEquals(10_500L, etfPrice.getEndPrice());
    }

    @Test
    @DisplayName("미수신 상태는 가격을 만들지 않고 NO_DATA로 기록한다")
    void markOpenNoData_recordsStatusWithoutPrice() {
        EtfPrice etfPrice = EtfPrice.pending(ETF_ID, PRICE_DATE);

        etfPrice.markOpenNoData();

        assertNull(etfPrice.getStartPrice());
        assertEquals(PriceCollectionStatus.NO_DATA, etfPrice.getStartPriceStatus());
    }

    @Test
    @DisplayName("09시 30분까지 시가가 없으면 FINAL_MISSING으로 확정할 수 있다")
    void markOpenFinalMissing_recordsTerminalStatus() {
        EtfPrice etfPrice = EtfPrice.pending(ETF_ID, PRICE_DATE);

        etfPrice.markOpenFinalMissing();

        assertNull(etfPrice.getStartPrice());
        assertEquals(PriceCollectionStatus.FINAL_MISSING, etfPrice.getStartPriceStatus());
    }

    @Test
    @DisplayName("16시 05분까지 종가가 없으면 FINAL_MISSING으로 확정할 수 있다")
    void markCloseFinalMissing_recordsTerminalStatus() {
        EtfPrice etfPrice = EtfPrice.pending(ETF_ID, PRICE_DATE);

        etfPrice.markCloseFinalMissing();

        assertNull(etfPrice.getEndPrice());
        assertEquals(PriceCollectionStatus.FINAL_MISSING, etfPrice.getEndPriceStatus());
    }

    @Test
    @DisplayName("0 이하 가격은 정상 가격으로 생성할 수 없다")
    void create_rejectsNonPositivePrice() {
        assertThrows(IllegalArgumentException.class, () -> EtfPrice.open(ETF_ID, PRICE_DATE, 0L));
    }
}
