package com.muffin.quiz.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchLogCapture;
import com.muffin.quiz.application.generation.DailyQuizGenerationService;
import com.muffin.quiz.application.generation.DailyQuizGenerationSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyQuizGenerationRetrySchedulerTest {

    private final DailyQuizGenerationService dailyQuizGenerationService = mock(DailyQuizGenerationService.class);
    private static final LocalDate QUIZ_DATE = LocalDate.of(2026, 7, 13);

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-12T22:10:00Z"), ZoneId.of("Asia/Seoul"));
    private final DailyQuizGenerationRetryScheduler scheduler =
            new DailyQuizGenerationRetryScheduler(dailyQuizGenerationService, new BatchJobRunner(), clock);

    @Test
    @DisplayName("재시도 스케줄러는 오늘 퀴즈 생성을 호출하고 생성 문항 수를 로그에 남긴다")
    void retryDailyQuizGeneration_callsGenerationService() {
        when(dailyQuizGenerationService.generate(QUIZ_DATE))
                .thenReturn(new DailyQuizGenerationSummary(DailyQuizGenerationSummary.Outcome.GENERATED, 5));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.QUIZ_GENERATION_RETRY)) {
            scheduler.retryDailyQuizGeneration();

            assertThat(capture.line()).contains("job=quiz_generation_retry", "outcome=success", "questions=5");
        }
        verify(dailyQuizGenerationService).generate(QUIZ_DATE);
    }

    @Test
    @DisplayName("생성 서비스가 예외를 삼키고 실패로 끝나면 배치 로그도 실패로 남는다")
    void retryDailyQuizGeneration_logsFailureWhenGenerationFails() {
        when(dailyQuizGenerationService.generate(QUIZ_DATE))
                .thenReturn(new DailyQuizGenerationSummary(DailyQuizGenerationSummary.Outcome.FAILED, 0));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.QUIZ_GENERATION_RETRY)) {
            scheduler.retryDailyQuizGeneration();

            assertThat(capture.line()).contains("outcome=failure", "reason=generation_failed");
            assertThat(capture.level()).isEqualTo(Level.ERROR);
        }
    }

    @Test
    @DisplayName("뉴스가 아직 부족하면 실패가 아니라 건너뛴 것으로 남긴다")
    void retryDailyQuizGeneration_logsSkipWhenNewsIsInsufficient() {
        when(dailyQuizGenerationService.generate(QUIZ_DATE))
                .thenReturn(new DailyQuizGenerationSummary(DailyQuizGenerationSummary.Outcome.INSUFFICIENT_NEWS, 0));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.QUIZ_GENERATION_RETRY)) {
            scheduler.retryDailyQuizGeneration();

            assertThat(capture.line()).contains("outcome=skipped", "reason=insufficient_news");
        }
    }

    @Test
    @DisplayName("재시도 스케줄러는 생성 서비스 예외를 밖으로 전파하지 않는다")
    void retryDailyQuizGeneration_doesNotThrowWhenGenerationFails() {
        doThrow(new IllegalStateException("generation failed"))
                .when(dailyQuizGenerationService)
                .generate(QUIZ_DATE);

        scheduler.retryDailyQuizGeneration();

        verify(dailyQuizGenerationService).generate(QUIZ_DATE);
    }
}
