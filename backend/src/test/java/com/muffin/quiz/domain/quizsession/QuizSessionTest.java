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
    @DisplayName("모든 문제를 푼 뒤에는 제출 기록을 추가할 수 없다")
    void recordAttempt_throwsWhenSolvedCountReachesTotalCount() {
        QuizSession session = QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 1);
        session.recordAttempt(100L, 1001L, false, null, LocalDateTime.of(2026, 5, 7, 9, 10));

        assertThrows(
                IllegalStateException.class,
                () -> session.recordAttempt(200L, 2001L, false, null, LocalDateTime.of(2026, 5, 7, 9, 11)));
    }

    @Test
    @DisplayName("정답 제출 시 보상 금액이 없으면 예외가 발생한다")
    void recordAttempt_throwsWhenCorrectRewardMoneyIsNull() {
        QuizSession session = QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 3);

        assertThrows(
                IllegalArgumentException.class,
                () -> session.recordAttempt(100L, 1001L, true, null, LocalDateTime.of(2026, 5, 7, 9, 10)));
    }

    @Test
    @DisplayName("같은 문제에 대한 제출 기록은 중복으로 추가할 수 없다")
    void recordAttempt_throwsWhenQuizAlreadyAttempted() {
        QuizSession session = QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 3);
        session.recordAttempt(100L, 1001L, false, null, LocalDateTime.of(2026, 5, 7, 9, 10));

        assertThrows(
                IllegalStateException.class,
                () -> session.recordAttempt(100L, 1002L, true, 100L, LocalDateTime.of(2026, 5, 7, 9, 11)));
    }

    @Test
    @DisplayName("완료된 세션의 보상은 한 번만 확정할 수 있다")
    void claimReward_returnsRewardOnlyOnce() {
        QuizSession session = QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 2);
        session.recordAttempt(100L, 1001L, true, 100L, LocalDateTime.of(2026, 5, 7, 9, 10));
        session.recordAttempt(200L, 2001L, true, 100L, LocalDateTime.of(2026, 5, 7, 9, 11));

        assertEquals(200L, session.claimReward());
        assertEquals(0L, session.claimReward());
    }

    @Test
    @DisplayName("정답 수가 1문항 이하이면 보상은 0원으로 확정된다")
    void claimReward_setsZeroWhenCorrectCountIsOneOrLess() {
        QuizSession oneCorrectSession = QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 1);
        oneCorrectSession.recordAttempt(100L, 1001L, true, 100L, LocalDateTime.of(2026, 5, 7, 9, 10));

        Long oneCorrectReward = oneCorrectSession.claimReward();

        assertEquals(0L, oneCorrectReward);
        assertEquals(0L, oneCorrectSession.getRewardMoney());

        QuizSession zeroCorrectSession = QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 1);
        zeroCorrectSession.recordAttempt(100L, 1001L, false, null, LocalDateTime.of(2026, 5, 7, 9, 10));

        Long zeroCorrectReward = zeroCorrectSession.claimReward();

        assertEquals(0L, zeroCorrectReward);
        assertEquals(0L, zeroCorrectSession.getRewardMoney());
    }

    @Test
    @DisplayName("완료되지 않은 세션은 보상을 확정할 수 없다")
    void claimReward_throwsWhenSessionIsNotFinished() {
        QuizSession session = QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 3);

        assertThrows(IllegalStateException.class, session::claimReward);
    }

    @Test
    @DisplayName("제출 기록 목록은 외부에서 직접 수정할 수 없다")
    void getAttempts_returnsUnmodifiableView() {
        QuizSession session = QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 3);

        List<QuizAttempt> attempts = session.getAttempts();

        assertThrows(UnsupportedOperationException.class, () -> attempts.add(null));
    }
}
