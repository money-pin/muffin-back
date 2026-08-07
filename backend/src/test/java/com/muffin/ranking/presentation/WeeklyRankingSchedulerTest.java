package com.muffin.ranking.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchLogCapture;
import com.muffin.ranking.application.WeeklyRankingBatchResult;
import com.muffin.ranking.application.WeeklyRankingBatchService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyRankingSchedulerTest {

    @Mock
    private WeeklyRankingBatchService batchService;

    @Test
    @DisplayName("집계가 끝나면 참가자 수와 대상 주를 배치 로그 한 줄에 남긴다")
    void run_logsParticipantCountOnCreation() {
        WeeklyRankingScheduler scheduler =
                new WeeklyRankingScheduler(batchService, new BatchJobRunner(), clockAt("2026-07-13T09:40:00+09:00"));
        LocalDate weekStartDate = LocalDate.of(2026, 7, 6);
        when(batchService.createPreviousWeekRanking(LocalDate.of(2026, 7, 13)))
                .thenReturn(new WeeklyRankingBatchResult(WeeklyRankingBatchResult.Outcome.CREATED, weekStartDate, 42));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.WEEKLY_RANKING)) {
            scheduler.runMorning();

            assertThat(capture.line())
                    .contains("job=weekly_ranking", "outcome=success", "participants=42", "week_start=2026-07-06");
        }
    }

    @Test
    @DisplayName("이미 생성돼 있으면 건너뛴 이유를 배치 로그에 남긴다")
    void run_logsSkipReasonWhenAlreadyCreated() {
        WeeklyRankingScheduler scheduler =
                new WeeklyRankingScheduler(batchService, new BatchJobRunner(), clockAt("2026-07-13T10:00:00+09:00"));
        when(batchService.createPreviousWeekRanking(LocalDate.of(2026, 7, 13)))
                .thenReturn(new WeeklyRankingBatchResult(
                        WeeklyRankingBatchResult.Outcome.ALREADY_CREATED, LocalDate.of(2026, 7, 6), 0));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.WEEKLY_RANKING)) {
            scheduler.runFinal();

            assertThat(capture.line()).contains("outcome=skipped", "reason=already_created");
        }
    }

    @Test
    @DisplayName("오전 및 최종 재시도 스케줄은 KST 기준일로 같은 배치 서비스를 호출한다")
    void run_callsBatchServiceWithKstDate() {
        WeeklyRankingScheduler scheduler =
                new WeeklyRankingScheduler(batchService, new BatchJobRunner(), clockAt("2026-07-13T09:40:00+09:00"));
        when(batchService.createPreviousWeekRanking(LocalDate.of(2026, 7, 13)))
                .thenReturn(new WeeklyRankingBatchResult(
                        WeeklyRankingBatchResult.Outcome.ALREADY_CREATED, LocalDate.of(2026, 7, 6), 0));

        scheduler.runMorning();
        scheduler.runFinal();

        verify(batchService, times(2)).createPreviousWeekRanking(LocalDate.of(2026, 7, 13));
    }

    @Test
    @DisplayName("배치 실패가 발생해도 다음 스케줄 실행을 위해 예외를 전파하지 않는다")
    void run_catchesBatchFailure() {
        WeeklyRankingScheduler scheduler =
                new WeeklyRankingScheduler(batchService, new BatchJobRunner(), clockAt("2026-07-13T10:00:00+09:00"));
        doThrow(new IllegalStateException("database unavailable"))
                .when(batchService)
                .createPreviousWeekRanking(LocalDate.of(2026, 7, 13));

        assertDoesNotThrow(scheduler::runFinal);
    }

    private static Clock clockAt(String timestamp) {
        return Clock.fixed(OffsetDateTime.parse(timestamp).toInstant(), ZoneId.of("Asia/Seoul"));
    }
}
