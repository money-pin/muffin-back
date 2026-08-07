package com.muffin.news.presentation;

import com.muffin.global.batch.BatchJob;
import com.muffin.global.batch.BatchJobReport;
import com.muffin.global.batch.BatchJobRunner;
import com.muffin.global.batch.BatchTrigger;
import com.muffin.news.application.publication.NewsPublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "muffin.batch.news-publication.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NewsPublicationScheduler {

    private final NewsPublicationService publicationService;
    private final BatchJobRunner batchJobRunner;

    /** 설정된 발행 시각에 PENDING 뉴스를 사용자에게 공개한다. */
    @Scheduled(
            cron = "${muffin.batch.news-publication.cron:0 0 9 * * *}",
            zone = "${muffin.batch.news-publication.zone:Asia/Seoul}")
    public void publishDailyNews() {
        batchJobRunner.run(BatchJob.NEWS_PUBLICATION, BatchTrigger.SCHEDULER, () -> BatchJobReport.success()
                .with("published", publicationService.publishPendingNews()));
    }
}
