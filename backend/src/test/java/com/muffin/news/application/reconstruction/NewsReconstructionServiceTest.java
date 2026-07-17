package com.muffin.news.application.reconstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.news.enums.NewsStatus;
import com.muffin.news.domain.sectorimpact.NewsSectorImpact;
import com.muffin.news.domain.sectorimpact.NewsSectorImpactRepository;
import com.muffin.news.domain.sectorimpact.enums.ImpactType;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class NewsReconstructionServiceTest {

    private final NewsRepository newsRepository = mock(NewsRepository.class);
    private final NewsSectorImpactRepository newsSectorImpactRepository = mock(NewsSectorImpactRepository.class);
    private final SectorRepository sectorRepository = mock(SectorRepository.class);
    private final NewsArticleContentClient articleContentClient = mock(NewsArticleContentClient.class);
    private final NewsRewriter newsRewriter = mock(NewsRewriter.class);
    private final NewsReconstructionService reconstructionService = new NewsReconstructionService(
            newsRepository, newsSectorImpactRepository, sectorRepository, articleContentClient, newsRewriter);

    /** PROCESSING 뉴스를 재구성하면 결과를 저장하고 PENDING 상태로 전환한다. */
    @Test
    void reconstruct_fillsResultAndChangesStatusToPending() {
        Long newsId = 1L;
        String originalUrl = "https://example.com/news/1";
        News news = News.processing(1L, "경제 뉴스", "매일경제", LocalDateTime.of(2026, 7, 16, 6, 0), null, originalUrl);
        ReflectionTestUtils.setField(news, "id", newsId);
        Sector sector = Sector.create(1L, 1L, "금", null, "GOLD", 1);
        ReflectionTestUtils.setField(sector, "id", 10L);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(articleContentClient.fetch(originalUrl)).thenReturn("원문 본문");
        when(sectorRepository.findBySectorCode("GOLD")).thenReturn(Optional.of(sector));
        when(newsRewriter.rewrite(new NewsReconstructionRequest(
                        news.getTitle(), news.getPublisher(), news.getPublishedAt(), "원문 본문")))
                .thenReturn(new NewsReconstructionResult(
                        "한 줄 요약",
                        "재구성된 본문",
                        List.of(new SectorImpactResult("GOLD", ImpactType.POSITIVE, "금 가격 상승 가능성")),
                        List.of()));

        reconstructionService.reconstruct(List.of(newsId));

        assertThat(news.getStatus()).isEqualTo(NewsStatus.PENDING);
        assertThat(news.getSummary()).isEqualTo("한 줄 요약");
        assertThat(news.getContent()).isEqualTo("재구성된 본문");
        assertThat(news.hasReconstructionResult()).isTrue();
        verify(newsRepository).save(news);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NewsSectorImpact>> impactsCaptor = ArgumentCaptor.forClass(List.class);
        verify(newsSectorImpactRepository).saveAll(impactsCaptor.capture());
        assertThat(impactsCaptor.getValue()).singleElement().satisfies(impact -> {
            assertThat(impact.getNewsId()).isEqualTo(newsId);
            assertThat(impact.getSectorId()).isEqualTo(10L);
            assertThat(impact.getImpact()).isEqualTo(ImpactType.POSITIVE);
        });
    }

    @Test
    void reconstruct_continuesWhenMarkAsFailedAlsoFails() {
        Long failedNewsId = 1L;
        Long nextNewsId = 2L;
        News nextNews = mock(News.class);
        when(nextNews.getStatus()).thenReturn(NewsStatus.PUBLISHED);
        when(newsRepository.findById(failedNewsId)).thenThrow(new IllegalStateException("database unavailable"));
        when(newsRepository.findById(nextNewsId)).thenReturn(Optional.of(nextNews));

        reconstructionService.reconstruct(List.of(failedNewsId, nextNewsId));

        verify(newsRepository, times(2)).findById(failedNewsId);
        verify(newsRepository).findById(nextNewsId);
    }
}
