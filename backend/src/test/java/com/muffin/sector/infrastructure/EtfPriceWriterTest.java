package com.muffin.sector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
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
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getStartPriceStatus());
        assertNull(saved.getEndPrice());
        assertEquals(PriceCollectionStatus.PENDING, saved.getEndPriceStatus());
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
        assertEquals(PriceCollectionStatus.PENDING, saved.getStartPriceStatus());
        assertEquals(10_500L, saved.getEndPrice());
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getEndPriceStatus());
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

    @Test
    @DisplayName("시가 캔들이 없으면 가격 없이 NO_DATA 상태 행을 남긴다")
    void markOpenNoData_createsStatusRecord() {
        etfPriceWriter.markOpenNoData(ETF_ID, PRICE_DATE);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertNull(saved.getStartPrice());
        assertEquals(PriceCollectionStatus.NO_DATA, saved.getStartPriceStatus());
    }

    @Test
    @DisplayName("시가 수신에 실패하면 가격 없이 FAILED 상태 행을 남긴다")
    void markOpenFailed_createsStatusRecord() {
        etfPriceWriter.markOpenFailed(ETF_ID, PRICE_DATE);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertNull(saved.getStartPrice());
        assertEquals(PriceCollectionStatus.FAILED, saved.getStartPriceStatus());
    }

    @Test
    @DisplayName("종가 최종 미확보는 가격 없이 FINAL_MISSING 상태 행으로 기록한다")
    void markCloseFinalMissing_createsStatusRecord() {
        etfPriceWriter.markCloseFinalMissing(ETF_ID, PRICE_DATE);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertNull(saved.getEndPrice());
        assertEquals(PriceCollectionStatus.FINAL_MISSING, saved.getEndPriceStatus());
    }

    @Test
    @DisplayName("정상 종가는 FINAL_MISSING 처리로 덮어쓰지 않는다")
    void markCloseFinalMissing_preservesSuccessfulClose() {
        etfPriceWriter.writeClose(ETF_ID, PRICE_DATE, 10_500L);

        etfPriceWriter.markCloseFinalMissing(ETF_ID, PRICE_DATE);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertEquals(10_500L, saved.getEndPrice());
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getEndPriceStatus());
    }

    @Test
    @DisplayName("최종 미확보 종가는 이후 수집 결과로 덮어쓰지 않는다")
    void writeClose_preservesFinalMissingStatus() {
        etfPriceWriter.markCloseFinalMissing(ETF_ID, PRICE_DATE);

        etfPriceWriter.writeClose(ETF_ID, PRICE_DATE, 10_500L);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertNull(saved.getEndPrice());
        assertEquals(PriceCollectionStatus.FINAL_MISSING, saved.getEndPriceStatus());
    }

    @Test
    @DisplayName("최종 미확보 종가는 이후 실패 응답으로도 덮어쓰지 않는다")
    void markCloseFailed_preservesFinalMissingStatus() {
        etfPriceWriter.markCloseFinalMissing(ETF_ID, PRICE_DATE);

        etfPriceWriter.markCloseFailed(ETF_ID, PRICE_DATE);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertNull(saved.getEndPrice());
        assertEquals(PriceCollectionStatus.FINAL_MISSING, saved.getEndPriceStatus());
    }

    @Test
    @DisplayName("재시도에서 정상 시가를 받으면 실패 상태를 SUCCESS로 갱신한다")
    void writeOpen_changesFailedStatusToSuccess() {
        etfPriceWriter.markOpenFailed(ETF_ID, PRICE_DATE);

        etfPriceWriter.writeOpen(ETF_ID, PRICE_DATE, 10_000L);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertEquals(10_000L, saved.getStartPrice());
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getStartPriceStatus());
    }

    @Test
    @DisplayName("최종 미확보 시가는 뒤늦은 수집 결과로 덮어쓰지 않는다")
    void writeOpen_preservesFinalMissingStatus() {
        etfPriceWriter.markOpenFinalMissing(ETF_ID, PRICE_DATE);

        etfPriceWriter.writeOpen(ETF_ID, PRICE_DATE, 10_000L);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertNull(saved.getStartPrice());
        assertEquals(PriceCollectionStatus.FINAL_MISSING, saved.getStartPriceStatus());
    }

    @Test
    @DisplayName("최종 미확보 시가는 이후 실패 응답으로도 덮어쓰지 않는다")
    void markOpenFailed_preservesFinalMissingStatus() {
        etfPriceWriter.markOpenFinalMissing(ETF_ID, PRICE_DATE);

        etfPriceWriter.markOpenFailed(ETF_ID, PRICE_DATE);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertNull(saved.getStartPrice());
        assertEquals(PriceCollectionStatus.FINAL_MISSING, saved.getStartPriceStatus());
    }

    @Test
    @DisplayName("최종 미확보 시가는 이후 미수신 응답으로도 덮어쓰지 않는다")
    void markOpenNoData_preservesFinalMissingStatus() {
        etfPriceWriter.markOpenFinalMissing(ETF_ID, PRICE_DATE);

        etfPriceWriter.markOpenNoData(ETF_ID, PRICE_DATE);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertNull(saved.getStartPrice());
        assertEquals(PriceCollectionStatus.FINAL_MISSING, saved.getStartPriceStatus());
    }

    @Test
    @DisplayName("코인 기준가를 기록하면 시가와 종가에 동일한 값이 SUCCESS로 저장된다")
    void writeBasePrice_createsRecordWithSameOpenAndClose() {
        etfPriceWriter.writeBasePrice(ETF_ID, PRICE_DATE, 50_000_000L);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertEquals(50_000_000L, saved.getStartPrice());
        assertEquals(50_000_000L, saved.getEndPrice());
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getStartPriceStatus());
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getEndPriceStatus());
    }

    @Test
    @DisplayName("이미 SUCCESS인 기준가는 재수집 값으로 덮어쓰지 않는다")
    void writeBasePrice_doesNotOverwriteExistingSuccess() {
        etfPriceWriter.writeBasePrice(ETF_ID, PRICE_DATE, 50_000_000L);

        etfPriceWriter.writeBasePrice(ETF_ID, PRICE_DATE, 51_000_000L);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertEquals(50_000_000L, saved.getStartPrice());
        assertEquals(50_000_000L, saved.getEndPrice());
    }

    @Test
    @DisplayName("FAILED 상태였던 기준가는 재수집 값으로 갱신된다")
    void writeBasePrice_updatesFailedStatus() {
        etfPriceWriter.markBaseFailed(ETF_ID, PRICE_DATE);

        etfPriceWriter.writeBasePrice(ETF_ID, PRICE_DATE, 50_000_000L);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertEquals(50_000_000L, saved.getStartPrice());
        assertEquals(50_000_000L, saved.getEndPrice());
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getStartPriceStatus());
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getEndPriceStatus());
    }

    @Test
    @DisplayName("코인 기준가 조회에 실패하면 시가·종가 모두 FAILED 상태로 남는다")
    void markBaseFailed_marksBothOpenAndClose() {
        etfPriceWriter.markBaseFailed(ETF_ID, PRICE_DATE);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertNull(saved.getStartPrice());
        assertNull(saved.getEndPrice());
        assertEquals(PriceCollectionStatus.FAILED, saved.getStartPriceStatus());
        assertEquals(PriceCollectionStatus.FAILED, saved.getEndPriceStatus());
    }

    @Test
    @DisplayName("거래일이 아니면 시가·종가 모두 MARKET_CLOSED 상태로 남는다")
    void markBaseMarketClosed_marksBothOpenAndClose() {
        etfPriceWriter.markBaseMarketClosed(ETF_ID, PRICE_DATE);

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertEquals(PriceCollectionStatus.MARKET_CLOSED, saved.getStartPriceStatus());
        assertEquals(PriceCollectionStatus.MARKET_CLOSED, saved.getEndPriceStatus());
    }
}
