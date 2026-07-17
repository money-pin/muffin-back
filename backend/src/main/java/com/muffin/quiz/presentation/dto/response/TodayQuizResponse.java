package com.muffin.quiz.presentation.dto.response;

import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import java.time.LocalDate;
import java.util.List;

public record TodayQuizResponse(
        Long dailyQuizSetId,
        LocalDate quizDate,
        QuizSetStatus quizSetStatus,
        QuizSessionStatus sessionStatus,
        String nickname,
        QuizProgressResponse progress,
        List<QuizQuestionResponse> questions) {}
