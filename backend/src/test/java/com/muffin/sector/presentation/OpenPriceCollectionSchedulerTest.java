package com.muffin.sector.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.muffin.sector.application.OpenPriceCollectionOrchestrator;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
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
        scheduler = new OpenPriceCollectionScheduler(orchestrator, clock);
    }

    @Test
    void collect_runsEveryFiveMinutesUntilNineFifteen() throws Exception {
        scheduler.collect();

        verify(orchestrator).collectOpenPrices(DATE);
        Scheduled scheduled =
                OpenPriceCollectionScheduler.class.getMethod("collect").getAnnotation(Scheduled.class);
        assertEquals("${muffin.batch.open-price.collection-cron:0 0-15/5 9 * * *}", scheduled.cron());
    }

    @Test
    void finalizeMissing_runsAtNineTwenty() throws Exception {
        scheduler.finalizeMissing();

        verify(orchestrator).collectAndFinalizeOpenPrices(DATE);
        Scheduled scheduled =
                OpenPriceCollectionScheduler.class.getMethod("finalizeMissing").getAnnotation(Scheduled.class);
        assertEquals("${muffin.batch.open-price.finalization-cron:0 20 9 * * *}", scheduled.cron());
    }
}
