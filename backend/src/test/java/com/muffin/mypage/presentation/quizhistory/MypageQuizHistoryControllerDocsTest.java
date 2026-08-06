package com.muffin.mypage.presentation.quizhistory;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.mypage.application.quizhistory.MypageQuizHistoryQueryService;
import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import com.muffin.mypage.presentation.MypageController;
import com.muffin.quiz.domain.quizsession.QuizSession;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** MYPAGE-05-1 월별 퀴즈 참여 내역 조회 API의 REST Docs 스니펫을 생성한다(정상/빈 상태/400/404). */
@ExtendWith(RestDocumentationExtension.class)
class MypageQuizHistoryControllerDocsTest {

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("월별 퀴즈 참여 내역 정상 응답 문서화")
    void documentQuizHistory(RestDocumentationContextProvider restDocumentation) throws Exception {
        LocalDate finishedDate = LocalDate.of(2026, 7, 20);
        QuizSession finishedSession = QuizSession.start(USER_ID, 1L, finishedDate, 3);
        finishedSession.recordAttempt(1L, 1L, true, 100L, finishedDate.atStartOfDay());
        finishedSession.recordAttempt(2L, 2L, true, 100L, finishedDate.atStartOfDay());
        finishedSession.recordAttempt(3L, 3L, false, null, finishedDate.atStartOfDay());
        finishedSession.claimReward();
        ReflectionTestUtils.setField(finishedSession, "id", 10L);

        LocalDate progressDate = LocalDate.of(2026, 7, 5);
        QuizSession progressSession = QuizSession.start(USER_ID, 2L, progressDate, 3);
        progressSession.recordAttempt(4L, 4L, false, null, progressDate.atStartOfDay());
        ReflectionTestUtils.setField(progressSession, "id", 9L);

        MockMvc mockMvc = mockMvcWith(stubReturning(List.of(finishedSession, progressSession)), restDocumentation);

        mockMvc.perform(get("/api/mypage/quiz-history")
                        .param("year", "2026")
                        .param("month", "7")
                        .header("Authorization", "Bearer {accessToken}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "mypage-quiz-history",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        queryParameters(
                                parameterWithName("year").description("조회할 연도(예: 2026)"),
                                parameterWithName("month").description("조회할 월(1~12)")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.year").description("조회한 연도"),
                                fieldWithPath("result.month").description("조회한 월"),
                                fieldWithPath("result.quizSessions[].date").description("퀴즈 참여 날짜"),
                                fieldWithPath("result.quizSessions[].sessionId").description("퀴즈 세션 ID"),
                                fieldWithPath("result.quizSessions[].status")
                                        .description("퀴즈 진행 상태(PROGRESS/FINISHED)"),
                                fieldWithPath("result.quizSessions[].correctCount")
                                        .description("정답 문항 수"),
                                fieldWithPath("result.quizSessions[].totalCount")
                                        .description("전체 문항 수"),
                                fieldWithPath("result.quizSessions[].rewardMoney")
                                        .description("획득한 가상 머니 보상"),
                                fieldWithPath("result.quizSessions[].rewardClaimed")
                                        .description("보상 수령 여부"))));
    }

    @Test
    @DisplayName("해당 월에 참여 기록이 없는 빈 상태 응답 문서화")
    void documentEmptyQuizHistory(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(stubReturning(List.of()), restDocumentation);

        mockMvc.perform(get("/api/mypage/quiz-history").param("year", "2026").param("month", "7"))
                .andExpect(status().isOk())
                .andDo(document(
                        "mypage-quiz-history-empty",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.year").description("조회한 연도"),
                                fieldWithPath("result.month").description("조회한 월"),
                                fieldWithPath("result.quizSessions").description("빈 배열(참여 기록 없음)"))));
    }

    @Test
    @DisplayName("month가 1~12 범위를 벗어나면 400(MYPAGE_400_005) 문서화")
    void documentInvalidMonth(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(stubThrowing(MypageErrorCode.INVALID_YEAR_MONTH), restDocumentation);

        mockMvc.perform(get("/api/mypage/quiz-history").param("year", "2026").param("month", "13"))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "mypage-quiz-history-invalid-month",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(MYPAGE_400_005)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("상세 원인"))));
    }

    @Test
    @DisplayName("사용자 정보를 찾을 수 없으면 404(MYPAGE_404_001) 문서화")
    void documentUserNotFound(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(stubThrowing(MypageErrorCode.USER_NOT_FOUND), restDocumentation);

        mockMvc.perform(get("/api/mypage/quiz-history").param("year", "2026").param("month", "7"))
                .andExpect(status().isNotFound())
                .andDo(document(
                        "mypage-quiz-history-user-not-found",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(MYPAGE_404_001)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("상세 원인"))));
    }

    private MypageQuizHistoryQueryService stubReturning(List<QuizSession> quizSessions) {
        return new MypageQuizHistoryQueryService(null, null) {
            @Override
            public List<QuizSession> getQuizHistory(Long userId, int year, int month) {
                return quizSessions;
            }
        };
    }

    private MypageQuizHistoryQueryService stubThrowing(MypageErrorCode errorCode) {
        return new MypageQuizHistoryQueryService(null, null) {
            @Override
            public List<QuizSession> getQuizHistory(Long userId, int year, int month) {
                throw new MypageException(errorCode, "detail");
            }
        };
    }

    private MockMvc mockMvcWith(
            MypageQuizHistoryQueryService stubService, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new MypageController(null, null, null, stubService))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
