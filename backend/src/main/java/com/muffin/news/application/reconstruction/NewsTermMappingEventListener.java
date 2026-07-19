package com.muffin.news.application.reconstruction;

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
public class NewsTermMappingEventListener {

    private final NewsTermMappingService newsTermMappingService;

    /** 재구성 저장이 끝난 뉴스 본문에서 용어 사전 매칭을 독립 후속 작업으로 실행한다. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(NewsReconstructedEvent event) {
        try {
            newsTermMappingService.mapTerms(event.newsId());
        } catch (RuntimeException exception) {
            log.error("News term mapping failed after reconstruction: newsId={}", event.newsId(), exception);
        }
    }
}
