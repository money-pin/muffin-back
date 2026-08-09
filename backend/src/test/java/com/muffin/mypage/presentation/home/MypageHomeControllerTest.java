package com.muffin.mypage.presentation.home;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.character.domain.characterprofile.CharacterProfile;
import com.muffin.character.domain.characterprofile.CharacterRepository;
import com.muffin.character.domain.enums.MuffinType;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.readhistory.ReadHistory;
import com.muffin.news.domain.readhistory.ReadHistoryRepository;
import com.muffin.quiz.domain.quizsession.QuizSession;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.scrap.domain.Scrap;
import com.muffin.scrap.domain.ScrapRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 마이페이지 홈 조회 API가 실제 서비스/저장소/QueryDSL 조인/Security까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MypageHomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private QuizSessionRepository quizSessionRepository;

    @Autowired
    private NewsRepository newsRepository;

    @Autowired
    private ReadHistoryRepository readHistoryRepository;

    @Autowired
    private ScrapRepository scrapRepository;

    @Autowired
    private Clock clock;

    @AfterEach
    void cleanUp() {
        scrapRepository.deleteAll();
        readHistoryRepository.deleteAll();
        newsRepository.deleteAll();
        quizSessionRepository.deleteAll();
        userRepository.deleteAll();
        characterRepository.deleteAll();
    }

    private CharacterProfile seedCharacter() {
        return characterRepository.save(
                CharacterProfile.create(MuffinType.PLAIN, "플레인 머핀", "설명", "https://example.com/plain.png"));
    }

    private User createOnboardedUser(Long characterId) {
        return createOnboardedUser(
                characterId, "길동" + UUID.randomUUID().toString().substring(0, 4));
    }

    private User createOnboardedUser(Long characterId, String nickname) {
        User user = User.register(characterId, UUID.randomUUID().toString(), "홍길동", nickname);
        return userRepository.save(user);
    }

    private int sundayBasedIndex(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SUNDAY -> 0;
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 3;
            case THURSDAY -> 4;
            case FRIDAY -> 5;
            case SATURDAY -> 6;
        };
    }

    private void finishQuizSession(Long userId, Long dailyQuizSetId, LocalDate date) {
        QuizSession session = QuizSession.start(userId, dailyQuizSetId, date, 1);
        session.recordAttempt(1L, 1L, true, 100L, date.atStartOfDay());
        quizSessionRepository.save(session);
    }

    private News saveReadNews(Long userId, String title, String originalUrl) {
        News news = newsRepository.save(
                News.processing(1L, title, "테스트뉴스", LocalDateTime.now(), "http://thumb", originalUrl));
        readHistoryRepository.save(ReadHistory.create(userId, news.getId()));
        return news;
    }

    private String bearerTokenFor(Long userId) {
        return "Bearer " + accessTokenProvider.issue(userId, "USER");
    }

    @Test
    @DisplayName("닉네임/캐릭터/스트릭/최근 뉴스를 조합해서 200으로 응답한다")
    void getHome_success() throws Exception {
        CharacterProfile character = seedCharacter();
        User user = createOnboardedUser(character.getCharacterId(), "길동이");
        finishQuizSession(user.getUserId(), 1L, LocalDate.now(clock));
        saveReadNews(user.getUserId(), "뉴스1", "http://origin/1");
        News scrappedNews = saveReadNews(user.getUserId(), "뉴스2", "http://origin/2");
        scrapRepository.save(Scrap.create(user.getUserId(), scrappedNews.getId()));

        mockMvc.perform(get("/api/mypage/home").header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.nickname", is("길동이")))
                .andExpect(jsonPath("$.result.character.characterType", is("PLAIN")))
                .andExpect(jsonPath("$.result.character.characterName", is("플레인 머핀")))
                .andExpect(jsonPath("$.result.streak.currentStreak", is(1)))
                .andExpect(jsonPath("$.result.recentNews.length()", is(2)))
                .andExpect(jsonPath("$.result.recentNews[0].title", is("뉴스2")))
                .andExpect(jsonPath("$.result.recentNews[0].isScrapped", is(true)))
                .andExpect(jsonPath("$.result.recentNews[1].isScrapped", is(false)));
    }

    @Test
    @DisplayName("과거의 더 긴 연속 기록이 maxStreak에 반영되고, weeklyActivity는 이번 주 참여 현황을 정확히 보여준다")
    void getHome_maxStreakAndWeeklyActivity() throws Exception {
        CharacterProfile character = seedCharacter();
        User user = createOnboardedUser(character.getCharacterId());
        LocalDate today = LocalDate.now(clock);
        finishQuizSession(user.getUserId(), 1L, today);
        // 이번 주 범위 밖(20일 전)의 4일 연속 기록: currentStreak(1)보다 긴 maxStreak를 만든다.
        finishQuizSession(user.getUserId(), 2L, today.minusDays(20));
        finishQuizSession(user.getUserId(), 3L, today.minusDays(19));
        finishQuizSession(user.getUserId(), 4L, today.minusDays(18));
        finishQuizSession(user.getUserId(), 5L, today.minusDays(17));

        var result = mockMvc.perform(get("/api/mypage/home").header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.streak.currentStreak", is(1)))
                .andExpect(jsonPath("$.result.streak.maxStreak", is(4)))
                .andExpect(jsonPath("$.result.streak.weeklyActivity.length()", is(7)));

        int todayIndex = sundayBasedIndex(today.getDayOfWeek());
        for (int i = 0; i < 7; i++) {
            result.andExpect(jsonPath("$.result.streak.weeklyActivity[" + i + "].participated", is(i == todayIndex)));
        }
    }

    @Test
    @DisplayName("읽은 뉴스가 3건보다 많아도 최신순 상위 3건만 반환한다")
    void getHome_recentNewsTruncatedToThree() throws Exception {
        CharacterProfile character = seedCharacter();
        User user = createOnboardedUser(character.getCharacterId());
        saveReadNews(user.getUserId(), "뉴스1", "http://origin/1");
        saveReadNews(user.getUserId(), "뉴스2", "http://origin/2");
        saveReadNews(user.getUserId(), "뉴스3", "http://origin/3");
        saveReadNews(user.getUserId(), "뉴스4", "http://origin/4");

        mockMvc.perform(get("/api/mypage/home").header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.recentNews.length()", is(3)))
                .andExpect(jsonPath("$.result.recentNews[0].title", is("뉴스4")))
                .andExpect(jsonPath("$.result.recentNews[2].title", is("뉴스2")));
    }

    @Test
    @DisplayName("다른 유저의 퀴즈 기록/열람 이력은 내 스트릭/최근 뉴스에 섞이지 않는다")
    void getHome_excludesOtherUsersData() throws Exception {
        CharacterProfile character = seedCharacter();
        User me = createOnboardedUser(character.getCharacterId(), "나야나");
        User other = createOnboardedUser(character.getCharacterId(), "다른사람");

        finishQuizSession(other.getUserId(), 1L, LocalDate.now(clock));
        saveReadNews(other.getUserId(), "남의뉴스", "http://origin/other");

        mockMvc.perform(get("/api/mypage/home").header("Authorization", bearerTokenFor(me.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.streak.currentStreak", is(0)))
                .andExpect(jsonPath("$.result.recentNews.length()", is(0)));
    }

    @Test
    @DisplayName("온보딩 미완료(characterId=null) 유저는 409")
    void getHome_onboardingNotCompleted() throws Exception {
        User user = userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", "길동이"));

        mockMvc.perform(get("/api/mypage/home").header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("USER_409_001")));
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401")
    void getHome_unauthorized() throws Exception {
        mockMvc.perform(get("/api/mypage/home")).andExpect(status().isUnauthorized());
    }
}
