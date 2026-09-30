package com.muffin.briefing.presentation;

import com.muffin.briefing.application.generation.BriefingGenerationService;
import com.muffin.briefing.application.generation.BriefingGenerationSummary;
import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression(
        "${muffin.news.ai.enabled:false} && ${muffin.batch.briefing-generation.scheduler-enabled:true}")
public class BriefingGenerationScheduler {

    private final BriefingGenerationService briefingGenerationService;
    private final BatchJobRunner batchJobRunner;
    private final Clock clock;

    /**
     * 발행 시각 전까지 오늘 브리핑 생성을 반복 시도한다. 재구성이 끝난 뉴스가 최소 개수만큼 모이면 그때 생성되고, 이미 만들어졌으면
     * 건너뛴다.
     */
    @Scheduled(
            cron = "${muffin.batch.briefing-generation.cron:0 */10 6-7 * * *}",
            zone = "${muffin.batch.briefing-generation.zone:Asia/Seoul}")
    public void generateBriefing() {
        // 기준일을 여기서 한 번만 정해 로그와 실제 처리 대상이 어긋나지 않게 한다(자정 근처 경합 방지).
        LocalDate briefingDate = LocalDate.now(clock);
        batchJobRunner.run(
                BatchJob.BRIEFING_GENERATION,
                BatchTrigger.SCHEDULER,
                briefingDate,
                () -> report(briefingGenerationService.generate(briefingDate)));
    }

    /**
     * 생성 서비스는 실패해도 예외를 던지지 않고 이용 불가 브리핑을 저장한다. 그 결말을 성공으로 남기면 매번 실패해도 마지막 성공 시각이
     * 갱신돼 알림이 울리지 않으므로 실패로 보고한다.
     */
    private BatchJobReport report(BriefingGenerationSummary summary) {
        return switch (summary.outcome()) {
            case GENERATED -> BatchJobReport.success().with("issues", summary.issueCount());
            case ALREADY_RESERVED -> BatchJobReport.skipped("already_reserved");
            case INSUFFICIENT_NEWS -> BatchJobReport.deferred("insufficient_news");
            case FAILED -> BatchJobReport.failure("generation_failed");
        };
    }
}
