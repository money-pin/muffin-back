package com.muffin.news.application.rss;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * RSS를 수집하고 선별된 기사를 저장한 뒤 수집 이벤트를 발행한다.
 *
 * <p>수집은 피드별로 격리하지만 선별은 {@link RssSelectionStrategy}에 위임한다. 선별 방식이 카테고리별인지 전체 통합인지에 따라
 * AI 호출 단위가 달라지는데, 이 서비스는 그 차이를 알지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RssCollectionService {

    private final RssFeedClient feedClient;
    private final RssSelectionStrategy selectionStrategy;
    private final RssFeedWriter feedWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final RssFeedProperties properties;

    /** 모든 피드를 수집한 뒤 선별하고 저장한다. 한 피드의 수집 실패가 나머지 피드를 버리지 않는다. */
    public int collect() {
        Map<String, List<RssArticle>> candidatesByCategory = fetchCandidates();
        if (candidatesByCategory.isEmpty()) {
            log.warn("RSS feed collection completed, but no candidate was fetched.");
            return 0;
        }

        Map<String, List<RssArticle>> selected = selectionStrategy.select(candidatesByCategory);

        List<Long> collectedIds = new ArrayList<>();
        for (Map.Entry<String, List<RssArticle>> entry : selected.entrySet()) {
            // 저장은 카테고리별 트랜잭션이므로 한 카테고리의 저장 실패가 나머지를 되돌리지 않는다.
            try {
                collectedIds.addAll(feedWriter.save(entry.getKey(), properties.publisher(), entry.getValue()));
            } catch (Exception exception) {
                log.error("RSS feed save failed: category={}", entry.getKey(), exception);
            }
        }

        if (collectedIds.isEmpty()) {
            log.warn("RSS feed collection completed, but no articles were collected.");
            return 0;
        }

        eventPublisher.publishEvent(new NewsCollectedEvent(collectedIds));
        return collectedIds.size();
    }

    /** 피드를 모두 읽어 카테고리별 후보로 모은다. 같은 카테고리에 피드가 여러 개면 후보가 합쳐진다. */
    private Map<String, List<RssArticle>> fetchCandidates() {
        Map<String, List<RssArticle>> candidatesByCategory = new LinkedHashMap<>();
        for (RssFeedSource source : properties.feeds()) {
            try {
                candidatesByCategory
                        .computeIfAbsent(source.category(), key -> new ArrayList<>())
                        .addAll(feedClient.fetch(source.url()));
            } catch (Exception exception) {
                log.error(
                        "RSS feed collection failed: category={}, url={}", source.category(), source.url(), exception);
            }
        }
        candidatesByCategory.values().removeIf(List::isEmpty);
        return candidatesByCategory;
    }
}
