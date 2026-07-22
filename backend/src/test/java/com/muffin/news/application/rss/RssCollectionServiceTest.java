package com.muffin.news.application.rss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RssCollectionServiceTest {

    private final RssFeedClient feedClient = mock(RssFeedClient.class);
    private final RssArticleSelector articleSelector = mock(RssArticleSelector.class);
    private final RssFeedWriter feedWriter = mock(RssFeedWriter.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final RssFeedProperties properties = new RssFeedProperties(
            "매일경제",
            List.of(
                    new RssFeedSource("경제", "https://feed.example/economy"),
                    new RssFeedSource("증권", "https://feed.example/stock"),
                    new RssFeedSource("세계", "https://feed.example/world")));
    private final RssCollectionService service =
            new RssCollectionService(feedClient, articleSelector, feedWriter, eventPublisher, properties);

    /** 한 피드의 조회가 실패해도 나머지 피드는 계속 수집하고, 성공한 결과만 모아 이벤트를 발행한다. */
    @Test
    void continuesWithRemainingFeedsWhenOneFeedFails() {
        RssArticle economy = article("경제", "https://example.com/1");
        RssArticle world = article("세계", "https://example.com/2");
        when(feedClient.fetch("https://feed.example/economy")).thenReturn(List.of(economy));
        when(feedClient.fetch("https://feed.example/stock")).thenThrow(new IllegalStateException("feed unavailable"));
        when(feedClient.fetch("https://feed.example/world")).thenReturn(List.of(world));
        when(articleSelector.select("경제", List.of(economy))).thenReturn(List.of(economy));
        when(articleSelector.select("세계", List.of(world))).thenReturn(List.of(world));
        when(feedWriter.save("경제", "매일경제", List.of(economy))).thenReturn(List.of(1L));
        when(feedWriter.save("세계", "매일경제", List.of(world))).thenReturn(List.of(2L));

        int count = service.collect();

        assertThat(count).isEqualTo(2);
        verify(feedClient).fetch("https://feed.example/world");
        verify(eventPublisher).publishEvent(new NewsCollectedEvent(List.of(1L, 2L)));
    }

    /** 한 피드의 저장이 실패해도 나머지 피드는 계속 저장하고, 실패한 피드만 수집 결과에서 제외한다. */
    @Test
    void continuesWhenAFeedCannotBeSaved() {
        RssArticle economy = article("경제", "https://example.com/1");
        RssArticle stock = article("증권", "https://example.com/2");
        when(feedClient.fetch("https://feed.example/economy")).thenReturn(List.of(economy));
        when(feedClient.fetch("https://feed.example/stock")).thenReturn(List.of(stock));
        when(feedClient.fetch("https://feed.example/world")).thenReturn(List.of());
        when(articleSelector.select("경제", List.of(economy))).thenReturn(List.of(economy));
        when(articleSelector.select("증권", List.of(stock))).thenReturn(List.of(stock));
        when(articleSelector.select("세계", List.of())).thenReturn(List.of());
        when(feedWriter.save("경제", "매일경제", List.of(economy)))
                .thenThrow(new IllegalStateException("category not found"));
        when(feedWriter.save("증권", "매일경제", List.of(stock))).thenReturn(List.of(2L));

        assertThat(service.collect()).isEqualTo(1);

        verify(feedWriter).save("증권", "매일경제", List.of(stock));
        verify(eventPublisher).publishEvent(new NewsCollectedEvent(List.of(2L)));
    }

    /** 선별기가 걸러낸 기사는 저장하지 않고, 선별된 기사만 저장소에 전달한다. */
    @Test
    void savesOnlyArticlesReturnedByTheSelector() {
        RssArticle selected = article("선별", "https://example.com/1");
        RssArticle rejected = article("탈락", "https://example.com/2");
        when(feedClient.fetch("https://feed.example/economy")).thenReturn(List.of(selected, rejected));
        when(feedClient.fetch("https://feed.example/stock")).thenReturn(List.of());
        when(feedClient.fetch("https://feed.example/world")).thenReturn(List.of());
        when(articleSelector.select("경제", List.of(selected, rejected))).thenReturn(List.of(selected));
        when(articleSelector.select("증권", List.of())).thenReturn(List.of());
        when(articleSelector.select("세계", List.of())).thenReturn(List.of());
        when(feedWriter.save("경제", "매일경제", List.of(selected))).thenReturn(List.of(1L));
        when(feedWriter.save("증권", "매일경제", List.of())).thenReturn(List.of());
        when(feedWriter.save("세계", "매일경제", List.of())).thenReturn(List.of());

        assertThat(service.collect()).isEqualTo(1);

        verify(feedWriter).save("경제", "매일경제", List.of(selected));
    }

    private static RssArticle article(String title, String url) {
        return new RssArticle(title, url, "요약", null, LocalDateTime.of(2026, 7, 12, 6, 0));
    }
}
