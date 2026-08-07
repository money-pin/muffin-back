package com.muffin.investment.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobRunners;
import com.muffin.global.batch.BatchLogCapture;
import com.muffin.investment.application.InvestmentFinalizationResult;
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
    @DisplayName("08시 50분 재시도도 정확히 전날을 마감 대상으로 사용한다")
    void run_finalizesPreviousDay() {
        Clock clock = clockAt("2026-07-14T08:50:00+09:00");
        InvestmentFinalizationScheduler scheduler =
                new InvestmentFinalizationScheduler(service, BatchJobRunners.forTest(), clock);
        when(service.finalizeInvestments(LocalDate.of(2026, 7, 13), LocalDateTime.of(2026, 7, 14, 8, 50)))
                .thenReturn(new InvestmentFinalizationResult(LocalDate.of(2026, 7, 13), true, 3, 3, 0));

        scheduler.run();

        verify(service).finalizeInvestments(LocalDate.of(2026, 7, 13), LocalDateTime.of(2026, 7, 14, 8, 50));
    }

    @Test
    @DisplayName("마감 대상이 있는데 전부 실패하면 배치 실패로 남긴다")
    void run_logsFailureWhenEveryTargetFailed() {
        Clock clock = clockAt("2026-07-14T08:50:00+09:00");
        InvestmentFinalizationScheduler scheduler =
                new InvestmentFinalizationScheduler(service, BatchJobRunners.forTest(), clock);
        when(service.finalizeInvestments(LocalDate.of(2026, 7, 13), LocalDateTime.of(2026, 7, 14, 8, 50)))
                .thenReturn(new InvestmentFinalizationResult(LocalDate.of(2026, 7, 13), true, 3, 0, 3));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.INVESTMENT_FINALIZATION)) {
            scheduler.run();

            assertThat(capture.line()).contains("outcome=failure", "reason=all_targets_failed", "failed=3");
            assertThat(capture.level()).isEqualTo(Level.ERROR);
        }
    }

    @Test
    @DisplayName("일부만 실패하면 배치는 성공이고 실패 건수만 수치로 남는다")
    void run_keepsPartialFailureAsSuccess() {
        Clock clock = clockAt("2026-07-14T08:50:00+09:00");
        InvestmentFinalizationScheduler scheduler =
                new InvestmentFinalizationScheduler(service, BatchJobRunners.forTest(), clock);
        when(service.finalizeInvestments(LocalDate.of(2026, 7, 13), LocalDateTime.of(2026, 7, 14, 8, 50)))
                .thenReturn(new InvestmentFinalizationResult(LocalDate.of(2026, 7, 13), true, 3, 2, 1));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.INVESTMENT_FINALIZATION)) {
            scheduler.run();

            assertThat(capture.line()).contains("outcome=success", "success=2", "failed=1");
        }
    }

    @Test
    @DisplayName("캘린더 장애가 발생해도 스케줄러는 예외를 전파하지 않아 다음 실행을 허용한다")
    void run_catchesFailureForNextRetry() {
        Clock clock = clockAt("2026-07-14T00:10:00+09:00");
        InvestmentFinalizationScheduler scheduler =
                new InvestmentFinalizationScheduler(service, BatchJobRunners.forTest(), clock);
        when(service.finalizeInvestments(LocalDate.of(2026, 7, 13), LocalDateTime.of(2026, 7, 14, 0, 10)))
                .thenThrow(new IllegalStateException("calendar unavailable"));

        assertDoesNotThrow(scheduler::run);
    }

    private static Clock clockAt(String timestamp) {
        return Clock.fixed(OffsetDateTime.parse(timestamp).toInstant(), ZoneId.of("Asia/Seoul"));
    }
}
