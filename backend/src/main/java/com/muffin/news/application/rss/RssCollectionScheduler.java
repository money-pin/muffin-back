package com.muffin.news.application.rss;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "muffin.batch.rss.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class RssCollectionScheduler {

    private final RssCollectionService collectionService;

    public RssCollectionScheduler(RssCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    /** 설정된 cron 일정에 따라 RSS 뉴스 수집 배치를 실행한다. */
    @Scheduled(cron = "${muffin.batch.rss.cron}", zone = "${muffin.batch.rss.zone:Asia/Seoul}")
    public void collectDailyNews() {
        try {
            int count = collectionService.collect();
            log.info("BATCH-RSS completed: collected={}", count);
        } catch (Exception exception) {
            log.error("BATCH-RSS failed", exception);
        }
    }
}
