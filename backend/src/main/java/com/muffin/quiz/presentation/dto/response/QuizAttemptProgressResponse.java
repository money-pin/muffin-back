package com.muffin.quiz.presentation.dto.response;

public record QuizAttemptProgressResponse(
        int totalCount, int solvedCount, int correctCount, Integer nextQuestionOrder) {}
