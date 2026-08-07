package com.muffin.quiz.application.generation;

import java.util.List;

/** 일일 퀴즈 생성을 위해 AI에 전달할 재구성 완료 뉴스와 해설카드 정보. */
public record DailyQuizNewsSource(
        Long newsId, String title, String rewrittenBody, List<DailyQuizExplanationCardSource> explanationCards) {

    public DailyQuizNewsSource {
        explanationCards = List.copyOf(explanationCards);
    }
}
