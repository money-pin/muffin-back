package com.muffin.quiz.presentation.dto;

import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import java.time.OffsetDateTime;

public record QuizAttemptResponse(
        Long attemptId,
        Long quizId,
        Long selectedOptionId,
        Long correctOptionId,
        boolean isCorrect,
        String explanation,
        QuizSessionStatus sessionStatus,
        boolean isLastQuestion,
        QuizAttemptProgressResponse progress,
        OffsetDateTime submittedAt) {}
