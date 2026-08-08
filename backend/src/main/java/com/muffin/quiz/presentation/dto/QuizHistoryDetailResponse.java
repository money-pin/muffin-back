package com.muffin.quiz.presentation.dto;

import java.time.LocalDate;
import java.util.List;

public record QuizHistoryDetailResponse(
        LocalDate quizDate, QuizHistoryDetailSummaryResponse summary, List<QuizHistoryQuestionResponse> questions) {}
