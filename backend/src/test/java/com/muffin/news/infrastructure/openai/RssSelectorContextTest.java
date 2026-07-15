package com.muffin.news.infrastructure.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.news.application.rss.RssArticleSelector;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"muffin.news.ai-selection.enabled=true", "muffin.news.ai-selection.api-key=test-key"})
@ActiveProfiles("test")
class RssSelectorContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    /** AI 선별 기능이 활성화되면 OpenAI 선별기만 빈으로 등록되고, no-op 선별기는 등록되지 않는다. */
    @Test
    void registersOnlyOpenAiSelectorWhenEnabled() {
        Map<String, RssArticleSelector> selectors = applicationContext.getBeansOfType(RssArticleSelector.class);

        assertThat(selectors).hasSize(1);
        assertThat(selectors.values().iterator().next()).isInstanceOf(OpenAiRssArticleSelector.class);
    }
}
