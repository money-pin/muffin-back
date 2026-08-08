package com.muffin.news.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.news.application.rss.RssCollectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "muffin.batch.rss.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class RssCollectionScheduler {

    private final RssCollectionService collectionService;
    private final BatchJobRunner batchJobRunner;

    public RssCollectionScheduler(RssCollectionService collectionService, BatchJobRunner batchJobRunner) {
        this.collectionService = collectionService;
        this.batchJobRunner = batchJobRunner;
    }

    /** 설정된 cron 일정에 따라 RSS 뉴스 수집 배치를 실행한다. */
    @Scheduled(cron = "${muffin.batch.rss.cron}", zone = "${muffin.batch.rss.zone:Asia/Seoul}")
    public void collectDailyNews() {
        batchJobRunner.run(BatchJob.RSS_COLLECTION, BatchTrigger.SCHEDULER, () -> BatchJobReport.success()
                .with("collected", collectionService.collect()));
    }
}
