package com.muffin.sector.presentation;

import static org.mockito.Mockito.verify;

import com.muffin.sector.infrastructure.EtfPriceCollector;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EtfClosePriceSchedulerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 13);

    @Mock
    private EtfPriceCollector collector;

    private EtfClosePriceScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock clock =
                Clock.fixed(OffsetDateTime.parse("2026-07-13T15:35:00+09:00").toInstant(), ZoneId.of("Asia/Seoul"));
        scheduler = new EtfClosePriceScheduler(collector, clock);
    }

    @Test
    @DisplayName("오후 종가 스케줄은 당일 종가 수집을 실행한다")
    void runAfternoon_collectsClose() {
        scheduler.runAfternoon();

        verify(collector).collectClose(DATE);
    }

    @Test
    @DisplayName("16시 05분 최종 스케줄도 당일 종가 수집을 실행한다")
    void runFinalAttempt_collectsClose() {
        scheduler.runFinalAttempt();

        verify(collector).collectClose(DATE);
    }
}
