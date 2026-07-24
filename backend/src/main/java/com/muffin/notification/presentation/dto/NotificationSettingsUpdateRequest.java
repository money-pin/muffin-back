package com.muffin.notification.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationSettingsUpdateRequest(
        @NotNull Boolean newsUpdate,
        @NotNull Boolean dailyQuiz,
        @NotNull Boolean investResult,
        @NotNull Boolean rankingChange) {}
