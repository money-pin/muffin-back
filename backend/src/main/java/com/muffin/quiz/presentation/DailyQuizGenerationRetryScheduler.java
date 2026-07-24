package com.muffin.quiz.presentation;

import com.muffin.quiz.application.generation.DailyQuizGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "${muffin.news.ai.enabled:false} && ${muffin.batch.quiz-generation-retry.scheduler-enabled:true}")
public class DailyQuizGenerationRetryScheduler {

    private final DailyQuizGenerationService dailyQuizGenerationService;

    /** 이벤트 기반 생성이 실패했거나 누락된 경우를 대비해 발행 전까지 오늘 퀴즈 생성을 재시도한다. */
    @Scheduled(
            cron = "${muffin.batch.quiz-generation-retry.cron:0 */10 6-8 * * *}",
            zone = "${muffin.batch.quiz-generation-retry.zone:Asia/Seoul}")
    public void retryDailyQuizGeneration() {
        try {
            dailyQuizGenerationService.generateToday();
            log.info("BATCH-DAILY-QUIZ-GENERATION-RETRY completed");
        } catch (Exception exception) {
            log.error("BATCH-DAILY-QUIZ-GENERATION-RETRY failed", exception);
        }
    }
}
