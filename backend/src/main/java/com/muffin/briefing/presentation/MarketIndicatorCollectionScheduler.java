package com.muffin.briefing.presentation;

import com.muffin.briefing.application.marketindicator.MarketIndicatorCollectionService;
import com.muffin.briefing.application.marketindicator.MarketIndicatorCollectionSummary;
import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "muffin.batch.market-indicator.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MarketIndicatorCollectionScheduler {

    private final MarketIndicatorCollectionService collectionService;
    private final BatchJobRunner batchJobRunner;
    private final Clock clock;

    /**
     * 브리핑 발행 전에 지표를 수집한다.
     *
     * <p>미국 장 마감이 한국시간 05:00~06:00(섬머타임에 따라 1시간 이동)이고 브리핑 발행이 07:30이라 그 사이에 돌려야 한다.
     * 여러 번 돌려도 같은 행을 갱신하므로 안전하며, 실패한 지표만 다음 시도에서 다시 받는다.
     */
    @Scheduled(
            cron = "${muffin.batch.market-indicator.cron:0 10,40 6 * * *}",
            zone = "${muffin.batch.market-indicator.zone:Asia/Seoul}")
    public void collectMarketIndicators() {
        // 기준일을 여기서 한 번만 정해 로그와 실제 처리 대상이 어긋나지 않게 한다(자정 근처 경합 방지).
        LocalDate baseDate = LocalDate.now(clock);
        batchJobRunner.run(
                BatchJob.MARKET_INDICATOR_COLLECT,
                BatchTrigger.SCHEDULER,
                baseDate,
                () -> report(collectionService.collect(baseDate)));
    }

    /**
     * 일부 지표만 실패하는 것은 기획이 허용한 상태라 성공으로 보고하되 실패 수를 남긴다. 전부 실패면 지표 블록이 통째로 비므로 실패로
     * 보고해 마지막 성공 시각이 갱신되지 않게 한다.
     */
    private BatchJobReport report(MarketIndicatorCollectionSummary summary) {
        if (!summary.hasAnySuccess()) {
            return BatchJobReport.failure("all_indicators_failed");
        }
        return BatchJobReport.success().with("succeeded", summary.succeeded()).with("failed", summary.failed());
    }
}
