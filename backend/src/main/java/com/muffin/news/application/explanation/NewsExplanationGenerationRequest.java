package com.muffin.news.application.explanation;

import java.util.List;

/** 해설카드 생성을 위해 AI에 전달할 뉴스 본문과 후보 용어 목록. */
public record NewsExplanationGenerationRequest(
        Long newsId, String title, String summary, String rewrittenBody, List<NewsExplanationTermCandidate> terms) {

    public NewsExplanationGenerationRequest {
        terms = List.copyOf(terms);
    }
}
