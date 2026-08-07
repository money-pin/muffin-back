package com.muffin.quiz.application.generation;

import com.muffin.quiz.domain.quizset.enums.QuizDifficulty;
import java.util.List;

/** AI가 생성한 퀴즈 문항 결과. */
public record DailyQuizQuestionResult(
        int order,
        Long newsId,
        String questionText,
        List<DailyQuizOptionResult> options,
        int correctOptionOrder,
        String explanation,
        String sourceSentence,
        String questionTopic,
        QuizDifficulty difficulty) {

    public DailyQuizQuestionResult {
        options = List.copyOf(options);
    }

    public DailyQuizQuestionResult(
            int order,
            Long newsId,
            String questionText,
            List<DailyQuizOptionResult> options,
            int correctOptionOrder,
            String explanation,
            String sourceSentence,
            QuizDifficulty difficulty) {
        this(order, newsId, questionText, options, correctOptionOrder, explanation, sourceSentence, "", difficulty);
    }
}
