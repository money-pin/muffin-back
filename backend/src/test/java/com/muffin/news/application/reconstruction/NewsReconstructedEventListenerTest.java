package com.muffin.news.application.reconstruction;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.muffin.news.application.explanation.NewsExplanationGenerationService;
import com.muffin.news.application.term.NewsTermMappingService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class NewsReconstructedEventListenerTest {

    private final NewsTermMappingService newsTermMappingService = mock(NewsTermMappingService.class);
    private final NewsExplanationGenerationService newsExplanationGenerationService =
            mock(NewsExplanationGenerationService.class);
    private final NewsReconstructedEventListener listener =
            new NewsReconstructedEventListener(newsTermMappingService, newsExplanationGenerationService);

    @Test
    void handle_mapsTermsThenGeneratesExplanationCards() {
        Long newsId = 1L;

        listener.handle(new NewsReconstructedEvent(newsId));

        InOrder inOrder = inOrder(newsTermMappingService, newsExplanationGenerationService);
        inOrder.verify(newsTermMappingService).mapTerms(newsId);
        inOrder.verify(newsExplanationGenerationService).generate(newsId);
    }

    @Test
    void handle_continuesExplanationGenerationWhenTermMappingFails() {
        Long newsId = 1L;
        doThrow(new IllegalStateException("mapping failed"))
                .when(newsTermMappingService)
                .mapTerms(newsId);

        listener.handle(new NewsReconstructedEvent(newsId));

        verify(newsTermMappingService).mapTerms(newsId);
        verify(newsExplanationGenerationService).generate(newsId);
    }
}
