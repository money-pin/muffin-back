package com.muffin.quiz.presentation;

import com.muffin.quiz.application.generation.DailyQuizPublicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "muffin.batch.quiz-publication.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DailyQuizPublicationScheduler {

    private final DailyQuizPublicationService dailyQuizPublicationService;

    /** 뉴스 공개 시각에 맞춰 READY 퀴즈 세트를 사용자에게 공개한다. */
    @Scheduled(
            cron = "${muffin.batch.quiz-publication.cron:0 0 9 * * *}",
            zone = "${muffin.batch.quiz-publication.zone:Asia/Seoul}")
    public void publishDailyQuiz() {
        try {
            boolean published = dailyQuizPublicationService.publishToday();
            log.info("BATCH-DAILY-QUIZ-PUBLICATION completed: published={}", published);
        } catch (Exception exception) {
            log.error("BATCH-DAILY-QUIZ-PUBLICATION failed", exception);
        }
    }
}
