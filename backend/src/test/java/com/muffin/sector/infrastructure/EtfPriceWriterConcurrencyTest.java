package com.muffin.sector.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.muffin.sector.domain.etfprice.EtfPrice;
import com.muffin.sector.domain.etfprice.EtfPriceRepository;
import com.muffin.sector.domain.etfprice.PriceCollectionStatus;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    @DisplayName("같은 ETF의 시가와 종가를 동시에 저장해도 하나의 행에 모두 반영한다")
    void writesOpenAndCloseIntoSingleRow_whenCreatedConcurrently() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> open = executor.submit(
                    () -> runTogether(ready, start, () -> etfPriceWriter.writeOpen(ETF_ID, PRICE_DATE, 10_000L)));
            Future<?> close = executor.submit(
                    () -> runTogether(ready, start, () -> etfPriceWriter.writeClose(ETF_ID, PRICE_DATE, 10_500L)));

            ready.await(3, TimeUnit.SECONDS);
            start.countDown();
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
