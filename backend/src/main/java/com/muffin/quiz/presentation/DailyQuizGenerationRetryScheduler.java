package com.muffin.quiz.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.quiz.application.generation.DailyQuizGenerationService;
import com.muffin.quiz.application.generation.DailyQuizGenerationSummary;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "${muffin.news.ai.enabled:false} && ${muffin.batch.quiz-generation-retry.scheduler-enabled:true}")
public class DailyQuizGenerationRetryScheduler {

    private final DailyQuizGenerationService dailyQuizGenerationService;
    private final BatchJobRunner batchJobRunner;
    private final Clock clock;

    /** 이벤트 기반 생성이 실패했거나 누락된 경우를 대비해 발행 전까지 오늘 퀴즈 생성을 재시도한다. */
    @Scheduled(
            cron = "${muffin.batch.quiz-generation-retry.cron:0 */10 6-8 * * *}",
            zone = "${muffin.batch.quiz-generation-retry.zone:Asia/Seoul}")
    public void retryDailyQuizGeneration() {
        // 기준일을 여기서 한 번만 정해 로그와 실제 처리 대상이 어긋나지 않게 한다(자정 근처 경합 방지).
        LocalDate quizDate = LocalDate.now(clock);
        batchJobRunner.run(
                BatchJob.QUIZ_GENERATION_RETRY,
                BatchTrigger.SCHEDULER,
                quizDate,
                () -> report(dailyQuizGenerationService.generate(quizDate)));
    }

    /**
     * 생성 서비스는 실패해도 예외를 던지지 않고 이용 불가 세트를 저장한다. 그 결말을 그대로 성공으로 남기면 매 시도 실패해도 마지막 성공 시각이 갱신돼 알림이 울리지 않으므로,
     * 실패로 보고한다.
     */
    private BatchJobReport report(DailyQuizGenerationSummary summary) {
        return switch (summary.outcome()) {
            case GENERATED -> BatchJobReport.success().with("questions", summary.questionCount());
            case ALREADY_RESERVED -> BatchJobReport.skipped("already_reserved");
            case INSUFFICIENT_NEWS -> BatchJobReport.deferred("insufficient_news");
            case FAILED -> BatchJobReport.failure("generation_failed");
        };
    }
}
