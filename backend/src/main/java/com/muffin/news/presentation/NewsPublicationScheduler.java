package com.muffin.news.presentation;

import com.muffin.news.application.publication.NewsPublicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "muffin.batch.news-publication.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NewsPublicationScheduler {

    private final NewsPublicationService publicationService;

    /** 설정된 발행 시각에 PENDING 뉴스를 사용자에게 공개한다. */
    @Scheduled(
            cron = "${muffin.batch.news-publication.cron:0 0 9 * * *}",
            zone = "${muffin.batch.news-publication.zone:Asia/Seoul}")
    public void publishDailyNews() {
        try {
            int count = publicationService.publishPendingNews();
            log.info("BATCH-NEWS-PUBLICATION completed: published={}", count);
        } catch (Exception exception) {
            log.error("BATCH-NEWS-PUBLICATION failed", exception);
        }
    }
}
