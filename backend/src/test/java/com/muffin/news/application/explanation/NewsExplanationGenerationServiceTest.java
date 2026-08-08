package com.muffin.news.application.explanation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.news.domain.explanation.NewsExplanation;
import com.muffin.news.domain.explanation.NewsExplanationRepository;
import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.term.TermDictionary;
import com.muffin.news.domain.term.TermDictionaryRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class NewsExplanationGenerationServiceTest {

    private final NewsRepository newsRepository = mock(NewsRepository.class);
    private final TermDictionaryRepository termDictionaryRepository = mock(TermDictionaryRepository.class);
    private final NewsExplanationRepository newsExplanationRepository = mock(NewsExplanationRepository.class);
    private final NewsExplanationGenerator newsExplanationGenerator = mock(NewsExplanationGenerator.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
    private final NewsExplanationGenerationService generationService = new NewsExplanationGenerationService(
            newsRepository,
            termDictionaryRepository,
            newsExplanationRepository,
            newsExplanationGenerator,
            transactionTemplate,
            eventPublisher);

    NewsExplanationGenerationServiceTest() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void generate_savesDoneCards() {
        Long newsId = 1L;
        News news = reconstructedNews(newsId);
        news.addTerm(10L);
        TermDictionary term = TermDictionary.create("기준금리", "중앙은행이 정하는 대표 금리");
        ReflectionTestUtils.setField(term, "id", 10L);
        when(newsExplanationRepository.existsByNewsIdAndStatus(newsId, NewsExplanationStatus.DONE))
                .thenReturn(false);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(termDictionaryRepository.findAllById(List.of(10L))).thenReturn(List.of(term));
        when(newsExplanationGenerator.generate(new NewsExplanationGenerationRequest(
                        newsId,
                        news.getTitle(),
                        news.getSummary(),
                        news.getContent(),
                        List.of(new NewsExplanationTermCandidate(10L, "기준금리")))))
                .thenReturn(new NewsExplanationGenerationResult(List.of(new NewsExplanationCardResult(
                        1, "기준금리란?", "기준금리는 중앙은행이 돈의 흐름을 조절하기 위해 정하는 대표 금리입니다.", "기준금리"))));

        generationService.generate(newsId);

        assertSavedExplanation(newsId, 1, "기준금리란?", "기준금리는 중앙은행이 돈의 흐름을 조절하기 위해 정하는 대표 금리입니다.", "기준금리");
        verify(eventPublisher).publishEvent(new NewsExplanationGeneratedEvent(newsId));
    }

    @Test
    void generate_generatesWhenMatchedTermsAreEmpty() {
        Long newsId = 1L;
        News news = reconstructedNews(newsId);
        when(newsExplanationRepository.existsByNewsIdAndStatus(newsId, NewsExplanationStatus.DONE))
                .thenReturn(false);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(newsExplanationGenerator.generate(new NewsExplanationGenerationRequest(
                        newsId, news.getTitle(), news.getSummary(), news.getContent(), List.of())))
                .thenReturn(new NewsExplanationGenerationResult(List.of(new NewsExplanationCardResult(
                        2,
                        "왜 금리 변화가 생활비와 연결될까?",
                        "금리가 바뀌면 대출과 예금의 부담이 함께 움직입니다. 장바구니 가격을 보고 소비를 조절하듯, 사람들은 이자 부담에 따라 지출과 저축을 조정합니다.",
                        "금리 변화"))));

        generationService.generate(newsId);

        assertSavedExplanation(
                newsId,
                1,
                "왜 금리 변화가 생활비와 연결될까?",
                "금리가 바뀌면 대출과 예금의 부담이 함께 움직입니다. 장바구니 가격을 보고 소비를 조절하듯, 사람들은 이자 부담에 따라 지출과 저축을 조정합니다.",
                "금리 변화");
        verify(eventPublisher).publishEvent(new NewsExplanationGeneratedEvent(newsId));
    }

    @Test
    void generate_doesNotThrowWhenGeneratorFails() {
        Long newsId = 1L;
        News news = reconstructedNews(newsId);
        news.addTerm(10L);
        TermDictionary term = TermDictionary.create("기준금리", "중앙은행이 정하는 대표 금리");
        ReflectionTestUtils.setField(term, "id", 10L);
        when(newsExplanationRepository.existsByNewsIdAndStatus(newsId, NewsExplanationStatus.DONE))
                .thenReturn(false);
        when(newsRepository.findById(newsId)).thenReturn(Optional.of(news));
        when(newsRepository.existsById(newsId)).thenReturn(true);
        when(termDictionaryRepository.findAllById(List.of(10L))).thenReturn(List.of(term));
        when(newsExplanationGenerator.generate(any())).thenThrow(new IllegalStateException("OpenAI failed"));

        generationService.generate(newsId);

        ArgumentCaptor<NewsExplanation> captor = ArgumentCaptor.forClass(NewsExplanation.class);
        verify(newsExplanationRepository).save(captor.capture());
        assertThat(captor.getValue()).satisfies(explanation -> {
            assertThat(explanation.getNewsId()).isEqualTo(newsId);
            assertThat(explanation.getCardOrder()).isZero();
            assertThat(explanation.getTitle()).isEqualTo("해설카드 생성 실패");
            assertThat(explanation.getContent()).isEqualTo("해설카드 생성에 실패했습니다.");
            assertThat(explanation.getKeyTerm()).isEqualTo("해설카드");
            assertThat(explanation.getStatus()).isEqualTo(NewsExplanationStatus.FAILED);
        });
        verify(eventPublisher, never()).publishEvent(any());
    }

    private static News reconstructedNews(Long newsId) {
        News news =
                News.processing(1L, "금리 뉴스", "매일경제", LocalDateTime.of(2026, 7, 18, 9, 0), null, "https://example.com");
        ReflectionTestUtils.setField(news, "id", newsId);
        news.completeReconstruction("금리 뉴스 요약", "기준금리가 바뀌면서 대출과 예금 환경에도 변화가 생겼습니다.");
        return news;
    }

    private void assertSavedExplanation(Long newsId, int cardOrder, String title, String content, String keyTerm) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<NewsExplanation>> captor = ArgumentCaptor.forClass(List.class);

        verify(newsExplanationRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(explanation -> {
            assertThat(explanation.getNewsId()).isEqualTo(newsId);
            assertThat(explanation.getCardOrder()).isEqualTo(cardOrder);
            assertThat(explanation.getTitle()).isEqualTo(title);
            assertThat(explanation.getContent()).isEqualTo(content);
            assertThat(explanation.getKeyTerm()).isEqualTo(keyTerm);
            assertThat(explanation.getStatus()).isEqualTo(NewsExplanationStatus.DONE);
        });
    }
}
