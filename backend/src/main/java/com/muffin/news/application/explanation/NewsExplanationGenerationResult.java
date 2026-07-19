package com.muffin.news.application.explanation;

import java.util.List;

/** AI 해설카드 생성 결과. */
public record NewsExplanationGenerationResult(List<NewsExplanationCardResult> cards) {

    public NewsExplanationGenerationResult {
        cards = List.copyOf(cards);
    }
}
