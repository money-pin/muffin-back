package com.muffin.notification.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.notification.application.send.QuizReminderNotificationService;
import com.muffin.notification.application.send.RankingNotificationService;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 정해진 시각에 보내는 알림. 퀴즈 리마인드(NOTI-02)와, 오전에 확정된 랭킹 알림(NOTI-04)을 보낸다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "muffin.batch.notification.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationScheduler {

    private final QuizReminderNotificationService quizReminderNotificationService;
    private final RankingNotificationService rankingNotificationService;
    private final BatchJobRunner batchJobRunner;
    private final Clock clock;

    /** 매일 18:00, 오늘의 퀴즈를 아직 끝내지 않은 사용자에게 보낸다. */
    @Scheduled(
            cron = "${muffin.batch.notification.quiz-reminder-cron:0 0 18 * * *}",
            zone = "${muffin.batch.notification.zone:Asia/Seoul}")
    public void remindQuiz() {
        LocalDate today = LocalDate.now(clock);
        batchJobRunner.run(
                BatchJob.NOTIFICATION_QUIZ_REMINDER,
                BatchTrigger.SCHEDULER,
                today,
                () -> quizReminderNotificationService.remind(today).toReport());
    }

    /** 매일 12:00. 이번 주 첫 영업일이 아니거나 랭킹이 아직 없으면 건너뛴다. */
    @Scheduled(
            cron = "${muffin.batch.notification.ranking-cron:0 0 12 * * *}",
            zone = "${muffin.batch.notification.zone:Asia/Seoul}")
    public void notifyRanking() {
        batchJobRunner.run(
                BatchJob.NOTIFICATION_RANKING,
                BatchTrigger.SCHEDULER,
                LocalDate.now(clock),
                () -> rankingNotificationService.notifyIfDue().toReport());
    }
}
