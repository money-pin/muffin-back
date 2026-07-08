package com.muffin.quiz.domain.quizsession;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuizSessionTest {

    @Test
    @DisplayName("퀴즈 세션을 시작하면 PROGRESS 상태와 기본 카운트가 설정된다")
    void start_setsInitialValues() {
        QuizSession session = QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 3);

        assertEquals(QuizSessionStatus.PROGRESS, session.getStatus());
        assertEquals(0, session.getSolvedCount());
        assertEquals(0, session.getCorrectCount());
        assertEquals(0L, session.getRewardMoney());
    }
}
