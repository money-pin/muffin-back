package com.muffin.mypage.presentation.quizhistory;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.quiz.domain.quizsession.QuizSession;
import com.muffin.quiz.domain.quizsession.QuizSessionRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 마이페이지 월별 퀴즈 참여 내역 조회 API가 실제 서비스/저장소/Security까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MypageQuizHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QuizSessionRepository quizSessionRepository;

    @AfterEach
    void cleanUp() {
        quizSessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createUser() {
        return userRepository.save(User.register(
                1L,
                UUID.randomUUID().toString(),
                "홍길동",
                "길동" + UUID.randomUUID().toString().substring(0, 4)));
    }

    private void finishQuizSession(Long userId, Long dailyQuizSetId, LocalDate date, int totalCount, int correctCount) {
        QuizSession session = QuizSession.start(userId, dailyQuizSetId, date, totalCount);
        for (int i = 0; i < totalCount; i++) {
            boolean correct = i < correctCount;
            session.recordAttempt((long) (i + 1), (long) (i + 1), correct, correct ? 100L : null, date.atStartOfDay());
        }
        quizSessionRepository.save(session);
    }

    private String bearerTokenFor(Long userId) {
        return "Bearer " + accessTokenProvider.issue(userId, "USER");
    }

    @Test
    @DisplayName("해당 연/월의 퀴즈 세션을 날짜 내림차순으로 200 응답한다")
    void getQuizHistory_success() throws Exception {
        User user = createUser();
        finishQuizSession(user.getUserId(), 1L, LocalDate.of(2026, 7, 20), 3, 2);
        finishQuizSession(user.getUserId(), 2L, LocalDate.of(2026, 7, 5), 3, 1);
        // 다른 달의 기록은 결과에서 제외되어야 한다.
        finishQuizSession(user.getUserId(), 3L, LocalDate.of(2026, 6, 30), 3, 3);

        mockMvc.perform(get("/api/mypage/quiz-history")
                        .param("year", "2026")
                        .param("month", "7")
                        .header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.year", is(2026)))
                .andExpect(jsonPath("$.result.month", is(7)))
                .andExpect(jsonPath("$.result.quizSessions.length()", is(2)))
                .andExpect(jsonPath("$.result.quizSessions[0].date", is("2026-07-20")))
                .andExpect(jsonPath("$.result.quizSessions[0].correctCount", is(2)))
                .andExpect(jsonPath("$.result.quizSessions[0].totalCount", is(3)))
                .andExpect(jsonPath("$.result.quizSessions[0].rewardMoney", is(200)))
                .andExpect(jsonPath("$.result.quizSessions[1].date", is("2026-07-05")));
    }

    @Test
    @DisplayName("해당 월에 참여 기록이 없으면 빈 배열로 200 응답한다")
    void getQuizHistory_empty() throws Exception {
        User user = createUser();

        mockMvc.perform(get("/api/mypage/quiz-history")
                        .param("year", "2026")
                        .param("month", "7")
                        .header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.quizSessions.length()", is(0)));
    }

    @Test
    @DisplayName("다른 유저의 퀴즈 참여 기록은 섞이지 않는다")
    void getQuizHistory_excludesOtherUsersData() throws Exception {
        User me = createUser();
        User other = createUser();
        finishQuizSession(other.getUserId(), 1L, LocalDate.of(2026, 7, 20), 3, 2);

        mockMvc.perform(get("/api/mypage/quiz-history")
                        .param("year", "2026")
                        .param("month", "7")
                        .header("Authorization", bearerTokenFor(me.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.quizSessions.length()", is(0)));
    }

    @Test
    @DisplayName("month가 1~12 범위를 벗어나면 400(MYPAGE_400_005)")
    void getQuizHistory_invalidMonth() throws Exception {
        User user = createUser();

        mockMvc.perform(get("/api/mypage/quiz-history")
                        .param("year", "2026")
                        .param("month", "13")
                        .header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MYPAGE_400_005")));
    }

    @Test
    @DisplayName("year/month 파라미터가 없으면 400")
    void getQuizHistory_missingParams() throws Exception {
        User user = createUser();

        mockMvc.perform(get("/api/mypage/quiz-history").header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401")
    void getQuizHistory_unauthorized() throws Exception {
        mockMvc.perform(get("/api/mypage/quiz-history").param("year", "2026").param("month", "7"))
                .andExpect(status().isUnauthorized());
    }
}
