package com.muffin.quiz.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import com.muffin.quiz.domain.quizsession.QuizAttempt;
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
import com.muffin.quiz.presentation.dto.request.QuizAttemptRequest;
import com.muffin.quiz.presentation.dto.response.QuizAttemptResponse;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class QuizCommandServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 1L;
    private static final Long QUIZ_SET_ID = 10L;

    private QuizSetRepository quizSetRepository;
    private QuizSessionRepository quizSessionRepository;
    private UserRepository userRepository;
    private UserAssetRepository userAssetRepository;
    private QuizCommandService quizCommandService;

    @BeforeEach
    void setUp() {
        quizSetRepository = mock(QuizSetRepository.class);
        quizSessionRepository = mock(QuizSessionRepository.class);
        userRepository = mock(UserRepository.class);
        userAssetRepository = mock(UserAssetRepository.class);
        quizCommandService =
                new QuizCommandService(quizSetRepository, quizSessionRepository, userRepository, userAssetRepository);
    }

    @Test
    @DisplayName("정답을 제출하면 제출 기록을 저장하고 정답 피드백을 반환한다")
    void submitAnswer_recordsCorrectAttempt() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.empty());
        when(quizSessionRepository.saveAndFlush(any(QuizSession.class))).thenAnswer(invocation -> {
            QuizSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 501L);
            ReflectionTestUtils.setField(session.getAttempts().getFirst(), "id", 9001L);
            return session;
        });

        QuizAttemptResponse response = quizCommandService.submitAnswer(USER_ID, 101L, new QuizAttemptRequest(1011L));

        assertEquals(9001L, response.attemptId());
        assertEquals(101L, response.quizId());
        assertEquals(1011L, response.selectedOptionId());
        assertEquals(1011L, response.correctOptionId());
        assertTrue(response.isCorrect());
        assertEquals("해설 1", response.explanation());
        assertEquals(QuizSessionStatus.PROGRESS, response.sessionStatus());
        assertFalse(response.isLastQuestion());
        assertEquals(3, response.progress().totalCount());
        assertEquals(1, response.progress().solvedCount());
        assertEquals(1, response.progress().correctCount());
        assertEquals(2, response.progress().nextQuestionOrder());
        assertEquals(
                KST.getRules().getOffset(response.submittedAt().toInstant()),
                response.submittedAt().getOffset());
        verifyNoInteractions(userAssetRepository);
    }

    @Test
    @DisplayName("오답을 제출하면 정답 선택지와 오답 피드백을 반환한다")
    void submitAnswer_recordsIncorrectAttempt() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.empty());
        when(quizSessionRepository.saveAndFlush(any(QuizSession.class))).thenAnswer(invocation -> {
            QuizSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "id", 502L);
            ReflectionTestUtils.setField(session.getAttempts().getFirst(), "id", 9002L);
            return session;
        });

        QuizAttemptResponse response = quizCommandService.submitAnswer(USER_ID, 101L, new QuizAttemptRequest(1012L));

        assertEquals(1012L, response.selectedOptionId());
        assertEquals(1011L, response.correctOptionId());
        assertFalse(response.isCorrect());
        assertEquals(1, response.progress().solvedCount());
        assertEquals(0, response.progress().correctCount());
        assertEquals(2, response.progress().nextQuestionOrder());
        verifyNoInteractions(userAssetRepository);
    }

    @Test
    @DisplayName("이미 제출한 문항을 다시 제출하면 기존 제출 결과를 반환하고 새로 저장하지 않는다")
    void submitAnswer_returnsExistingAttemptWhenAlreadySubmitted() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);
        QuizSession session = QuizSession.start(USER_ID, QUIZ_SET_ID, today, 3);
        QuizAttempt attempt = session.recordAttempt(101L, 1011L, true, 100000L, LocalDateTime.now());
        ReflectionTestUtils.setField(session, "id", 503L);
        ReflectionTestUtils.setField(attempt, "id", 9003L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.of(session));

        QuizAttemptResponse response = quizCommandService.submitAnswer(USER_ID, 101L, new QuizAttemptRequest(1012L));

        assertEquals(9003L, response.attemptId());
        assertEquals(1011L, response.selectedOptionId());
        assertTrue(response.isCorrect());
        assertEquals(1, response.progress().solvedCount());
        verify(quizSessionRepository, never()).saveAndFlush(any(QuizSession.class));
        verifyNoInteractions(userAssetRepository);
    }

    @Test
    @DisplayName("완료됐지만 보상이 미확정인 세션을 재제출하면 기존 답안을 반환하며 보상을 확정한다")
    void submitAnswer_claimsRewardForFinishedUnclaimedSessionOnDuplicateSubmit() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);
        UserAsset userAsset = UserAsset.create(USER_ID, 1_000_000L);
        QuizSession session = QuizSession.start(USER_ID, QUIZ_SET_ID, today, 3);
        session.recordAttempt(101L, 1011L, true, 100000L, LocalDateTime.now());
        session.recordAttempt(102L, 1022L, false, 100000L, LocalDateTime.now());
        QuizAttempt attempt = session.recordAttempt(103L, 1031L, true, 100000L, LocalDateTime.now());
        ReflectionTestUtils.setField(session, "id", 505L);
        ReflectionTestUtils.setField(attempt, "id", 9005L);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.of(session));
        when(userAssetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(userAsset));
        when(quizSessionRepository.saveAndFlush(any(QuizSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuizAttemptResponse response = quizCommandService.submitAnswer(USER_ID, 103L, new QuizAttemptRequest(1032L));

        assertEquals(9005L, response.attemptId());
        assertEquals(1031L, response.selectedOptionId());
        assertEquals(QuizSessionStatus.FINISHED, response.sessionStatus());
        assertTrue(session.isRewardClaimed());
        assertEquals(1_200_000L, userAsset.getTotalAsset());
        verify(userAssetRepository).findByUserId(USER_ID);
        verify(quizSessionRepository).saveAndFlush(session);
    }

    @Test
    @DisplayName("마지막 문항을 제출하면 세션을 완료하고 보상을 자산에 반영한다")
    void submitAnswer_finishesSessionAndClaimsReward() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);
        UserAsset userAsset = UserAsset.create(USER_ID, 1_000_000L);
        QuizSession session = QuizSession.start(USER_ID, QUIZ_SET_ID, today, 3);
        session.recordAttempt(101L, 1011L, true, 100000L, LocalDateTime.now());
        session.recordAttempt(102L, 1022L, false, 100000L, LocalDateTime.now());

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.of(session));
        when(userAssetRepository.findByUserId(USER_ID)).thenReturn(Optional.of(userAsset));
        when(quizSessionRepository.saveAndFlush(any(QuizSession.class))).thenAnswer(invocation -> {
            QuizSession savedSession = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedSession, "id", 504L);
            ReflectionTestUtils.setField(savedSession.getAttempts().get(2), "id", 9004L);
            return savedSession;
        });

        QuizAttemptResponse response = quizCommandService.submitAnswer(USER_ID, 103L, new QuizAttemptRequest(1031L));

        assertEquals(QuizSessionStatus.FINISHED, response.sessionStatus());
        assertTrue(response.isLastQuestion());
        assertEquals(3, response.progress().solvedCount());
        assertEquals(2, response.progress().correctCount());
        assertNull(response.progress().nextQuestionOrder());
        assertTrue(session.isRewardClaimed());
        assertEquals(200_000L, session.getRewardMoney());
        assertEquals(1_200_000L, userAsset.getTotalAsset());
        verify(userAssetRepository).findByUserId(USER_ID);
    }

    @Test
    @DisplayName("보상 지급 대상인데 사용자 자산이 없으면 예외가 발생하고 세션을 저장하지 않는다")
    void submitAnswer_throwsWhenUserAssetMissingForReward() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);
        QuizSession session = QuizSession.start(USER_ID, QUIZ_SET_ID, today, 3);
        session.recordAttempt(101L, 1011L, true, 100000L, LocalDateTime.now());
        session.recordAttempt(102L, 1022L, false, 100000L, LocalDateTime.now());

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.of(session));
        when(userAssetRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(
                IllegalStateException.class,
                () -> quizCommandService.submitAnswer(USER_ID, 103L, new QuizAttemptRequest(1031L)));

        verify(userAssetRepository).findByUserId(USER_ID);
        verify(quizSessionRepository, never()).saveAndFlush(any(QuizSession.class));
    }

    @Test
    @DisplayName("마지막 제출 후 정답이 1문항 이하이면 보상을 0원으로 확정하고 자산은 조회하지 않는다")
    void submitAnswer_claimsZeroRewardWhenCorrectCountIsOneOrLess() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);
        QuizSession session = QuizSession.start(USER_ID, QUIZ_SET_ID, today, 3);
        session.recordAttempt(101L, 1012L, false, 100000L, LocalDateTime.now());
        session.recordAttempt(102L, 1022L, false, 100000L, LocalDateTime.now());

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));
        when(quizSessionRepository.findByUserIdAndDailyQuizSetId(USER_ID, QUIZ_SET_ID))
                .thenReturn(Optional.of(session));
        when(quizSessionRepository.saveAndFlush(any(QuizSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuizAttemptResponse response = quizCommandService.submitAnswer(USER_ID, 103L, new QuizAttemptRequest(1031L));

        assertEquals(QuizSessionStatus.FINISHED, response.sessionStatus());
        assertTrue(session.isRewardClaimed());
        assertEquals(0L, session.getRewardMoney());
        verifyNoInteractions(userAssetRepository);
    }

    @Test
    @DisplayName("해당 퀴즈 문항의 선택지가 아니면 예외가 발생한다")
    void submitAnswer_throwsWhenOptionDoesNotBelongToQuiz() {
        LocalDate today = LocalDate.now(KST);
        User user = onboardedUser("세현");
        QuizSet quizSet = publishedQuizSet(today);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(quizSetRepository.findByQuizDate(today)).thenReturn(Optional.of(quizSet));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> quizCommandService.submitAnswer(USER_ID, 101L, new QuizAttemptRequest(1021L)));

        assertEquals(QuizErrorCode.QUIZ_OPTION_NOT_FOUND, exception.getErrorCode());
    }

    private User onboardedUser(String nickname) {
        User user = User.register(1L, UUID.randomUUID().toString(), "세현", nickname);
        user.completeOnboarding(1, 2, 3);
        return user;
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
}
