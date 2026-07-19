package com.muffin.news.application.reconstruction;

import com.muffin.news.application.explanation.NewsExplanationGenerationService;
import com.muffin.news.application.term.NewsTermMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class NewsReconstructedEventListener {

    private final NewsTermMappingService newsTermMappingService;
    private final NewsExplanationGenerationService newsExplanationGenerationService;

    /** 재구성 저장이 끝난 뒤 용어 매핑과 해설카드 생성을 후속 작업으로 실행한다. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(NewsReconstructedEvent event) {
        // 후속 작업 실패가 뉴스 재구성 트랜잭션을 되돌리지 않도록 각 작업을 독립적으로 보호한다.
        mapTerms(event.newsId());
        generateExplanationCards(event.newsId());
    }

    private void mapTerms(Long newsId) {
        try {
            newsTermMappingService.mapTerms(newsId);
        } catch (RuntimeException exception) {
            log.error("News term mapping failed after reconstruction: newsId={}", newsId, exception);
        }
    }

    private void generateExplanationCards(Long newsId) {
        try {
            newsExplanationGenerationService.generate(newsId);
        } catch (RuntimeException exception) {
            log.error("News explanation generation failed after reconstruction: newsId={}", newsId, exception);
        }
    }
}
