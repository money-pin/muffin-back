package com.muffin.mypage.application.home;

import com.muffin.character.domain.characterprofile.CharacterProfile;
import com.muffin.character.domain.characterprofile.CharacterRepository;
import com.muffin.mypage.domain.StreakCalculator;
import com.muffin.mypage.presentation.home.dto.MypageHomeResponse;
import com.muffin.mypage.presentation.home.dto.MypageHomeResponse.CharacterSummary;
import com.muffin.mypage.presentation.home.dto.MypageHomeResponse.RecentNewsItem;
import com.muffin.mypage.presentation.home.dto.MypageHomeResponse.StreakSummary;
import com.muffin.mypage.presentation.home.dto.MypageHomeResponse.WeeklyActivityDay;
import com.muffin.mypage.presentation.home.dto.WeekDay;
import com.muffin.news.application.query.NewsQueryRepository;
import com.muffin.news.application.query.RecentReadNewsRow;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.exception.UserException;
import com.muffin.user.domain.exception.code.UserErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지 홈 조회 유스케이스. user/character/quiz/news 도메인의 데이터를 조합만 하는 오케스트레이션 서비스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MypageHomeQueryService {

    private static final int RECENT_NEWS_LIMIT = 3;

    private final UserRepository userRepository;
    private final CharacterRepository characterRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final NewsQueryRepository newsQueryRepository;
    private final Clock clock;

    public MypageHomeResponse getHome(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
        if (user.getCharacterId() == null) {
            throw new UserException(UserErrorCode.ONBOARDING_NOT_COMPLETED);
        }
        CharacterProfile character = characterRepository
                .findById(user.getCharacterId())
                .orElseThrow(() -> new UserException(UserErrorCode.CHARACTER_NOT_FOUND));

        Set<LocalDate> finishedDates =
                new HashSet<>(quizSessionRepository.findDatesByUserIdAndStatus(userId, QuizSessionStatus.FINISHED));
        StreakCalculator.Result streak = StreakCalculator.calculate(finishedDates, LocalDate.now(clock));

        List<RecentReadNewsRow> recentNewsRows = newsQueryRepository.findRecentReadNews(userId, RECENT_NEWS_LIMIT);

        return new MypageHomeResponse(
                user.getNickname(),
                new CharacterSummary(
                        character.getCharacterId(),
                        character.getMuffinType(),
                        character.getName(),
                        character.getImageUrl()),
                toStreakSummary(streak),
                recentNewsRows.stream()
                        .map(row -> new RecentNewsItem(
                                row.newsId(), row.title(), row.thumbnailUrl(), row.readAt(), row.isScrapped()))
                        .toList());
    }

    private StreakSummary toStreakSummary(StreakCalculator.Result streak) {
        List<WeeklyActivityDay> weeklyActivity = streak.weeklyActivity().stream()
                .map(day -> new WeeklyActivityDay(WeekDay.from(day.date().getDayOfWeek()), day.participated()))
                .toList();
        return new StreakSummary(streak.currentStreak(), streak.maxStreak(), weeklyActivity);
    }
}
