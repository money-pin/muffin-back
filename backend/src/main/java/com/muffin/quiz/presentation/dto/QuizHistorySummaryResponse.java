package com.muffin.quiz.presentation.dto;

import java.time.LocalDate;

public record QuizHistorySummaryResponse(LocalDate quizDate, int totalCount, int correctCount, int incorrectCount) {}
