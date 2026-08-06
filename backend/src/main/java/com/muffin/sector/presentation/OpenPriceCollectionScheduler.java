package com.muffin.sector.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.sector.application.OpenPriceCollectionOrchestrator;
import com.muffin.sector.application.OpenPriceCollectionResult;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.batch.open-price.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class OpenPriceCollectionScheduler {

    private final OpenPriceCollectionOrchestrator orchestrator;
    private final BatchJobRunner batchJobRunner;
    private final Clock clock;

    @Scheduled(
            cron = "${muffin.batch.open-price.collection-cron:0 0-25/5 9 * * *}",
            zone = "${muffin.batch.open-price.zone:Asia/Seoul}")
    public void collect() {
        LocalDate priceDate = LocalDate.now(clock);
        batchJobRunner.run(
                BatchJob.OPEN_PRICE_COLLECT,
                BatchTrigger.SCHEDULER,
                priceDate,
                () -> report(orchestrator.collectOpenPrices(priceDate)));
    }

    @Scheduled(
            cron = "${muffin.batch.open-price.finalization-cron:0 30 9 * * *}",
            zone = "${muffin.batch.open-price.zone:Asia/Seoul}")
    public void finalizeMissing() {
        LocalDate priceDate = LocalDate.now(clock);
        batchJobRunner.run(
                BatchJob.OPEN_PRICE_FINALIZE,
                BatchTrigger.SCHEDULER,
                priceDate,
                () -> report(orchestrator.finalizeMissingOpenPrices(priceDate)));
    }

    /**
     * 수집이 끝나지 않은 것(INCOMPLETE)은 정산 트리거 이벤트가 발행되지 않았다는 뜻이라, 정상 스킵이 아니라 실패로 남긴다. 반복되면 그날 정산이 돌지 않는다.
     */
    private BatchJobReport report(OpenPriceCollectionResult result) {
        return switch (result.outcome()) {
            case COMPLETED -> BatchJobReport.success().with("targets", result.targetCount());
            case INCOMPLETE -> BatchJobReport.failure("collection_incomplete").with("targets", result.targetCount());
            case MARKET_CLOSED -> BatchJobReport.skipped("market_closed");
            case ALREADY_COMPLETED -> BatchJobReport.skipped("already_completed");
            case NO_TARGETS -> BatchJobReport.skipped("no_targets");
        };
    }
}
