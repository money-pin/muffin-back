package com.muffin.news.application.explanation;

/** 뉴스 재구성 결과를 바탕으로 경제 상식 해설카드를 생성한다. */
public interface NewsExplanationGenerator {

    NewsExplanationGenerationResult generate(NewsExplanationGenerationRequest request);
}
