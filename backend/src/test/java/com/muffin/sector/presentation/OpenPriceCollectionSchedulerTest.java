package com.muffin.sector.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobRunners;
import com.muffin.global.batch.BatchLogCapture;
import com.muffin.sector.application.OpenPriceCollectionOrchestrator;
import com.muffin.sector.application.OpenPriceCollectionResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class OpenPriceCollectionSchedulerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 13);

    @Mock
    private OpenPriceCollectionOrchestrator orchestrator;

    private OpenPriceCollectionScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));
        scheduler = new OpenPriceCollectionScheduler(orchestrator, BatchJobRunners.forTest(), clock);
    }

    @Test
    @DisplayName("종결까지 하고도 시세가 다 차지 않으면 정산 트리거가 발행되지 않았다는 뜻이므로 실패로 남긴다")
    void finalizeMissing_logsFailureWhenCollectionIsIncomplete() {
        when(orchestrator.collectAndFinalizeOpenPrices(DATE))
                .thenReturn(new OpenPriceCollectionResult(OpenPriceCollectionResult.Outcome.INCOMPLETE, 2));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.OPEN_PRICE_FINALIZE)) {
            scheduler.finalizeMissing();

            assertThat(capture.line())
                    .contains(
                            "job=open_price_finalize", "outcome=failure", "reason=collection_incomplete", "targets=2");
            assertThat(capture.level()).isEqualTo(Level.ERROR);
        }
    }

    @Test
    @DisplayName("휴장일에는 실패가 아니라 건너뛴 것으로 남긴다")
    void collect_logsSkipOnMarketClosed() {
        when(orchestrator.collectOpenPrices(DATE))
                .thenReturn(new OpenPriceCollectionResult(OpenPriceCollectionResult.Outcome.MARKET_CLOSED, 0));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.OPEN_PRICE_COLLECT)) {
            scheduler.collect();

            assertThat(capture.line()).contains("outcome=skipped", "reason=market_closed");
            assertThat(capture.level()).isEqualTo(Level.INFO);
        }
    }

    @Test
    void collect_runsEveryFiveMinutesUntilNineFifteen() throws Exception {
        when(orchestrator.collectOpenPrices(DATE))
                .thenReturn(new OpenPriceCollectionResult(OpenPriceCollectionResult.Outcome.COLLECTED, 2));

        scheduler.collect();

        verify(orchestrator).collectOpenPrices(DATE);
        Scheduled scheduled =
                OpenPriceCollectionScheduler.class.getMethod("collect").getAnnotation(Scheduled.class);
        assertEquals("${muffin.batch.open-price.collection-cron:0 0-15/5 9 * * *}", scheduled.cron());
    }

    @Test
    void finalizeMissing_runsAtNineTwenty() throws Exception {
        when(orchestrator.collectAndFinalizeOpenPrices(DATE))
                .thenReturn(new OpenPriceCollectionResult(OpenPriceCollectionResult.Outcome.COMPLETED, 2));

        scheduler.finalizeMissing();

        verify(orchestrator).collectAndFinalizeOpenPrices(DATE);
        Scheduled scheduled =
                OpenPriceCollectionScheduler.class.getMethod("finalizeMissing").getAnnotation(Scheduled.class);
        assertEquals("${muffin.batch.open-price.finalization-cron:0 20 9 * * *}", scheduled.cron());
    }
}
