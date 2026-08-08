package com.muffin.quiz.presentation.dto;

public record QuizProgressResponse(int totalCount, int solvedCount, int correctCount, Integer nextQuestionOrder) {}
