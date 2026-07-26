package com.muffin.quiz.presentation.dto.response;

import java.time.LocalDate;

public record QuizHistorySummaryResponse(LocalDate quizDate, int totalCount, int correctCount, int incorrectCount) {}
