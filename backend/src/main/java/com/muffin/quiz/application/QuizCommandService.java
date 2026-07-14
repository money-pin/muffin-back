package com.muffin.quiz.application;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.quiz.domain.quizsession.QuizAttempt;
import com.muffin.quiz.domain.quizsession.QuizSession;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.quiz.domain.quizset.Quiz;
import com.muffin.quiz.domain.quizset.QuizOption;
import com.muffin.quiz.domain.quizset.QuizSet;
import com.muffin.quiz.domain.quizset.QuizSetRepository;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import com.muffin.quiz.exception.QuizErrorCode;
import com.muffin.quiz.presentation.dto.request.QuizAttemptRequest;
import com.muffin.quiz.presentation.dto.response.QuizAttemptProgressResponse;
import com.muffin.quiz.presentation.dto.response.QuizAttemptResponse;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizCommandService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final QuizSetRepository quizSetRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final UserRepository userRepository;

    @Transactional
    public QuizAttemptResponse submitAnswer(Long userId, Long quizId, QuizAttemptRequest request) {

        // 임시 userId 기반 인증 단계. 추후 Security 적용 시 인증 객체에서 사용자 식별자를 가져오도록 교체한다.
        User user =
                userRepository.findById(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED));

        // 온보딩을 마친 사용자만 퀴즈를 제출할 수 있다.
        if (!user.isOnboardingCompleted()) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }

        LocalDate today = LocalDate.now(KST);

        // 제출은 오늘 공개(PUBLISHED)된 퀴즈 세트에 대해서만 허용한다.
        QuizSet quizSet = quizSetRepository
                .findByQuizDate(today)
                .filter(todayQuizSet -> todayQuizSet.getStatus() == QuizSetStatus.PUBLISHED)
                .orElseThrow(() -> new GeneralException(QuizErrorCode.QUIZ_UNAVAILABLE));

        // 요청 path의 quizId가 오늘 퀴즈 세트에 포함된 문항인지 확인한다.
        Quiz quiz = quizSet.findQuiz(quizId).orElseThrow(() -> new GeneralException(QuizErrorCode.QUIZ_NOT_FOUND));

        // 요청 body의 optionId가 해당 문항에 속한 선택지인지 확인한다.
        QuizOption selectedOption = quiz.findOption(request.optionId())
                .orElseThrow(() -> new GeneralException(QuizErrorCode.QUIZ_OPTION_NOT_FOUND));

        // 오답 피드백에서도 정답 선택지를 강조해야 하므로 정답 선택지를 함께 찾는다.
        QuizOption correctOption = quiz.findCorrectOption()
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR));

        // 조회 API에서는 세션을 만들지 않고, 첫 답안 제출 시점에 풀이 세션을 시작한다.
        QuizSession session = quizSessionRepository
                .findByUserIdAndDailyQuizSetId(userId, quizSet.getId())
                .orElseGet(() -> QuizSession.start(
                        userId, quizSet.getId(), today, quizSet.getQuizzes().size()));

        Optional<QuizAttempt> attemptOptional = session.findAttemptByQuizId(quizId);

        // 이미 제출한 문항이면 새 답안을 저장하지 않고 기존 제출 결과를 그대로 반환한다.
        if (attemptOptional.isPresent()) {
            QuizAttempt attempt = attemptOptional.get();
            log.info(
                    "Duplicate quiz attempt ignored. userId={} quizSetId={} quizId={} attemptId={}",
                    userId,
                    quizSet.getId(),
                    quizId,
                    attempt.getId());
            return toResponse(session, quiz, attempt, correctOption);
        }

        boolean correct = selectedOption.isCorrect();
        LocalDateTime submittedAt = LocalDateTime.now();

        QuizAttempt attempt = session.recordAttempt(
                quiz.getId(), selectedOption.getId(), correct, quiz.getRewardMoney(), submittedAt);

        QuizSession savedSession = quizSessionRepository.saveAndFlush(session);
        if (savedSession.getSolvedCount() >= savedSession.getTotalCount()) {
            log.info(
                    "Quiz session finished. userId={} quizSetId={} sessionId={} correctCount={}",
                    userId,
                    quizSet.getId(),
                    savedSession.getId(),
                    savedSession.getCorrectCount());
        }

        return toResponse(savedSession, quiz, attempt, correctOption);
    }

    private QuizAttemptResponse toResponse(
            QuizSession session, Quiz quiz, QuizAttempt attempt, QuizOption correctOption) {
        // QuizAttempt에는 사용자가 고른 선택지와 정오답만 저장하고, 해설과 정답 선택지는 Quiz에서 꺼내 응답을 만든다.
        return new QuizAttemptResponse(
                attempt.getId(),
                quiz.getId(),
                attempt.getOptionId(),
                correctOption.getId(),
                attempt.isCorrect(),
                quiz.getExplanation(),
                session.getStatus(),
                session.getSolvedCount() >= session.getTotalCount(),
                toProgressResponse(session),
                attempt.getSubmittedAt().atZone(KST).toOffsetDateTime());
    }

    /** 세션의 현재 풀이 수를 기준으로 제출 응답용 진행률을 만든다. */
    private QuizAttemptProgressResponse toProgressResponse(QuizSession session) {
        Integer nextQuestionOrder =
                session.getSolvedCount() >= session.getTotalCount() ? null : session.getSolvedCount() + 1;

        return new QuizAttemptProgressResponse(
                session.getTotalCount(), session.getSolvedCount(), session.getCorrectCount(), nextQuestionOrder);
    }
}
