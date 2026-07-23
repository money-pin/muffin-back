package com.muffin.mypage.presentation.home;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.character.domain.CharacterProfile;
import com.muffin.character.domain.CharacterRepository;
import com.muffin.character.domain.enums.MuffinType;
import com.muffin.news.domain.news.News;
import com.muffin.news.domain.news.NewsRepository;
import com.muffin.news.domain.readhistory.ReadHistory;
import com.muffin.news.domain.readhistory.ReadHistoryRepository;
import com.muffin.quiz.domain.quizsession.QuizSession;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
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
class MyPageHomeControllerTest {

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

    @AfterEach
    void cleanUp() {
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
        User user = User.register(characterId, UUID.randomUUID().toString(), "홍길동", "길동이");
        return userRepository.save(user);
    }

    private void finishQuizSession(Long userId, Long dailyQuizSetId, LocalDate date) {
        QuizSession session = QuizSession.start(userId, dailyQuizSetId, date, 1);
        session.recordAttempt(1L, 1L, true, 100L, date.atStartOfDay());
        quizSessionRepository.save(session);
    }

    private void saveReadNews(Long userId, String title, String originalUrl) {
        News news = newsRepository.save(
                News.processing(1L, title, "테스트뉴스", LocalDateTime.now(), "http://thumb", originalUrl));
        readHistoryRepository.save(ReadHistory.create(userId, news.getId()));
    }

    private String bearerTokenFor(Long userId) {
        return "Bearer " + accessTokenProvider.issue(userId, "USER");
    }

    @Test
    @DisplayName("닉네임/캐릭터/스트릭/최근 뉴스를 조합해서 200으로 응답한다")
    void getHome_success() throws Exception {
        CharacterProfile character = seedCharacter();
        User user = createOnboardedUser(character.getCharacterId());
        finishQuizSession(user.getUserId(), 1L, LocalDate.now());
        saveReadNews(user.getUserId(), "뉴스1", "http://origin/1");
        saveReadNews(user.getUserId(), "뉴스2", "http://origin/2");

        mockMvc.perform(get("/api/mypage/home").header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.nickname", is("길동이")))
                .andExpect(jsonPath("$.result.character.characterType", is("PLAIN")))
                .andExpect(jsonPath("$.result.character.characterName", is("플레인 머핀")))
                .andExpect(jsonPath("$.result.streak.currentStreak", is(1)))
                .andExpect(jsonPath("$.result.recentNews.length()", is(2)))
                .andExpect(jsonPath("$.result.recentNews[0].title", is("뉴스2")));
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
