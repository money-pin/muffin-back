package com.muffin.quiz.application;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.quiz.domain.quizsession.QuizSession;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import com.muffin.quiz.domain.quizset.Quiz;
import com.muffin.quiz.domain.quizset.QuizOption;
import com.muffin.quiz.domain.quizset.QuizSet;
import com.muffin.quiz.domain.quizset.QuizSetRepository;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import com.muffin.quiz.exception.QuizErrorCode;
import com.muffin.quiz.presentation.dto.QuizHistoryDetailResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryDetailSummaryResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryListResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryOptionResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryQuestionResponse;
import com.muffin.quiz.presentation.dto.QuizHistorySummaryResponse;
import com.muffin.quiz.presentation.dto.QuizOptionResponse;
import com.muffin.quiz.presentation.dto.QuizProgressResponse;
import com.muffin.quiz.presentation.dto.QuizQuestionResponse;
import com.muffin.quiz.presentation.dto.QuizResultProgressResponse;
import com.muffin.quiz.presentation.dto.QuizResultResponse;
import com.muffin.quiz.presentation.dto.QuizRewardResponse;
import com.muffin.quiz.presentation.dto.TodayQuizResponse;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.exception.UserException;
import com.muffin.user.domain.exception.code.UserErrorCode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DAILY_QUIZ_COUNT = 3;

    private final QuizSetRepository quizSetRepository;
    private final UserRepository userRepository;
    private final QuizSessionRepository quizSessionRepository;

    @Transactional(readOnly = true)
    public TodayQuizResponse getTodayQuiz(Long userId) {
        LocalDate today = LocalDate.now(KST);
        User user = getValidatedUser(userId);
        String nickname = user.getNickname();

        // KST 기준 오늘 날짜의 퀴즈 세트를 조회한다.
        Optional<QuizSet> quizSetOptional = quizSetRepository.findByQuizDate(today);

        if (quizSetOptional.isEmpty()) {
            log.info("Today quiz set is unavailable. quizDate={} reason=not_found", today);
            return unavailableResponse(today, nickname);
        }

        QuizSet quizSet = quizSetOptional.get();

        // 퀴즈 세트가 공개 상태가 아니면 사용자에게는 이용 불가 상태로 내려준다.
        if (quizSet.getStatus() != QuizSetStatus.PUBLISHED) {
            log.info(
                    "Today quiz set is unavailable. quizDate={} quizSetId={} status={}",
                    today,
                    quizSet.getId(),
                    quizSet.getStatus());
            return unavailableResponse(today, nickname);
        }

        Optional<QuizSession> sessionOptional =
                quizSessionRepository.findByUserIdAndDailyQuizSetId(userId, quizSet.getId());

        // 세션이 없으면 아직 오늘 퀴즈를 시작하지 않은 상태다.
        if (sessionOptional.isEmpty()) {
            return notStartedResponse(today, nickname, quizSet);
        }

        QuizSession session = sessionOptional.get();
        return sessionResponse(today, nickname, quizSet, session);
    }

    @Transactional(readOnly = true)
    public QuizResultResponse getTodayQuizResult(Long userId) {
        LocalDate today = LocalDate.now(KST);
        getValidatedUser(userId);

        // 오늘 공개된 퀴즈 세트가 없으면 결과도 조회할 수 없다.
        QuizSet quizSet = quizSetRepository
                .findByQuizDate(today)
                .filter(todayQuizSet -> todayQuizSet.getStatus() == QuizSetStatus.PUBLISHED)
                .orElseThrow(() -> new GeneralException(QuizErrorCode.QUIZ_UNAVAILABLE));

        // 결과 조회는 사용자의 당일 퀴즈 세션을 기준으로 한다.
        QuizSession session = quizSessionRepository
                .findByUserIdAndDailyQuizSetId(userId, quizSet.getId())
                .orElseThrow(() -> new GeneralException(QuizErrorCode.QUIZ_RESULT_NOT_READY));

        // 3문항을 모두 완료한 세션만 결과 화면에 진입할 수 있다.
        if (session.getStatus() != QuizSessionStatus.FINISHED) {
            throw new GeneralException(QuizErrorCode.QUIZ_RESULT_NOT_READY);
        }

        return toQuizResultResponse(session);
    }

    @Transactional(readOnly = true)
    public QuizHistoryListResponse getQuizHistories(Long userId) {
        getValidatedUser(userId);

        // 복습 목록은 완료된 퀴즈 세션만 날짜순으로 보여준다.
        List<QuizHistorySummaryResponse> histories =
                quizSessionRepository
                        .findAllByUserIdAndStatusOrderByDateDesc(userId, QuizSessionStatus.FINISHED)
                        .stream()
                        .map(this::toQuizHistorySummaryResponse)
                        .toList();

        return new QuizHistoryListResponse(histories);
    }

    @Transactional(readOnly = true)
    public QuizHistoryDetailResponse getQuizHistoryDetail(Long userId, String date) {
        getValidatedUser(userId);
        LocalDate quizDate = parseHistoryDate(date);

        if (quizDate.isAfter(LocalDate.now(KST))) {
            throw new GeneralException(QuizErrorCode.QUIZ_HISTORY_FUTURE_DATE);
        }

        Optional<QuizSession> sessionOptional =
                quizSessionRepository.findByUserIdAndDateAndStatus(userId, quizDate, QuizSessionStatus.FINISHED);

        if (sessionOptional.isEmpty()) {
            return emptyQuizHistoryDetailResponse(quizDate);
        }

        QuizSession session = sessionOptional.get();
        QuizSet quizSet = quizSetRepository
                .findById(session.getDailyQuizSetId())
                .orElseThrow(() -> new GeneralException(QuizErrorCode.QUIZ_UNAVAILABLE));

        return toQuizHistoryDetailResponse(quizDate, session, quizSet);
    }

    /** 인증된 사용자 ID로 회원을 조회하고 퀴즈 이용에 필요한 온보딩 완료 여부를 검증한다. */
    private User getValidatedUser(Long userId) {
        User user =
                userRepository.findById(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED));

        if (!user.isOnboardingCompleted()) {
            throw new UserException(UserErrorCode.ONBOARDING_NOT_COMPLETED);
        }

        return user;
    }

    /** 오늘의 퀴즈가 없거나 공개 전인 경우 빈 상태 UI를 그릴 수 있는 응답을 만든다. */
    private TodayQuizResponse unavailableResponse(LocalDate today, String nickname) {
        return new TodayQuizResponse(
                null,
                today,
                QuizSetStatus.UNAVAILABLE,
                null,
                nickname,
                new QuizProgressResponse(DAILY_QUIZ_COUNT, 0, 0, null),
                List.of());
    }

    /** 공개된 퀴즈는 있지만 사용자의 풀이 세션이 아직 없는 경우의 응답을 만든다. */
    private TodayQuizResponse notStartedResponse(LocalDate today, String nickname, QuizSet quizSet) {
        int totalCount = quizSet.getQuizzes().size();

        return new TodayQuizResponse(
                quizSet.getId(),
                today,
                quizSet.getStatus(),
                QuizSessionStatus.NOT_STARTED,
                nickname,
                new QuizProgressResponse(totalCount, 0, 0, 1),
                toQuestionResponses(quizSet));
    }

    /** QuizSet 루트가 가진 문제 목록을 API 응답 DTO로 변환한다. */
    private List<QuizQuestionResponse> toQuestionResponses(QuizSet quizSet) {
        return quizSet.getQuizzes().stream()
                .sorted(Comparator.comparingInt(Quiz::getQuizOrder))
                .map(quiz -> new QuizQuestionResponse(
                        quiz.getId(), quiz.getQuizOrder(), quiz.getQuestion(), toOptionResponses(quiz)))
                .toList();
    }

    /** 정답 여부는 노출하지 않고 선택지 식별자, 순서, 내용만 응답으로 변환한다. */
    private List<QuizOptionResponse> toOptionResponses(Quiz quiz) {
        return quiz.getOptions().stream()
                .sorted(Comparator.comparingInt(QuizOption::getOptionNo))
                .map(option -> new QuizOptionResponse(option.getId(), option.getOptionNo(), option.getContent()))
                .toList();
    }

    /** 기존 세션의 진행 상태를 기준으로 이어 풀기 또는 완료 화면 응답을 만든다. */
    private TodayQuizResponse sessionResponse(LocalDate today, String nickname, QuizSet quizSet, QuizSession session) {
        Integer nextQuestionOrder =
                session.getSolvedCount() >= session.getTotalCount() ? null : session.getSolvedCount() + 1;

        QuizProgressResponse progress = new QuizProgressResponse(
                session.getTotalCount(), session.getSolvedCount(), session.getCorrectCount(), nextQuestionOrder);

        List<QuizQuestionResponse> questions =
                session.getStatus() == QuizSessionStatus.FINISHED ? List.of() : toQuestionResponses(quizSet);

        return new TodayQuizResponse(
                quizSet.getId(), today, quizSet.getStatus(), session.getStatus(), nickname, progress, questions);
    }

    /** 완료된 퀴즈 세션에 저장된 결과와 보상 정보를 결과 조회 응답 DTO로 변환한다. */
    private QuizResultResponse toQuizResultResponse(QuizSession session) {
        int incorrectCount = session.getTotalCount() - session.getCorrectCount();

        return new QuizResultResponse(
                session.getId(),
                session.getDate(),
                session.getStatus(),
                new QuizResultProgressResponse(session.getTotalCount(), session.getCorrectCount(), incorrectCount),
                new QuizRewardResponse(session.getRewardMoney(), session.isRewardClaimed()));
    }

    /** 복습 목록 카드에 필요한 날짜별 정답 수 요약으로 변환한다. */
    private QuizHistorySummaryResponse toQuizHistorySummaryResponse(QuizSession session) {
        int incorrectCount = session.getTotalCount() - session.getCorrectCount();
        return new QuizHistorySummaryResponse(
                session.getDate(), session.getTotalCount(), session.getCorrectCount(), incorrectCount);
    }

    private LocalDate parseHistoryDate(String date) {
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new GeneralException(QuizErrorCode.QUIZ_HISTORY_INVALID_DATE_FORMAT);
        }
    }

    private QuizHistoryDetailResponse emptyQuizHistoryDetailResponse(LocalDate quizDate) {
        return new QuizHistoryDetailResponse(quizDate, new QuizHistoryDetailSummaryResponse(0, 0, 0), List.of());
    }

    /** 복습 상세 화면에 필요한 세션 결과와 문제/선택지 정보를 조합한다. */
    private QuizHistoryDetailResponse toQuizHistoryDetailResponse(
            LocalDate quizDate, QuizSession session, QuizSet quizSet) {
        Map<Long, com.muffin.quiz.domain.quizsession.QuizAttempt> attemptByQuizId = session.getAttempts().stream()
                .collect(Collectors.toMap(
                        com.muffin.quiz.domain.quizsession.QuizAttempt::getQuizId, Function.identity()));

        List<QuizHistoryQuestionResponse> questions = quizSet.getQuizzes().stream()
                .sorted(Comparator.comparingInt(Quiz::getQuizOrder))
                .filter(quiz -> attemptByQuizId.containsKey(quiz.getId()))
                .map(quiz -> toQuizHistoryQuestionResponse(quiz, attemptByQuizId.get(quiz.getId())))
                .toList();

        int incorrectCount = session.getTotalCount() - session.getCorrectCount();
        QuizHistoryDetailSummaryResponse summary = new QuizHistoryDetailSummaryResponse(
                session.getTotalCount(), session.getCorrectCount(), incorrectCount);

        return new QuizHistoryDetailResponse(quizDate, summary, questions);
    }

    private QuizHistoryQuestionResponse toQuizHistoryQuestionResponse(
            Quiz quiz, com.muffin.quiz.domain.quizsession.QuizAttempt attempt) {
        QuizOption correctOption =
                quiz.findCorrectOption().orElseThrow(() -> new GeneralException(QuizErrorCode.QUIZ_OPTION_NOT_FOUND));

        return new QuizHistoryQuestionResponse(
                quiz.getId(),
                quiz.getQuizOrder(),
                quiz.getQuestion(),
                attempt.isCorrect(),
                attempt.getOptionId(),
                correctOption.getId(),
                toHistoryOptionResponses(quiz, attempt),
                quiz.getExplanation());
    }

    private List<QuizHistoryOptionResponse> toHistoryOptionResponses(
            Quiz quiz, com.muffin.quiz.domain.quizsession.QuizAttempt attempt) {
        return quiz.getOptions().stream()
                .sorted(Comparator.comparingInt(QuizOption::getOptionNo))
                .map(option -> new QuizHistoryOptionResponse(
                        option.getId(),
                        option.getOptionNo(),
                        option.getContent(),
                        option.getId().equals(attempt.getOptionId()),
                        option.isCorrect()))
                .toList();
    }
}
