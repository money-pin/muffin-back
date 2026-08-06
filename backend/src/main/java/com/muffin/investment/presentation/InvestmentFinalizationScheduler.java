package com.muffin.investment.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.investment.application.InvestmentFinalizationResult;
import com.muffin.investment.application.InvestmentFinalizationService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 자정부터 장 시작 전까지 정확히 전날의 투자를 멱등 마감한다. 여러 날에 걸친 장애로 놓친 날짜는 MVP에서 수동 복구한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "muffin.batch.investment-finalization.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InvestmentFinalizationScheduler {

    private final InvestmentFinalizationService finalizationService;
    private final BatchJobRunner batchJobRunner;
    private final Clock clock;

    @Scheduled(
            cron = "${muffin.batch.investment-finalization.cron:0 0/10 0-8 * * *}",
            zone = "${muffin.batch.investment-finalization.zone:Asia/Seoul}")
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate investDate = now.toLocalDate().minusDays(1);
        batchJobRunner.run(BatchJob.INVESTMENT_FINALIZATION, BatchTrigger.SCHEDULER, investDate, () -> {
            InvestmentFinalizationResult result = finalizationService.finalizeInvestments(investDate, now);
            if (!result.tradingDay()) {
                return BatchJobReport.skipped("market_closed");
            }
            return BatchJobReport.success()
                    .with("target", result.targetCount())
                    .with("success", result.successCount())
                    .with("failed", result.failureCount());
        });
    }
}
