package com.muffin.news.application.reconstruction;

import com.muffin.news.application.rss.NewsCollectedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "true")
public class NewsReconstructionEventListener {

    private final NewsReconstructionService reconstructionService;

    /** RSS 저장 완료 이벤트를 비동기로 받아 뉴스 재구성을 시작한다. */
    @Async
    @EventListener
    public void handle(NewsCollectedEvent event) {
        reconstructionService.reconstruct(event.newsIds());
    }
}
