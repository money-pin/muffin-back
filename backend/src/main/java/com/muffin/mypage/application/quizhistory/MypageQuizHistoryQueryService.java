package com.muffin.mypage.application.quizhistory;

import com.muffin.mypage.domain.QuizHistoryPeriod;
import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import com.muffin.quiz.domain.quizsession.QuizSession;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.user.domain.UserRepository;
import java.time.DateTimeException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지 월별 퀴즈 참여 내역 조회 유스케이스. 도메인/저장소 결과를 조합만 하는 오케스트레이션 서비스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MypageQuizHistoryQueryService {

    private final UserRepository userRepository;
    private final QuizSessionRepository quizSessionRepository;

    public List<QuizSession> getQuizHistory(Long userId, int year, int month) {
        if (!userRepository.existsById(userId)) {
            throw new MypageException(MypageErrorCode.USER_NOT_FOUND, null);
        }

        QuizHistoryPeriod.Range range = resolveRange(year, month);

        return quizSessionRepository.findAllByUserIdAndDateBetweenOrderByDateDesc(userId, range.start(), range.end());
    }

    private QuizHistoryPeriod.Range resolveRange(int year, int month) {
        try {
            return QuizHistoryPeriod.resolve(year, month);
        } catch (DateTimeException e) {
            throw new MypageException(MypageErrorCode.INVALID_YEAR_MONTH, null);
        }
    }
}
