package com.muffin.investment.presentation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.investment.application.InvestmentFinalizationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvestmentFinalizationSchedulerTest {

    @Mock
    private InvestmentFinalizationService service;

    @Test
    @DisplayName("자정 스케줄은 전날을 마감 대상으로 사용한다")
    void run_finalizesPreviousDay() {
        Clock clock = clockAt("2026-07-14T00:00:00+09:00");
        InvestmentFinalizationScheduler scheduler = new InvestmentFinalizationScheduler(service, clock);

        scheduler.run();

        verify(service).finalizePendingDates(LocalDate.of(2026, 7, 13), LocalDateTime.of(2026, 7, 14, 0, 0));
    }

    @Test
    @DisplayName("캘린더 장애가 발생해도 스케줄러는 예외를 전파하지 않아 다음 실행을 허용한다")
    void run_catchesFailureForNextRetry() {
        Clock clock = clockAt("2026-07-14T00:10:00+09:00");
        InvestmentFinalizationScheduler scheduler = new InvestmentFinalizationScheduler(service, clock);
        when(service.finalizePendingDates(LocalDate.of(2026, 7, 13), LocalDateTime.of(2026, 7, 14, 0, 10)))
                .thenThrow(new IllegalStateException("calendar unavailable"));

        assertDoesNotThrow(scheduler::run);
    }

    @Test
    @DisplayName("복구 스케줄도 과거 미완료 날짜 마감 흐름을 실행한다")
    void recover_runsPendingDateFinalization() {
        Clock clock = clockAt("2026-07-14T02:00:00+09:00");
        InvestmentFinalizationScheduler scheduler = new InvestmentFinalizationScheduler(service, clock);

        scheduler.recover();

        verify(service).finalizePendingDates(LocalDate.of(2026, 7, 13), LocalDateTime.of(2026, 7, 14, 2, 0));
    }

    private static Clock clockAt(String timestamp) {
        return Clock.fixed(OffsetDateTime.parse(timestamp).toInstant(), ZoneId.of("Asia/Seoul"));
    }
}
