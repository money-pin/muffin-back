package com.muffin.mypage.presentation.quizhistory.dto;

import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import java.time.LocalDate;
import java.util.List;

public record MypageQuizHistoryResponse(int year, int month, List<QuizSessionSummary> quizSessions) {

    public record QuizSessionSummary(
            LocalDate date,
            Long sessionId,
            QuizSessionStatus status,
            int correctCount,
            int totalCount,
            Long rewardMoney,
            boolean rewardClaimed) {}
}
