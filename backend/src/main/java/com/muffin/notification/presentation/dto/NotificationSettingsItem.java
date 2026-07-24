package com.muffin.notification.presentation.dto;

public record NotificationSettingsItem(
        boolean newsUpdate, boolean dailyQuiz, boolean investResult, boolean rankingChange) {}
