package com.muffin.news.infrastructure.openai;

import com.muffin.news.application.rss.RssArticleSelector;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RssSelectorConfig {

    /** AI 선별이 비활성화되면 빈 결과를 반환하여 RSS 후보가 news 테이블에 저장되지 않게 한다. */
    @Bean
    @ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "false", matchIfMissing = true)
    RssArticleSelector noSelectionRssArticleSelector() {
        return (category, candidates) -> List.of();
    }
}
