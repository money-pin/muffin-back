package com.muffin.news.application.reconstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NewsReconstructionServiceTest {

    private final NewsRepository newsRepository = mock(NewsRepository.class);
    private final NewsArticleContentClient articleContentClient = mock(NewsArticleContentClient.class);
    private final NewsRewriter newsRewriter = mock(NewsRewriter.class);
    private final NewsReconstructionService reconstructionService =
            new NewsReconstructionService(newsRepository, articleContentClient, newsRewriter);

    /** PROCESSING 뉴스를 재구성하면 결과를 저장하고 PENDING 상태로 전환한다. */
    @Test
    void reconstruct_fillsResultAndChangesStatusToPending() {
        Long newsId = 1L;
        String originalUrl = "https://example.com/news/1";
        News news = News.processing(1L, "경제 뉴스", "매일경제", LocalDateTime.of(2026, 7, 16, 6, 0), null, originalUrl);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(articleContentClient.fetch(originalUrl)).thenReturn("원문 본문");
        when(newsRewriter.rewrite(new NewsReconstructionRequest(
                        news.getTitle(), news.getPublisher(), news.getPublishedAt(), "원문 본문")))
                .thenReturn(new NewsReconstructionResult("한 줄 요약", "재구성된 본문", List.of()));

        reconstructionService.reconstruct(List.of(newsId));

        assertThat(news.getStatus()).isEqualTo(NewsStatus.PENDING);
        assertThat(news.getSummary()).isEqualTo("한 줄 요약");
        assertThat(news.getContent()).isEqualTo("재구성된 본문");
        assertThat(news.hasReconstructionResult()).isTrue();
        verify(newsRepository).save(news);
    }
}
