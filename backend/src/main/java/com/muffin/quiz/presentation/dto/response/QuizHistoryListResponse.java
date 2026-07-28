package com.muffin.quiz.presentation.dto.response;

import java.util.List;

public record QuizHistoryListResponse(List<QuizHistorySummaryResponse> histories) {}
