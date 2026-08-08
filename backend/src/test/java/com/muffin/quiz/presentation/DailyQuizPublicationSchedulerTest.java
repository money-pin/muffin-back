package com.muffin.quiz.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobRunners;
import com.muffin.global.batch.BatchLogCapture;
import com.muffin.quiz.application.generation.DailyQuizPublicationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DailyQuizPublicationSchedulerTest {

    private static final LocalDate QUIZ_DATE = LocalDate.of(2026, 7, 13);
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 7, 13, 9, 0);

    private final DailyQuizPublicationService dailyQuizPublicationService = mock(DailyQuizPublicationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-13T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final DailyQuizPublicationScheduler scheduler =
            new DailyQuizPublicationScheduler(dailyQuizPublicationService, BatchJobRunners.forTest(), clock);

    @Test
    @DisplayName("발행 기준일을 스케줄러가 정해 서비스와 배치 로그에 같은 값을 넘긴다")
    void publishDailyQuiz_passesSameBusinessDateToServiceAndLog() {
        when(dailyQuizPublicationService.publish(QUIZ_DATE, PUBLISHED_AT)).thenReturn(true);

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.QUIZ_PUBLICATION)) {
            scheduler.publishDailyQuiz();

            assertThat(capture.line()).contains("job=quiz_publication", "date=2026-07-13", "outcome=success");
        }
        verify(dailyQuizPublicationService).publish(QUIZ_DATE, PUBLISHED_AT);
    }

    @Test
    @DisplayName("발행할 세트가 없는 것은 사용자에게 퀴즈가 안 나가는 상태라 미룸으로 남긴다")
    void publishDailyQuiz_logsDeferredWhenNoReadyQuizSet() {
        when(dailyQuizPublicationService.publish(QUIZ_DATE, PUBLISHED_AT)).thenReturn(false);

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.QUIZ_PUBLICATION)) {
            scheduler.publishDailyQuiz();

            assertThat(capture.line()).contains("outcome=deferred", "reason=no_ready_quiz_set", "date=2026-07-13");
        }
    }
}
