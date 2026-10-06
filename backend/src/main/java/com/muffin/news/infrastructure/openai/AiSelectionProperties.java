package com.muffin.news.infrastructure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RSS 기사 선별에 사용하는 AI 설정을 제공한다.
 *
 * @param model 기사 선별에 사용할 AI 모델명
 * @param maxPerCategory 카테고리별 선별 방식에서 카테고리마다 고를 최대 기사 수. 총량이 카테고리 수에 비례해 늘어난다
 * @param maxTotal 통합 선별 방식에서 하루 전체에 고를 최대 기사 수. 카테고리 수와 무관하게 고정된다
 */
@ConfigurationProperties(prefix = "muffin.news.ai.ai-selection")
public record AiSelectionProperties(String model, int maxPerCategory, int maxTotal) {

    public AiSelectionProperties {
        maxPerCategory = maxPerCategory > 0 ? maxPerCategory : 5;
        maxTotal = maxTotal > 0 ? maxTotal : 15;
    }
}
