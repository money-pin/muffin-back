package com.muffin.notification.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobRunners;
import com.muffin.global.batch.BatchLogCapture;
import com.muffin.notification.application.dispatch.DispatchOutcome;
import com.muffin.notification.application.dispatch.NotificationDispatchSummary;
import com.muffin.notification.application.send.QuizReminderNotificationService;
import com.muffin.notification.application.send.RankingNotificationService;
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
class NotificationSchedulerTest {

    @Mock
    private QuizReminderNotificationService quizReminderNotificationService;

    @Mock
    private RankingNotificationService rankingNotificationService;

    @Test
    @DisplayName("퀴즈 리마인드는 KST 오늘 날짜로 보내고 발송 수를 배치 로그 한 줄에 남긴다")
    void remindQuiz_logsCounts() {
        NotificationScheduler scheduler = schedulerAt("2026-10-20T18:00:00+09:00");
        when(quizReminderNotificationService.remind(LocalDate.of(2026, 10, 20)))
                .thenReturn(NotificationDispatchSummary.empty()
                        .add(DispatchOutcome.SENT)
                        .add(DispatchOutcome.DUPLICATE));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.NOTIFICATION_QUIZ_REMINDER)) {
            scheduler.remindQuiz();

            assertThat(capture.line())
                    .contains("job=notification_quiz_reminder", "outcome=success", "sent=1", "duplicate=1");
        }
    }

    @Test
    @DisplayName("랭킹 알림을 보낼 날이 아니면 건너뛴 이유를 남긴다")
    void notifyRanking_logsSkipReason() {
        NotificationScheduler scheduler = schedulerAt("2026-10-21T12:00:00+09:00");
        when(rankingNotificationService.notifyIfDue())
                .thenReturn(NotificationDispatchSummary.skipped("not_first_trading_day"));

        try (BatchLogCapture capture = BatchLogCapture.on(BatchJob.NOTIFICATION_RANKING)) {
            scheduler.notifyRanking();

            assertThat(capture.line()).contains("outcome=skipped", "reason=not_first_trading_day");
        }
    }

    private NotificationScheduler schedulerAt(String dateTime) {
        Clock clock = Clock.fixed(OffsetDateTime.parse(dateTime).toInstant(), ZoneId.of("Asia/Seoul"));
        return new NotificationScheduler(
                quizReminderNotificationService, rankingNotificationService, BatchJobRunners.forTest(), clock);
    }
}
