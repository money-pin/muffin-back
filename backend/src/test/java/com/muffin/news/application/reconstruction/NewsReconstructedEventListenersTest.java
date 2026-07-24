package com.muffin.news.application.reconstruction;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muffin.news.application.explanation.NewsExplanationGenerationService;
import com.muffin.news.application.term.NewsTermMappingService;
import com.muffin.quiz.application.generation.DailyQuizGenerationEventListener;
import com.muffin.quiz.application.generation.DailyQuizGenerationService;
import org.junit.jupiter.api.Test;

class NewsReconstructedEventListenersTest {

    private final NewsTermMappingService newsTermMappingService = mock(NewsTermMappingService.class);
    private final NewsExplanationGenerationService newsExplanationGenerationService =
            mock(NewsExplanationGenerationService.class);
    private final DailyQuizGenerationService dailyQuizGenerationService = mock(DailyQuizGenerationService.class);

    private final NewsTermMappingEventListener termMappingListener =
            new NewsTermMappingEventListener(newsTermMappingService);
    private final NewsExplanationGenerationEventListener explanationGenerationListener =
            new NewsExplanationGenerationEventListener(newsExplanationGenerationService);
    private final DailyQuizGenerationEventListener quizGenerationListener =
            new DailyQuizGenerationEventListener(dailyQuizGenerationService);

    @Test
    void handle_mapsTerms() {
        Long newsId = 1L;

        termMappingListener.handle(new NewsReconstructedEvent(newsId));

        verify(newsTermMappingService).mapTerms(newsId);
    }

    @Test
    void handle_generatesExplanationCards() {
        Long newsId = 1L;

        explanationGenerationListener.handle(new NewsReconstructedEvent(newsId));

        verify(newsExplanationGenerationService).generate(newsId);
    }

    @Test
    void handle_generatesDailyQuiz() {
        Long newsId = 1L;

        quizGenerationListener.handle(new NewsReconstructedEvent(newsId));

        verify(dailyQuizGenerationService).generateToday();
    }

    @Test
    void handle_doesNotThrowWhenTermMappingFails() {
        Long newsId = 1L;
        doThrow(new IllegalStateException("mapping failed"))
                .when(newsTermMappingService)
                .mapTerms(newsId);

        termMappingListener.handle(new NewsReconstructedEvent(newsId));

        verify(newsTermMappingService).mapTerms(newsId);
    }

    @Test
    void handle_doesNotThrowWhenExplanationGenerationFails() {
        Long newsId = 1L;
        doThrow(new IllegalStateException("explanation failed"))
                .when(newsExplanationGenerationService)
                .generate(newsId);

        explanationGenerationListener.handle(new NewsReconstructedEvent(newsId));

        verify(newsExplanationGenerationService).generate(newsId);
    }

    @Test
    void handle_doesNotThrowWhenQuizGenerationFails() {
        Long newsId = 1L;
        doThrow(new IllegalStateException("quiz failed"))
                .when(dailyQuizGenerationService)
                .generateToday();

        quizGenerationListener.handle(new NewsReconstructedEvent(newsId));

        verify(dailyQuizGenerationService).generateToday();
    }
}
