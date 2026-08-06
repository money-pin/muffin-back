package com.muffin.mypage.application.quizhistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import com.muffin.quiz.domain.quizsession.QuizSession;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MypageQuizHistoryQueryServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private QuizSessionRepository quizSessionRepository;

    private MypageQuizHistoryQueryService mypageQuizHistoryQueryService;

    @BeforeEach
    void setUp() {
        mypageQuizHistoryQueryService = new MypageQuizHistoryQueryService(userRepository, quizSessionRepository);
    }

    private QuizSession finishedSession(Long id, LocalDate date, int totalCount, int correctCount, boolean claimed) {
        QuizSession session = QuizSession.start(USER_ID, 1L, date, totalCount);
        for (int i = 0; i < totalCount; i++) {
            boolean correct = i < correctCount;
            session.recordAttempt((long) (i + 1), (long) (i + 1), correct, correct ? 100L : null, date.atStartOfDay());
        }
        if (claimed) {
            session.claimReward();
        }
        ReflectionTestUtils.setField(session, "id", id);
        return session;
    }

    @Test
    @DisplayName("연/월 범위의 세션을 저장소가 준 순서(날짜 내림차순) 그대로 반환한다")
    void getQuizHistory_success() {
        QuizSession session1 = finishedSession(10L, LocalDate.of(2026, 7, 20), 3, 2, true);
        QuizSession session2 = finishedSession(11L, LocalDate.of(2026, 7, 5), 3, 1, false);

        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(quizSessionRepository.findAllByUserIdAndDateBetweenOrderByDateDesc(
                        USER_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(session1, session2));

        List<QuizSession> quizSessions = mypageQuizHistoryQueryService.getQuizHistory(USER_ID, 2026, 7);

        assertThat(quizSessions).hasSize(2);

        var first = quizSessions.get(0);
        assertThat(first.getDate()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(first.getId()).isEqualTo(10L);
        assertThat(first.getStatus()).isEqualTo(QuizSessionStatus.FINISHED);
        assertThat(first.getCorrectCount()).isEqualTo(2);
        assertThat(first.getTotalCount()).isEqualTo(3);
        assertThat(first.getRewardMoney()).isEqualTo(200L);
        assertThat(first.isRewardClaimed()).isTrue();

        var second = quizSessions.get(1);
        assertThat(second.getDate()).isEqualTo(LocalDate.of(2026, 7, 5));
        assertThat(second.isRewardClaimed()).isFalse();
    }

    @Test
    @DisplayName("해당 월에 세션이 없으면 빈 목록을 응답한다")
    void getQuizHistory_empty() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(quizSessionRepository.findAllByUserIdAndDateBetweenOrderByDateDesc(
                        USER_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of());

        List<QuizSession> quizSessions = mypageQuizHistoryQueryService.getQuizHistory(USER_ID, 2026, 7);

        assertThat(quizSessions).isEmpty();
    }

    @Test
    @DisplayName("month가 1~12 범위를 벗어나면 INVALID_YEAR_MONTH")
    void getQuizHistory_invalidMonth() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> mypageQuizHistoryQueryService.getQuizHistory(USER_ID, 2026, 13))
                .isInstanceOf(MypageException.class)
                .satisfies(ex -> assertThat(((MypageException) ex).getErrorCode())
                        .isEqualTo(MypageErrorCode.INVALID_YEAR_MONTH));
    }

    @Test
    @DisplayName("존재하지 않는 유저 → USER_NOT_FOUND")
    void getQuizHistory_userNotFound() {
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        assertThatThrownBy(() -> mypageQuizHistoryQueryService.getQuizHistory(USER_ID, 2026, 7))
                .isInstanceOf(MypageException.class)
                .satisfies(ex ->
                        assertThat(((MypageException) ex).getErrorCode()).isEqualTo(MypageErrorCode.USER_NOT_FOUND));
    }
}
