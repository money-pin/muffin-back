package com.muffin.mypage.presentation.quizhistory.dto;

import com.muffin.quiz.domain.quizsession.QuizSession;
import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import java.time.LocalDate;
import java.util.List;

public record MypageQuizHistoryResponse(int year, int month, List<QuizSessionSummary> quizSessions) {

    public static MypageQuizHistoryResponse from(int year, int month, List<QuizSession> quizSessions) {
        return new MypageQuizHistoryResponse(
                year, month, quizSessions.stream().map(QuizSessionSummary::from).toList());
    }

    public record QuizSessionSummary(
            LocalDate date,
            Long sessionId,
            QuizSessionStatus status,
            int correctCount,
            int totalCount,
            Long rewardMoney,
            boolean rewardClaimed) {

        public static QuizSessionSummary from(QuizSession session) {
            return new QuizSessionSummary(
                    session.getDate(),
                    session.getId(),
                    session.getStatus(),
                    session.getCorrectCount(),
                    session.getTotalCount(),
                    session.getRewardMoney(),
                    session.isRewardClaimed());
        }
    }
}
