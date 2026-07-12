package com.muffin.sector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(EtfPriceWriter.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EtfPriceWriterTest {

    private static final Long ETF_ID = 1L;
    private static final LocalDate PRICE_DATE = LocalDate.of(2026, 7, 10);

    @Autowired
    private EtfPriceWriter etfPriceWriter;

    @Autowired
    private EtfPriceRepository etfPriceRepository;

    @Test
    @DisplayName("기존 레코드가 없으면 시가만 있는 레코드를 새로 만든다")
    void writeOpen_createsRecord_whenNotExists() {
        etfPriceWriter.writeOpen(ETF_ID, PRICE_DATE, 10_000L);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertEquals(10_000L, saved.getStartPrice());
        assertNull(saved.getEndPrice());
    }

    @Test
    @DisplayName("기존 레코드가 있으면 시가만 갱신하고 새 레코드를 만들지 않는다")
    void writeOpen_updatesExistingRecord() {
        etfPriceWriter.writeClose(ETF_ID, PRICE_DATE, 10_500L);

        etfPriceWriter.writeOpen(ETF_ID, PRICE_DATE, 10_000L);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertEquals(10_000L, saved.getStartPrice());
        assertEquals(10_500L, saved.getEndPrice());
        assertEquals(1, etfPriceRepository.count());
    }

    @Test
    @DisplayName("기존 레코드가 없으면 종가만 있는 레코드를 새로 만든다")
    void writeClose_createsRecord_whenNotExists() {
        etfPriceWriter.writeClose(ETF_ID, PRICE_DATE, 10_500L);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertNull(saved.getStartPrice());
        assertEquals(10_500L, saved.getEndPrice());
    }

    @Test
    @DisplayName("기존 레코드가 있으면 종가만 갱신하고 새 레코드를 만들지 않는다")
    void writeClose_updatesExistingRecord() {
        etfPriceWriter.writeOpen(ETF_ID, PRICE_DATE, 10_000L);

        etfPriceWriter.writeClose(ETF_ID, PRICE_DATE, 10_500L);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertEquals(10_000L, saved.getStartPrice());
        assertEquals(10_500L, saved.getEndPrice());
        assertEquals(1, etfPriceRepository.count());
    }
}
