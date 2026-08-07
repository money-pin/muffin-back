package com.muffin.sector.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.sector.infrastructure.EtfPriceCollector;
import com.muffin.sector.infrastructure.EtfPriceCollector.CollectionSummary;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 거래일 종가를 15:35에 최초 수집하고 15:45, 15:55, 16:05에 미완료 종목만 재시도한다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.batch.close-price.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class EtfClosePriceScheduler {

    private final EtfPriceCollector etfPriceCollector;
    private final BatchJobRunner batchJobRunner;
    private final Clock clock;

    @Scheduled(
            cron = "${muffin.batch.close-price.afternoon-cron:0 35,45,55 15 * * *}",
            zone = "${muffin.batch.close-price.zone:Asia/Seoul}")
    public void runAfternoon() {
        LocalDate date = LocalDate.now(clock);
        batchJobRunner.run(
                BatchJob.CLOSE_PRICE_COLLECT,
                BatchTrigger.SCHEDULER,
                date,
                () -> report(etfPriceCollector.collectClose(date)));
    }

    /** 마지막 시도는 수집에 더해 끝내 못 받은 종목을 FINAL_MISSING으로 확정한다. 수집과 하는 일이 달라 별도 잡으로 관측한다. */
    @Scheduled(
            cron = "${muffin.batch.close-price.final-cron:0 5 16 * * *}",
            zone = "${muffin.batch.close-price.zone:Asia/Seoul}")
    public void runFinalAttempt() {
        LocalDate date = LocalDate.now(clock);
        batchJobRunner.run(BatchJob.CLOSE_PRICE_FINALIZE, BatchTrigger.SCHEDULER, date, () -> {
            CollectionSummary summary = etfPriceCollector.collectClose(date);
            etfPriceCollector.finalizeMissingClosePrices(date);
            return report(summary);
        });
    }

    private BatchJobReport report(CollectionSummary summary) {
        return BatchJobReport.success()
                .with("success", summary.successCount())
                .with("skipped", summary.skippedCount())
                .with("failed", summary.failureCount());
    }
}
