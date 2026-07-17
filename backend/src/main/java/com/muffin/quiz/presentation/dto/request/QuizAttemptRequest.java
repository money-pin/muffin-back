package com.muffin.quiz.presentation.dto.request;

import jakarta.validation.constraints.NotNull;

public record QuizAttemptRequest(@NotNull(message = "optionId는 필수입니다.") Long optionId) {}
