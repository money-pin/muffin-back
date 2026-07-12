package com.muffin.news.application.rss;

import com.muffin.news.application.rss.feed.RssFeedClient;
import com.muffin.news.application.rss.selection.RssArticleSelector;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RssCollectionService {

    private final RssFeedClient feedClient;
    private final RssArticleSelector articleSelector;
    private final RssFeedWriter feedWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final RssFeedProperties properties;

    public RssCollectionService(
            RssFeedClient feedClient,
            RssArticleSelector articleSelector,
            RssFeedWriter feedWriter,
            ApplicationEventPublisher eventPublisher,
            RssFeedProperties properties) {
        this.feedClient = feedClient;
        this.articleSelector = articleSelector;
        this.feedWriter = feedWriter;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

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
        if (!collectedIds.isEmpty()) {
            eventPublisher.publishEvent(new NewsCollectedEvent(collectedIds));
        }
        return collectedIds.size();
    }
}
