package com.muffin.quiz.presentation.dto.response;

public record QuizProgressResponse(int totalCount, int solvedCount, int correctCount, Integer nextQuestionOrder) {}
