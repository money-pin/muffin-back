package com.muffin.quiz.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.quiz.domain.quizsession.QuizSession;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import com.muffin.quiz.domain.quizset.Quiz;
import com.muffin.quiz.domain.quizset.QuizOption;
import com.muffin.quiz.domain.quizset.QuizSet;
import com.muffin.quiz.domain.quizset.QuizSetRepository;
import com.muffin.quiz.domain.quizset.enums.QuizDifficulty;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import com.muffin.quiz.exception.QuizErrorCode;
import com.muffin.quiz.presentation.dto.QuizHistoryDetailResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryListResponse;
import com.muffin.quiz.presentation.dto.QuizResultResponse;
import com.muffin.quiz.presentation.dto.TodayQuizResponse;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QuizQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;
    private static final Long QUIZ_SET_ID = 10L;

    private QuizSetRepository quizSetRepository;
    private UserRepository userRepository;
    private QuizSessionRepository quizSessionRepository;
    private QuizQueryService quizQueryService;

    @BeforeEach
    void setUp() {
        quizSetRepository = mock(QuizSetRepository.class);
        userRepository = mock(UserRepository.class);
        quizSessionRepository = mock(QuizSessionRepository.class);
        quizQueryService = new QuizQueryService(quizSetRepository, userRepository, quizSessionRepository);
    }

    @Test
    @DisplayName("오늘 퀴즈 세트가 없으면 UNAVAILABLE 응답을 반환한다")
    void getTodayQuiz_returnsUnavailableWhenQuizSetDoesNotExist() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.empty());

        TodayQuizResponse response = quizQueryService.getTodayQuiz(USER_ID);

        assertNull(response.dailyQuizSetId());
        assertEquals(today, response.quizDate());
        assertEquals(QuizSetStatus.UNAVAILABLE, response.quizSetStatus());
        assertNull(response.sessionStatus());
        assertEquals("세현", response.nickname());
        assertEquals(3, response.progress().totalCount());
        assertEquals(0, response.progress().solvedCount());
        assertEquals(0, response.progress().correctCount());
        assertNull(response.progress().nextQuestionOrder());
        assertTrue(response.questions().isEmpty());
    }

    @Test
    @DisplayName("공개된 퀴즈 세트가 있고 세션이 없으면 NOT_STARTED 응답과 문제 목록을 반환한다")
    void getTodayQuiz_returnsNotStartedWhenSessionDoesNotExist() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.empty());

        TodayQuizResponse response = quizQueryService.getTodayQuiz(USER_ID);

        assertEquals(QUIZ_SET_ID, response.dailyQuizSetId());
        assertEquals(QuizSetStatus.PUBLISHED, response.quizSetStatus());
        assertEquals(QuizSessionStatus.NOT_STARTED, response.sessionStatus());
        assertEquals(3, response.progress().totalCount());
        assertEquals(0, response.progress().solvedCount());
        assertEquals(0, response.progress().correctCount());
        assertEquals(1, response.progress().nextQuestionOrder());
        assertEquals(3, response.questions().size());
        assertEquals(101L, response.questions().getFirst().quizId());
        assertEquals(1, response.questions().getFirst().quizOrder());
        assertEquals("질문 1", response.questions().getFirst().question());
        assertEquals(3, response.questions().getFirst().options().size());
        assertEquals(1011L, response.questions().getFirst().options().getFirst().optionId());
        assertEquals(1, response.questions().getFirst().options().getFirst().optionOrder());
        assertEquals(
                "선택지 1-1", response.questions().getFirst().options().getFirst().content());
    }

    @Test
    @DisplayName("풀이 중인 세션이 있으면 진행률과 다음 문제 순서를 반환한다")
    void getTodayQuiz_returnsProgressWhenSessionIsInProgress() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);
        QuizSession session = QuizSession.start(USER_ID, QUIZ_SET_ID, today, 3);
        session.recordAttempt(101L, 1011L, true, 100000L, LocalDateTime.now());

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.of(session));

        TodayQuizResponse response = quizQueryService.getTodayQuiz(USER_ID);

        assertEquals(QuizSessionStatus.PROGRESS, response.sessionStatus());
        assertEquals(3, response.progress().totalCount());
        assertEquals(1, response.progress().solvedCount());
        assertEquals(1, response.progress().correctCount());
        assertEquals(2, response.progress().nextQuestionOrder());
        assertEquals(3, response.questions().size());
    }

    @Test
    @DisplayName("완료된 세션이면 FINISHED 진행률과 빈 문제 목록을 반환한다")
    void getTodayQuiz_returnsFinishedWithoutQuestionsWhenSessionIsFinished() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);
        QuizSession session = QuizSession.start(USER_ID, QUIZ_SET_ID, today, 3);
        ReflectionTestUtils.setField(session, "status", QuizSessionStatus.FINISHED);
        ReflectionTestUtils.setField(session, "solvedCount", 3);
        ReflectionTestUtils.setField(session, "correctCount", 2);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.of(session));

        TodayQuizResponse response = quizQueryService.getTodayQuiz(USER_ID);

        assertEquals(QuizSessionStatus.FINISHED, response.sessionStatus());
        assertEquals(3, response.progress().totalCount());
        assertEquals(3, response.progress().solvedCount());
        assertEquals(2, response.progress().correctCount());
        assertNull(response.progress().nextQuestionOrder());
        assertTrue(response.questions().isEmpty());
    }

    @Test
    @DisplayName("온보딩을 완료하지 않은 사용자는 퀴즈를 조회할 수 없다")
    void getTodayQuiz_throwsForbiddenWhenUserDidNotCompleteOnboarding() {
        User user = registeredUser("세현");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        GeneralException exception = assertThrows(GeneralException.class, () -> quizQueryService.getTodayQuiz(USER_ID));

        assertEquals(GeneralErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    @DisplayName("완료된 오늘 퀴즈 세션의 결과와 지급된 보상 정보를 조회한다")
    void getTodayQuizResult_returnsFinishedResult() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);
        QuizSession session = finishedSession(today);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.of(session));

        QuizResultResponse response = quizQueryService.getTodayQuizResult(USER_ID);

        assertEquals(501L, response.quizSessionId());
        assertEquals(today, response.quizDate());
        assertEquals(QuizSessionStatus.FINISHED, response.sessionStatus());
        assertEquals(3, response.progress().totalCount());
        assertEquals(2, response.progress().correctCount());
        assertEquals(1, response.progress().incorrectCount());
        assertEquals(200000L, response.reward().amount());
        assertTrue(response.reward().claimed());
    }

    @Test
    @DisplayName("오늘 퀴즈 세션이 아직 완료되지 않았으면 결과를 조회할 수 없다")
    void getTodayQuizResult_throwsConflictWhenSessionIsNotFinished() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);
        QuizSession session = QuizSession.start(USER_ID, QUIZ_SET_ID, today, 3);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.of(session));

        GeneralException exception =
                assertThrows(GeneralException.class, () -> quizQueryService.getTodayQuizResult(USER_ID));

        assertEquals(QuizErrorCode.QUIZ_RESULT_NOT_READY, exception.getErrorCode());
    }

    @Test
    @DisplayName("오늘 공개된 퀴즈 세트가 없으면 결과를 조회할 수 없다")
    void getTodayQuizResult_throwsNotFoundWhenQuizSetIsUnavailable() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.empty());

        GeneralException exception =
                assertThrows(GeneralException.class, () -> quizQueryService.getTodayQuizResult(USER_ID));

        assertEquals(QuizErrorCode.QUIZ_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    @DisplayName("완료된 지난 퀴즈 세션을 날짜별 정답 수 요약으로 조회한다")
    void getQuizHistories_returnsFinishedSessionSummaries() {
        User user = onboardedUser("세현");
        QuizSession firstSession = finishedSession(LocalDate.of(2026, 5, 8));
        QuizSession secondSession = finishedSession(LocalDate.of(2026, 5, 7));

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSessionRepository.findAllByUserIdAndStatusOrderByDateDesc(USER_ID, QuizSessionStatus.FINISHED))
                .thenReturn(List.of(firstSession, secondSession));

        QuizHistoryListResponse response = quizQueryService.getQuizHistories(USER_ID);

        assertEquals(2, response.histories().size());
        assertEquals(LocalDate.of(2026, 5, 8), response.histories().getFirst().quizDate());
        assertEquals(3, response.histories().getFirst().totalCount());
        assertEquals(2, response.histories().getFirst().correctCount());
        assertEquals(1, response.histories().getFirst().incorrectCount());
        assertEquals(LocalDate.of(2026, 5, 7), response.histories().get(1).quizDate());
    }

    @Test
    @DisplayName("완료된 지난 퀴즈 세션이 없으면 빈 복습 목록을 반환한다")
    void getQuizHistories_returnsEmptyListWhenFinishedSessionDoesNotExist() {
        User user = onboardedUser("세현");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSessionRepository.findAllByUserIdAndStatusOrderByDateDesc(USER_ID, QuizSessionStatus.FINISHED))
                .thenReturn(List.of());

        QuizHistoryListResponse response = quizQueryService.getQuizHistories(USER_ID);

        assertTrue(response.histories().isEmpty());
    }

    @Test
    @DisplayName("특정 날짜의 지난 퀴즈 복습 상세를 조회한다")
    void getQuizHistoryDetail_returnsFinishedSessionQuestions() {
        LocalDate quizDate = LocalDate.of(2026, 5, 8);
        User user = onboardedUser("세현");
        QuizSession session = finishedSession(quizDate);
        QuizSet quizSet = publishedQuizSet(quizDate);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSessionRepository.findByUserIdAndDateAndStatus(USER_ID, quizDate, QuizSessionStatus.FINISHED))
                .thenReturn(Optional.of(session));
        when(quizSetRepository.findById(QUIZ_SET_ID)).thenReturn(Optional.of(quizSet));

        QuizHistoryDetailResponse response = quizQueryService.getQuizHistoryDetail(USER_ID, "2026-05-08");

        assertEquals(quizDate, response.quizDate());
        assertEquals(3, response.summary().totalCount());
        assertEquals(2, response.summary().correctCount());
        assertEquals(1, response.summary().incorrectCount());
        assertEquals(3, response.questions().size());
        assertEquals(101L, response.questions().getFirst().quizId());
        assertEquals(1, response.questions().getFirst().questionOrder());
        assertTrue(response.questions().getFirst().isCorrect());
        assertEquals(1011L, response.questions().getFirst().selectedOptionId());
        assertEquals(1011L, response.questions().getFirst().correctOptionId());
        assertEquals(3, response.questions().getFirst().options().size());
        assertTrue(response.questions().getFirst().options().getFirst().isSelected());
        assertTrue(response.questions().getFirst().options().getFirst().isCorrect());
        assertEquals("해설 1", response.questions().getFirst().explanation());
        assertEquals(103L, response.questions().get(2).quizId());
        assertEquals(1033L, response.questions().get(2).selectedOptionId());
        assertEquals(1031L, response.questions().get(2).correctOptionId());
    }

    @Test
    @DisplayName("특정 날짜의 완료된 퀴즈 세션이 없으면 빈 복습 상세를 반환한다")
    void getQuizHistoryDetail_returnsEmptyQuestionsWhenFinishedSessionDoesNotExist() {
        LocalDate quizDate = LocalDate.of(2026, 5, 8);
        User user = onboardedUser("세현");

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSessionRepository.findByUserIdAndDateAndStatus(USER_ID, quizDate, QuizSessionStatus.FINISHED))
                .thenReturn(Optional.empty());

        QuizHistoryDetailResponse response = quizQueryService.getQuizHistoryDetail(USER_ID, "2026-05-08");

        assertEquals(quizDate, response.quizDate());
        assertEquals(0, response.summary().totalCount());
        assertEquals(0, response.summary().correctCount());
        assertEquals(0, response.summary().incorrectCount());
        assertTrue(response.questions().isEmpty());
    }

    @Test
    @DisplayName("미래 날짜의 지난 퀴즈 복습 상세는 조회할 수 없다")
    void getQuizHistoryDetail_throwsBadRequestWhenDateIsFuture() {
        User user = onboardedUser("세현");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        GeneralException exception = assertThrows(
                GeneralException.class, () -> quizQueryService.getQuizHistoryDetail(USER_ID, "2099-01-01"));

        assertEquals(QuizErrorCode.QUIZ_HISTORY_FUTURE_DATE, exception.getErrorCode());
    }

    @Test
    @DisplayName("날짜 형식이 올바르지 않으면 지난 퀴즈 복습 상세를 조회할 수 없다")
    void getQuizHistoryDetail_throwsBadRequestWhenDateFormatIsInvalid() {
        User user = onboardedUser("세현");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        GeneralException exception = assertThrows(
                GeneralException.class, () -> quizQueryService.getQuizHistoryDetail(USER_ID, "2026/05/08"));

        assertEquals(QuizErrorCode.QUIZ_HISTORY_INVALID_DATE_FORMAT, exception.getErrorCode());
    }

    private User onboardedUser(String nickname) {
        User user = registeredUser(nickname);
        user.completeOnboarding(1, 2, 3);
        return user;
    }

    private User registeredUser(String nickname) {
        return User.register(1L, UUID.randomUUID().toString(), "세현", nickname);
    }

    private QuizSet publishedQuizSet(LocalDate quizDate) {
        QuizSet quizSet = QuizSet.create(quizDate);
        ReflectionTestUtils.setField(quizSet, "id", QUIZ_SET_ID);
        ReflectionTestUtils.setField(quizSet, "status", QuizSetStatus.PUBLISHED);

        for (int quizOrder = 1; quizOrder <= 3; quizOrder++) {
            Quiz quiz = quizSet.addQuiz(
                    (long) quizOrder,
                    "질문 " + quizOrder,
                    "해설 " + quizOrder,
                    100000L,
                    quizOrder,
                    "근거 문장 " + quizOrder,
                    QuizDifficulty.EASY);
            ReflectionTestUtils.setField(quiz, "id", 100L + quizOrder);

            for (int optionNo = 1; optionNo <= 3; optionNo++) {
                QuizOption option = quiz.addOption(optionNo, "선택지 " + quizOrder + "-" + optionNo, optionNo == 1);
                ReflectionTestUtils.setField(option, "id", 1000L + quizOrder * 10L + optionNo);
            }
        }

        return quizSet;
    }

    private QuizSession finishedSession(LocalDate today) {
        QuizSession session = QuizSession.start(USER_ID, QUIZ_SET_ID, today, 3);
        ReflectionTestUtils.setField(session, "id", 501L);
        session.recordAttempt(101L, 1011L, true, 100000L, LocalDateTime.now());
        session.recordAttempt(102L, 1022L, true, 100000L, LocalDateTime.now());
        session.recordAttempt(103L, 1033L, false, null, LocalDateTime.now());
        session.claimReward();
        return session;
    }
}
