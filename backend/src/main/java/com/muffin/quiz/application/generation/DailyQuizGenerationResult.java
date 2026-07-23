package com.muffin.quiz.application.generation;

import java.util.List;

/** AI 일일 퀴즈 생성 결과. */
public record DailyQuizGenerationResult(List<DailyQuizQuestionResult> questions) {

    public DailyQuizGenerationResult {
        questions = List.copyOf(questions);
    }
}
