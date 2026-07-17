package com.muffin.quiz.presentation.dto.response;

import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import java.time.LocalDate;

public record QuizResultResponse(
        Long quizSessionId,
        LocalDate quizDate,
        QuizSessionStatus sessionStatus,
        QuizResultProgressResponse progress,
        QuizRewardResponse reward) {}
