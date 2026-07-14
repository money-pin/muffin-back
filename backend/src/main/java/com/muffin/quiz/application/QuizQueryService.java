package com.muffin.quiz.application;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.quiz.domain.quizsession.QuizSession;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import com.muffin.quiz.domain.quizset.Quiz;
import com.muffin.quiz.domain.quizset.QuizSet;
import com.muffin.quiz.domain.quizset.QuizSetRepository;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import com.muffin.quiz.presentation.dto.response.QuizOptionResponse;
import com.muffin.quiz.presentation.dto.response.QuizProgressResponse;
import com.muffin.quiz.presentation.dto.response.QuizQuestionResponse;
import com.muffin.quiz.presentation.dto.response.TodayQuizResponse;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DAILY_QUIZ_COUNT = 3;

    private final QuizSetRepository quizSetRepository;
    private final UserRepository userRepository;
    private final QuizSessionRepository quizSessionRepository;

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
        return new TodayQuizResponse(
                quizSet.getId(),
                today,
                quizSet.getStatus(),
                QuizSessionStatus.NOT_STARTED,
                nickname,
                new QuizProgressResponse(DAILY_QUIZ_COUNT, 0, 0, 1),
                toQuestionResponses(quizSet));
    }

    /** QuizSet 루트가 가진 문제 목록을 API 응답 DTO로 변환한다. */
    private List<QuizQuestionResponse> toQuestionResponses(QuizSet quizSet) {
        return quizSet.getQuizzes().stream()
                .map(quiz -> new QuizQuestionResponse(
                        quiz.getId(), quiz.getQuizOrder(), quiz.getQuestion(), toOptionResponses(quiz)))
                .toList();
    }

    /** 정답 여부는 노출하지 않고 선택지 식별자, 순서, 내용만 응답으로 변환한다. */
    private List<QuizOptionResponse> toOptionResponses(Quiz quiz) {
        return quiz.getOptions().stream()
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
}
