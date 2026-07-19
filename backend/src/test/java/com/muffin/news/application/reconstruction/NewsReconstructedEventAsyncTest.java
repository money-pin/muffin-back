package com.muffin.news.application.reconstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.muffin.news.application.explanation.NewsExplanationGenerationService;
import com.muffin.news.application.term.NewsTermMappingService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"muffin.news.ai.enabled=true", "muffin.news.ai.api-key=test-key"})
@ActiveProfiles("test")
class NewsReconstructedEventAsyncTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @MockitoBean
    private NewsTermMappingService newsTermMappingService;

    @MockitoBean
    private NewsExplanationGenerationService newsExplanationGenerationService;

    @Test
    void newsReconstructedEvent_runsListenerOnAsyncThread() throws InterruptedException {
        Long newsId = 1L;
        String publisherThreadName = Thread.currentThread().getName();
        AtomicReference<String> listenerThreadName = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        doAnswer(invocation -> {
                    listenerThreadName.set(Thread.currentThread().getName());
                    latch.countDown();
                    return null;
                })
                .when(newsExplanationGenerationService)
                .generate(newsId);

        eventPublisher.publishEvent(new NewsReconstructedEvent(newsId));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(listenerThreadName.get()).isNotEqualTo(publisherThreadName);
        verify(newsTermMappingService).mapTerms(newsId);
        verify(newsExplanationGenerationService).generate(newsId);
    }
}
