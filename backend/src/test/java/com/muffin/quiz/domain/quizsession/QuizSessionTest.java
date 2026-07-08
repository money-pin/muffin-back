package com.muffin.quiz.domain.quizsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    @DisplayName("제출 기록을 추가하면 풀이 수와 정답 수와 보상 합계가 갱신된다")
    void recordAttempt_updatesProgressAndReward() {
        QuizSession session = QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 3);

        session.recordAttempt(100L, 1001L, true, 100L, LocalDateTime.of(2026, 5, 7, 9, 10));
        session.recordAttempt(200L, 2001L, false, 100L, LocalDateTime.of(2026, 5, 7, 9, 11));

        assertEquals(2, session.getSolvedCount());
        assertEquals(1, session.getCorrectCount());
        assertEquals(100L, session.getRewardMoney());
        assertEquals(2, session.getAttempts().size());
    }

    @Test
    @DisplayName("제출 기록 목록은 외부에서 직접 수정할 수 없다")
    void getAttempts_returnsUnmodifiableView() {
        QuizSession session = QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 3);

        List<QuizAttempt> attempts = session.getAttempts();

        assertThrows(UnsupportedOperationException.class, () -> attempts.add(null));
    }
}
