package com.muffin.quiz.presentation.dto;

import java.util.List;

public record QuizHistoryQuestionResponse(
        Long quizId,
        int questionOrder,
        String question,
        boolean isCorrect,
        Long selectedOptionId,
        Long correctOptionId,
        List<QuizHistoryOptionResponse> options,
        String explanation) {}
