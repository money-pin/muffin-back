package com.muffin.quiz.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.quiz.application.generation.DailyQuizPublicationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "muffin.batch.quiz-publication.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DailyQuizPublicationScheduler {

    private final DailyQuizPublicationService dailyQuizPublicationService;
    private final BatchJobRunner batchJobRunner;
    private final Clock clock;

    /** 뉴스 공개 시각에 맞춰 READY 퀴즈 세트를 사용자에게 공개한다. */
    @Scheduled(
            cron = "${muffin.batch.quiz-publication.cron:0 0 9 * * *}",
            zone = "${muffin.batch.quiz-publication.zone:Asia/Seoul}")
    public void publishDailyQuiz() {
        // 기준일을 여기서 한 번만 정해 로그와 실제 발행 대상이 어긋나지 않게 한다(자정 근처 경합 방지).
        LocalDateTime publishedAt = LocalDateTime.now(clock);
        LocalDate quizDate = publishedAt.toLocalDate();
        batchJobRunner.run(BatchJob.QUIZ_PUBLICATION, BatchTrigger.SCHEDULER, quizDate, () -> {
            boolean published = dailyQuizPublicationService.publish(quizDate, publishedAt);
            // 발행할 세트가 없는 것은 이 잡의 실패가 아니라 선행 단계(퀴즈 생성)의 문제다.
            return published ? BatchJobReport.success() : BatchJobReport.skipped("no_ready_quiz_set");
        });
    }
}
