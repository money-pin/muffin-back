package com.muffin.news.application.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.domain.explanation.NewsExplanationRepository;
import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.term.TermDictionaryRepository;
import com.muffin.quiz.application.generation.DailyQuizGenerationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"muffin.news.ai.enabled=true", "muffin.news.ai.api-key=test-key"})
@ActiveProfiles("test")
class NewsExplanationGeneratedEventIntegrationTest {

    @Autowired
    private NewsExplanationGenerationService newsExplanationGenerationService;

    @MockitoBean
    private NewsRepository newsRepository;

    @MockitoBean
    private TermDictionaryRepository termDictionaryRepository;

    @MockitoBean
    private NewsExplanationRepository newsExplanationRepository;

    @MockitoBean
    private NewsExplanationGenerator newsExplanationGenerator;

    @MockitoBean
    private DailyQuizGenerationService dailyQuizGenerationService;

    @Test
    void generate_publishesEventAndTriggersDailyQuizGeneration() throws InterruptedException {
        Long newsId = 1L;
        News news = reconstructedNews(newsId);
        CountDownLatch latch = new CountDownLatch(1);

        when(newsExplanationRepository.existsByNewsIdAndStatus(newsId, NewsExplanationStatus.DONE))
                .thenReturn(false);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(termDictionaryRepository.findAllById(List.of())).thenReturn(List.of());
        when(newsExplanationGenerator.generate(new NewsExplanationGenerationRequest(
                        newsId, news.getTitle(), news.getSummary(), news.getContent(), List.of())))
                .thenReturn(new NewsExplanationGenerationResult(
                        List.of(new NewsExplanationCardResult(1, "기준금리란?", "기준금리는 중앙은행이 정하는 대표 금리입니다.", "기준금리"))));
        doAnswer(invocation -> {
                    latch.countDown();
                    return null;
                })
                .when(dailyQuizGenerationService)
                .generateToday();

        newsExplanationGenerationService.generate(newsId);

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(newsExplanationRepository).saveAll(any());
        verify(dailyQuizGenerationService).generateToday();
    }

    private static News reconstructedNews(Long newsId) {
        News news =
                News.processing(1L, "금리 뉴스", "매일경제", LocalDateTime.of(2026, 7, 18, 9, 0), null, "https://example.com");
        ReflectionTestUtils.setField(news, "id", newsId);
        news.completeReconstruction("금리 뉴스 요약", "기준금리가 바뀌면서 대출과 예금 환경에도 변화가 생겼습니다.");
        return news;
    }
}
