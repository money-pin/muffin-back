package com.muffin.quiz.presentation.dto;

public record QuizHistoryOptionResponse(
        Long optionId, int optionOrder, String content, boolean isSelected, boolean isCorrect) {}
