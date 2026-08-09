package com.muffin.quiz.presentation.dto;

import java.util.List;

public record QuizHistoryListResponse(List<QuizHistorySummaryResponse> histories) {}
