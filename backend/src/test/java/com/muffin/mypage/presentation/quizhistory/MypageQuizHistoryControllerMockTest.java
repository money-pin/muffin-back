package com.muffin.mypage.presentation.quizhistory;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.mypage.application.quizhistory.MypageQuizHistoryQueryService;
import com.muffin.mypage.presentation.MypageController;
import com.muffin.quiz.domain.quizsession.QuizSession;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** MypageController의 퀴즈 참여 내역 조회를 서비스는 mock으로 격리해 컨트롤러 계층만 단위 테스트한다. */
@ExtendWith(MockitoExtension.class)
class MypageQuizHistoryControllerMockTest {

    private static final Long USER_ID = 1L;

    @Mock
    private MypageQuizHistoryQueryService mypageQuizHistoryQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
        mockMvc = standaloneSetup(new MypageController(null, null, null, mypageQuizHistoryQueryService))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /quiz-history는 access token의 userId와 year/month 파라미터로 서비스를 호출하고 결과를 그대로 응답한다")
    void getQuizHistory_delegatesToServiceWithAuthenticatedUserIdAndParams() throws Exception {
        LocalDate date = LocalDate.of(2026, 7, 20);
        QuizSession session = QuizSession.start(USER_ID, 1L, date, 3);
        session.recordAttempt(1L, 1L, true, 100L, date.atStartOfDay());
        session.recordAttempt(2L, 2L, true, 100L, date.atStartOfDay());
        session.recordAttempt(3L, 3L, false, null, date.atStartOfDay());
        ReflectionTestUtils.setField(session, "id", 10L);

        when(mypageQuizHistoryQueryService.getQuizHistory(USER_ID, 2026, 7)).thenReturn(List.of(session));

        mockMvc.perform(get("/api/mypage/quiz-history").param("year", "2026").param("month", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.year").value(2026))
                .andExpect(jsonPath("$.result.month").value(7))
                .andExpect(jsonPath("$.result.quizSessions[0].sessionId").value(10))
                .andExpect(jsonPath("$.result.quizSessions[0].rewardMoney").value(200));

        verify(mypageQuizHistoryQueryService).getQuizHistory(USER_ID, 2026, 7);
    }

    @Test
    @DisplayName("year/month 파라미터가 없으면 400")
    void getQuizHistory_missingParams_badRequest() throws Exception {
        mockMvc.perform(get("/api/mypage/quiz-history")).andExpect(status().isBadRequest());
    }
}
