package com.muffin.news.application.rss;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RssCollectionService {

    private final RssFeedClient feedClient;
    private final RssArticleSelector articleSelector;
    private final RssFeedWriter feedWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final RssFeedProperties properties;

    /** 뉴스 피드별로 격리하여 RSS를 조회하고 저장한 뒤 수집 이벤트를 발행한다. */
    public int collect() {
        List<Long> collectedIds = new ArrayList<>();
        for (RssFeedSource source : properties.feeds()) {
            try {
                List<RssArticle> candidates = feedClient.fetch(source.url());
                List<RssArticle> selectedArticles = articleSelector.select(source.category(), candidates);
                collectedIds.addAll(feedWriter.save(source.category(), properties.publisher(), selectedArticles));
            } catch (Exception exception) {
                log.error(
                        "RSS feed collection failed: category={}, url={}", source.category(), source.url(), exception);
            }
        }
        if (collectedIds.isEmpty()) {
            log.warn("RSS feed collection completed, but no articles were collected.");
            return 0;
        }

        eventPublisher.publishEvent(new NewsCollectedEvent(collectedIds));
        return collectedIds.size();
    }
}
