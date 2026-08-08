package com.muffin.quiz.application.generation;

import com.muffin.news.application.explanation.NewsExplanationGeneratedEvent;
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
public class DailyQuizGenerationEventListener {

    private final DailyQuizGenerationService dailyQuizGenerationService;

    /** 뉴스 해설카드가 저장될 때마다 오늘 퀴즈 생성 조건을 확인하고, 조건이 충족되면 일일 퀴즈를 생성한다. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(NewsExplanationGeneratedEvent event) {
        try {
            dailyQuizGenerationService.generateToday();
        } catch (RuntimeException exception) {
            log.error("Daily quiz generation failed after news explanation: newsId={}", event.newsId(), exception);
        }
    }
}
