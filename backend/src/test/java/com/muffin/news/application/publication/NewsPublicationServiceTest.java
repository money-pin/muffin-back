package com.muffin.news.application.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewsPublicationServiceTest {

    private final NewsRepository newsRepository = mock(NewsRepository.class);
    private final NewsPublicationService publicationService = new NewsPublicationService(newsRepository);

    /** 발행 배치는 PENDING 뉴스만 조회하여 모두 PUBLISHED 상태로 변경한다. */
    @Test
    void publishesAllPendingNews() {
        News first = pendingNews("https://example.com/1");
        News second = pendingNews("https://example.com/2");
        when(newsRepository.findAllByStatus(NewsStatus.PENDING)).thenReturn(List.of(first, second));

        int count = publicationService.publishPendingNews();

        assertThat(count).isEqualTo(2);
        assertThat(first.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(second.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
        verify(newsRepository).findAllByStatus(NewsStatus.PENDING);
    }

    private static News pendingNews(String originalUrl) {
        News news = News.processing(1L, "경제 뉴스", "매일경제", LocalDateTime.of(2026, 7, 16, 6, 0), null, originalUrl);
        news.completeReconstruction("한 줄 요약", "재구성된 본문");
        return news;
    }
}
