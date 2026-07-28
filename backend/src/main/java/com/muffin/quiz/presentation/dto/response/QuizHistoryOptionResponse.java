package com.muffin.quiz.presentation.dto.response;

public record QuizHistoryOptionResponse(
        Long optionId, int optionOrder, String content, boolean isSelected, boolean isCorrect) {}
