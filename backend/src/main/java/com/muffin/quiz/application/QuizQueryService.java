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
import com.muffin.quiz.presentation.dto.response.QuizOptionResponse;
import com.muffin.quiz.presentation.dto.response.QuizProgressResponse;
import com.muffin.quiz.presentation.dto.response.QuizQuestionResponse;
import com.muffin.quiz.presentation.dto.response.QuizResultProgressResponse;
import com.muffin.quiz.presentation.dto.response.QuizResultResponse;
import com.muffin.quiz.presentation.dto.response.QuizRewardResponse;
import com.muffin.quiz.presentation.dto.response.TodayQuizResponse;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

        User user =
                userRepository.findById(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED));

        // 온보딩을 완료한 사용자만 오늘의 퀴즈를 조회할 수 있다.
        if (!user.isOnboardingCompleted()) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }

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

        // TODO: 임시 userId 기반 인증 단계. 추후 Security 적용 시 인증 객체에서 사용자 식별자를 가져오도록 교체한다.
        User user =
                userRepository.findById(userId).orElseThrow(() -> new GeneralException(GeneralErrorCode.UNAUTHORIZED));

        // 온보딩을 완료한 사용자만 퀴즈 결과를 조회할 수 있다.
        if (!user.isOnboardingCompleted()) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }

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
        Integer currentQuestionOrder =
                session.getSolvedCount() >= session.getTotalCount() ? null : session.getSolvedCount() + 1;

        QuizProgressResponse progress = new QuizProgressResponse(
                session.getTotalCount(), session.getSolvedCount(), session.getCorrectCount(), currentQuestionOrder);

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
}
