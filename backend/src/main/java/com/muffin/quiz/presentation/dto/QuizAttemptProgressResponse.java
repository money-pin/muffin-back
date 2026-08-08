package com.muffin.quiz.presentation.dto;

public record QuizAttemptProgressResponse(
        int totalCount, int solvedCount, int correctCount, Integer nextQuestionOrder) {}
