package com.muffin.sector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
@Import({EtfPriceWriter.class, EtfPriceWriteTransaction.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EtfPriceWriterConcurrencyTest {

    private static final Long ETF_ID = 1L;
    private static final LocalDate PRICE_DATE = LocalDate.of(2026, 7, 10);

    @Autowired
    private EtfPriceWriter etfPriceWriter;

    @Autowired
    private EtfPriceRepository etfPriceRepository;

    @AfterEach
    void tearDown() {
        etfPriceRepository.deleteAll();
    }

    @Test
    @DisplayName("두 작업이 없는 행을 함께 확인해도 유니크 키 경합을 복구한다")
    void recoversUniqueKeyRace_afterBothTransactionsObserveMissingRow() throws Exception {
        CountDownLatch readyToInsert = new CountDownLatch(2);
        CountDownLatch insert = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> open = executor.submit(() -> etfPriceWriter.upsert(
                    ETF_ID,
                    PRICE_DATE,
                    existing -> existing.recordOpen(10_000L),
                    () -> createAfterBothRead(
                            readyToInsert, insert, () -> EtfPrice.open(ETF_ID, PRICE_DATE, 10_000L))));
            Future<?> close = executor.submit(() -> etfPriceWriter.upsert(
                    ETF_ID,
                    PRICE_DATE,
                    existing -> existing.recordClose(10_500L),
                    () -> createAfterBothRead(
                            readyToInsert, insert, () -> EtfPrice.create(ETF_ID, PRICE_DATE, null, 10_500L))));

            boolean bothObservedMissingRow = readyToInsert.await(3, TimeUnit.SECONDS);
            insert.countDown();
            assertTrue(bothObservedMissingRow);
            open.get(5, TimeUnit.SECONDS);
            close.get(5, TimeUnit.SECONDS);
        }

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertEquals(1, etfPriceRepository.count());
        assertEquals(10_000L, saved.getStartPrice());
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getStartPriceStatus());
        assertEquals(10_500L, saved.getEndPrice());
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getEndPriceStatus());
    }

    @Test
    @DisplayName("기존 행의 시가와 종가를 동시에 갱신해도 두 값을 모두 보존한다")
    void preservesBothValues_whenExistingRowIsUpdatedConcurrently() throws Exception {
        etfPriceRepository.saveAndFlush(EtfPrice.pending(ETF_ID, PRICE_DATE));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> open = executor.submit(
                    () -> runTogether(ready, start, () -> etfPriceWriter.writeOpen(ETF_ID, PRICE_DATE, 10_000L)));
            Future<?> close = executor.submit(
                    () -> runTogether(ready, start, () -> etfPriceWriter.writeClose(ETF_ID, PRICE_DATE, 10_500L)));

            boolean bothReady = ready.await(3, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(bothReady);
            open.get(5, TimeUnit.SECONDS);
            close.get(5, TimeUnit.SECONDS);
        }

        EtfPrice saved =
                etfPriceRepository.findByEtfIdAndPriceDate(ETF_ID, PRICE_DATE).orElseThrow();
        assertEquals(1, etfPriceRepository.count());
        assertEquals(10_000L, saved.getStartPrice());
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getStartPriceStatus());
        assertEquals(10_500L, saved.getEndPrice());
        assertEquals(PriceCollectionStatus.SUCCESS, saved.getEndPriceStatus());
    }

    private EtfPrice createAfterBothRead(
            CountDownLatch readyToInsert, CountDownLatch insert, Supplier<EtfPrice> create) {
        readyToInsert.countDown();
        try {
            insert.await();
            return create.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("시세 insert 경합 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }

    private void runTogether(CountDownLatch ready, CountDownLatch start, Runnable action) {
        ready.countDown();
        try {
            start.await();
            action.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기 중 인터럽트가 발생했습니다.", exception);
        }
    }
}
