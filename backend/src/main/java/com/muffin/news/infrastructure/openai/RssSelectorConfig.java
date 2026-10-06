package com.muffin.news.infrastructure.openai;

import com.muffin.news.application.rss.PerCategoryRssSelectionStrategy;
import com.muffin.news.application.rss.RssArticleSelector;
import com.muffin.news.application.rss.RssFeedProperties;
import com.muffin.news.application.rss.RssSelectionMode;
import com.muffin.news.application.rss.RssSelectionStrategy;
import com.muffin.news.application.rss.UnifiedRssArticleSelector;
import com.muffin.news.application.rss.UnifiedRssSelectionStrategy;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RssSelectorConfig {

    /** AI 선별이 비활성화되면 빈 결과를 반환하여 RSS 후보가 news 테이블에 저장되지 않게 한다. */
    @Bean
    @ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "false", matchIfMissing = true)
    RssArticleSelector noSelectionRssArticleSelector() {
        return (category, candidates) -> List.of();
    }

    /** 위와 같은 이유로 통합 선별도 비활성화 시 빈 결과를 반환한다. */
    @Bean
    @ConditionalOnProperty(name = "muffin.news.ai.enabled", havingValue = "false", matchIfMissing = true)
    UnifiedRssArticleSelector noSelectionUnifiedRssArticleSelector() {
        return candidates -> List.of();
    }

    /**
     * 설정에 따라 선별 방식을 고른다. 두 방식의 구현은 모두 남겨 두고 활성 전략만 빈으로 등록한다.
     *
     * <p>통합 선별은 AI 호출이 하루 한 번뿐이라 실패 시 수집이 통째로 비므로, 카테고리별 선별을 폴백으로 끼워 준다.
     */
    @Bean
    RssSelectionStrategy rssSelectionStrategy(
            RssArticleSelector articleSelector,
            UnifiedRssArticleSelector unifiedSelector,
            RssFeedProperties properties) {
        RssSelectionStrategy perCategory = new PerCategoryRssSelectionStrategy(articleSelector);
        if (properties.selectionMode() == RssSelectionMode.PER_CATEGORY) {
            log.info("RSS selection mode: PER_CATEGORY");
            return perCategory;
        }
        log.info("RSS selection mode: UNIFIED");
        return new UnifiedRssSelectionStrategy(unifiedSelector, perCategory);
    }
}
