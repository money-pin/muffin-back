package com.muffin.quiz.presentation;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.quiz.application.QuizCommandService;
import com.muffin.quiz.application.QuizQueryService;
import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import com.muffin.quiz.exception.QuizErrorCode;
import com.muffin.quiz.presentation.dto.request.QuizAttemptRequest;
import com.muffin.quiz.presentation.dto.response.QuizAttemptProgressResponse;
import com.muffin.quiz.presentation.dto.response.QuizAttemptResponse;
import com.muffin.quiz.presentation.dto.response.QuizOptionResponse;
import com.muffin.quiz.presentation.dto.response.QuizProgressResponse;
import com.muffin.quiz.presentation.dto.response.QuizQuestionResponse;
import com.muffin.quiz.presentation.dto.response.QuizResultProgressResponse;
import com.muffin.quiz.presentation.dto.response.QuizResultResponse;
import com.muffin.quiz.presentation.dto.response.QuizRewardResponse;
import com.muffin.quiz.presentation.dto.response.TodayQuizResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** QUIZ-01/02/03 오늘의 한입 퀴즈 API의 REST Docs 스니펫을 생성한다. */
@ExtendWith(RestDocumentationExtension.class)
class QuizControllerDocsTest {

    private static final String AUTHORIZATION = "Bearer {accessToken}";

    @BeforeEach
    void setUpAuthentication() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("오늘의 한입 퀴즈 조회 성공 문서화")
    void documentTodayQuizSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        TodayQuizResponse response = new TodayQuizResponse(
                1L,
                LocalDate.of(2026, 7, 2),
                QuizSetStatus.PUBLISHED,
                QuizSessionStatus.NOT_STARTED,
                "예은",
                new QuizProgressResponse(3, 0, 0, 1),
                List.of(new QuizQuestionResponse(
                        101L,
                        1,
                        "오늘 뉴스에 나온 ETF는 무엇의 약자일까요?",
                        List.of(
                                new QuizOptionResponse(1001L, 1, "상장지수펀드 (Exchange Traded Fund)"),
                                new QuizOptionResponse(1002L, 2, "개인종합자산관리계좌 (ISA)"),
                                new QuizOptionResponse(1003L, 3, "퇴직연금 (IRP)")))));
        MockMvc mockMvc = mockMvcOf(queryStub(response, null), commandStub(null), restDocumentation);

        mockMvc.perform(get("/api/quizzes/today").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "quiz-today-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.dailyQuizSetId").description("오늘의 퀴즈 세트 ID"),
                                fieldWithPath("result.quizDate").description("퀴즈 날짜(KST)"),
                                fieldWithPath("result.quizSetStatus").description("퀴즈 세트 상태"),
                                fieldWithPath("result.sessionStatus").description("사용자의 풀이 세션 상태"),
                                fieldWithPath("result.nickname").description("개인화 문구에 사용할 닉네임"),
                                fieldWithPath("result.progress.totalCount").description("전체 문항 수"),
                                fieldWithPath("result.progress.solvedCount").description("풀이 완료 문항 수"),
                                fieldWithPath("result.progress.correctCount").description("정답 문항 수"),
                                fieldWithPath("result.progress.currentQuestionOrder")
                                        .description("이어 풀 문항 순서. 완료/이용 불가 상태면 null"),
                                fieldWithPath("result.questions[].quizId").description("퀴즈 문항 ID"),
                                fieldWithPath("result.questions[].quizOrder").description("문항 순서"),
                                fieldWithPath("result.questions[].question").description("문제 텍스트"),
                                fieldWithPath("result.questions[].options[].optionId")
                                        .description("선택지 ID"),
                                fieldWithPath("result.questions[].options[].optionOrder")
                                        .description("선택지 순서"),
                                fieldWithPath("result.questions[].options[].content")
                                        .description("선택지 내용"))));
    }

    @Test
    @DisplayName("오늘의 한입 퀴즈 이어 풀기 상태 문서화")
    void documentTodayQuizProgress(RestDocumentationContextProvider restDocumentation) throws Exception {
        TodayQuizResponse response = new TodayQuizResponse(
                1L,
                LocalDate.of(2026, 7, 2),
                QuizSetStatus.PUBLISHED,
                QuizSessionStatus.PROGRESS,
                "예은",
                new QuizProgressResponse(3, 1, 0, 2),
                List.of(
                        new QuizQuestionResponse(
                                101L,
                                1,
                                "오늘 뉴스에 나온 ETF는 무엇의 약자일까요?",
                                List.of(
                                        new QuizOptionResponse(1001L, 1, "상장지수펀드 (Exchange Traded Fund)"),
                                        new QuizOptionResponse(1002L, 2, "개인종합자산관리계좌 (ISA)"),
                                        new QuizOptionResponse(1003L, 3, "퇴직연금 (IRP)"))),
                        new QuizQuestionResponse(
                                102L,
                                2,
                                "반도체 수출 실적이 좋아졌다면 어떤 섹터가 유리할까요?",
                                List.of(
                                        new QuizOptionResponse(1004L, 1, "원자재(금/은) 섹터"),
                                        new QuizOptionResponse(1005L, 2, "반도체/IT 섹터"),
                                        new QuizOptionResponse(1006L, 3, "방산 섹터")))));
        MockMvc mockMvc = mockMvcOf(queryStub(response, null), commandStub(null), restDocumentation);

        mockMvc.perform(get("/api/quizzes/today").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "quiz-today-progress",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.dailyQuizSetId").description("오늘의 퀴즈 세트 ID"),
                                fieldWithPath("result.quizDate").description("퀴즈 날짜(KST)"),
                                fieldWithPath("result.quizSetStatus").description("퀴즈 세트 상태"),
                                fieldWithPath("result.sessionStatus").description("사용자의 풀이 세션 상태(PROGRESS)"),
                                fieldWithPath("result.nickname").description("개인화 문구에 사용할 닉네임"),
                                fieldWithPath("result.progress.totalCount").description("전체 문항 수"),
                                fieldWithPath("result.progress.solvedCount").description("풀이 완료 문항 수"),
                                fieldWithPath("result.progress.correctCount").description("정답 문항 수"),
                                fieldWithPath("result.progress.currentQuestionOrder")
                                        .description("이어 풀 문항 순서. 프론트는 이 문항부터 보여준다."),
                                fieldWithPath("result.questions[].quizId").description("퀴즈 문항 ID"),
                                fieldWithPath("result.questions[].quizOrder").description("문항 순서"),
                                fieldWithPath("result.questions[].question").description("문제 텍스트"),
                                fieldWithPath("result.questions[].options[].optionId")
                                        .description("선택지 ID"),
                                fieldWithPath("result.questions[].options[].optionOrder")
                                        .description("선택지 순서"),
                                fieldWithPath("result.questions[].options[].content")
                                        .description("선택지 내용"))));
    }

    @Test
    @DisplayName("오늘의 한입 퀴즈 이용 불가 상태 문서화")
    void documentTodayQuizUnavailable(RestDocumentationContextProvider restDocumentation) throws Exception {
        TodayQuizResponse response = new TodayQuizResponse(
                null,
                LocalDate.of(2026, 7, 14),
                QuizSetStatus.UNAVAILABLE,
                null,
                "예은",
                new QuizProgressResponse(3, 0, 0, null),
                List.of());
        MockMvc mockMvc = mockMvcOf(queryStub(response, null), commandStub(null), restDocumentation);

        mockMvc.perform(get("/api/quizzes/today").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "quiz-today-unavailable",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.dailyQuizSetId").description("퀴즈 세트가 없으면 null"),
                                fieldWithPath("result.quizDate").description("조회 날짜(KST)"),
                                fieldWithPath("result.quizSetStatus").description("UNAVAILABLE"),
                                fieldWithPath("result.sessionStatus").description("이용 불가 상태이므로 null"),
                                fieldWithPath("result.nickname").description("개인화 문구에 사용할 닉네임"),
                                fieldWithPath("result.progress.totalCount").description("전체 문항 수(정책상 3)"),
                                fieldWithPath("result.progress.solvedCount").description("풀이 완료 문항 수"),
                                fieldWithPath("result.progress.correctCount").description("정답 문항 수"),
                                fieldWithPath("result.progress.currentQuestionOrder")
                                        .description("이용 불가 상태이므로 null"),
                                fieldWithPath("result.questions").description("빈 배열"))));
    }

    @Test
    @DisplayName("퀴즈 답안 제출 성공 문서화")
    void documentAttemptSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        QuizAttemptResponse response = new QuizAttemptResponse(
                501L,
                101L,
                1002L,
                1001L,
                false,
                "ETF는 주식처럼 거래소에서 사고팔 수 있는 펀드를 말해요.",
                QuizSessionStatus.PROGRESS,
                false,
                new QuizAttemptProgressResponse(3, 1, 0, 2),
                OffsetDateTime.parse("2026-07-02T12:43:00+09:00"));
        MockMvc mockMvc = mockMvcOf(queryStub(null, null), commandStub(response), restDocumentation);

        mockMvc.perform(post("/api/quizzes/{quizId}/attempt", 101L)
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optionId\":1002}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "quiz-attempt-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("quizId").description("답안을 제출할 퀴즈 문항 ID")),
                        requestFields(fieldWithPath("optionId").description("사용자가 선택한 선택지 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.attemptId").description("답안 제출 ID"),
                                fieldWithPath("result.quizId").description("퀴즈 문항 ID"),
                                fieldWithPath("result.selectedOptionId").description("사용자가 선택한 선택지 ID"),
                                fieldWithPath("result.correctOptionId").description("정답 선택지 ID"),
                                fieldWithPath("result.isCorrect").description("정답 여부"),
                                fieldWithPath("result.explanation").description("문항 해설"),
                                fieldWithPath("result.sessionStatus").description("제출 후 세션 상태"),
                                fieldWithPath("result.isLastQuestion").description("마지막 문항 여부"),
                                fieldWithPath("result.progress.totalCount").description("전체 문항 수"),
                                fieldWithPath("result.progress.solvedCount").description("풀이 완료 문항 수"),
                                fieldWithPath("result.progress.correctCount").description("정답 문항 수"),
                                fieldWithPath("result.progress.nextQuestionOrder")
                                        .description("다음 문항 순서. 마지막 문항이면 null"),
                                fieldWithPath("result.submittedAt").description("제출 시각(KST 오프셋 포함)"))));
    }

    @Test
    @DisplayName("오늘의 퀴즈 결과 조회 성공 문서화")
    void documentResultSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        QuizResultResponse response = new QuizResultResponse(
                501L,
                LocalDate.of(2026, 7, 2),
                QuizSessionStatus.FINISHED,
                new QuizResultProgressResponse(3, 2, 1),
                new QuizRewardResponse(200_000L, true));
        MockMvc mockMvc = mockMvcOf(queryStub(null, response), commandStub(null), restDocumentation);

        mockMvc.perform(get("/api/quizzes/today/result").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "quiz-result-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.quizSessionId").description("퀴즈 세션 ID"),
                                fieldWithPath("result.quizDate").description("퀴즈 날짜(KST)"),
                                fieldWithPath("result.sessionStatus").description("세션 상태(FINISHED)"),
                                fieldWithPath("result.progress.totalCount").description("전체 문항 수"),
                                fieldWithPath("result.progress.correctCount").description("정답 문항 수"),
                                fieldWithPath("result.progress.incorrectCount").description("오답 문항 수"),
                                fieldWithPath("result.reward.amount").description("지급된 보상 금액"),
                                fieldWithPath("result.reward.claimed").description("보상 지급 완료 여부"))));
    }

    @Test
    @DisplayName("오늘의 퀴즈 결과 조회 실패(미완료) 문서화")
    void documentResultNotReady(RestDocumentationContextProvider restDocumentation) throws Exception {
        QuizQueryService queryService = new QuizQueryService(null, null, null) {
            @Override
            public QuizResultResponse getTodayQuizResult(Long userId) {
                throw new GeneralException(QuizErrorCode.QUIZ_RESULT_NOT_READY);
            }
        };
        MockMvc mockMvc = mockMvcOf(queryService, commandStub(null), restDocumentation);

        mockMvc.perform(get("/api/quizzes/today/result").header("Authorization", AUTHORIZATION))
                .andExpect(status().isConflict())
                .andDo(document(
                        "quiz-result-not-ready",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(QUIZ_409_001)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    private QuizQueryService queryStub(TodayQuizResponse todayResponse, QuizResultResponse resultResponse) {
        return new QuizQueryService(null, null, null) {
            @Override
            public TodayQuizResponse getTodayQuiz(Long userId) {
                return todayResponse;
            }

            @Override
            public QuizResultResponse getTodayQuizResult(Long userId) {
                return resultResponse;
            }
        };
    }

    private QuizCommandService commandStub(QuizAttemptResponse response) {
        return new QuizCommandService(null, null, null, null) {
            @Override
            public QuizAttemptResponse submitAnswer(Long userId, Long quizId, QuizAttemptRequest request) {
                return response;
            }
        };
    }

    private MockMvc mockMvcOf(
            QuizQueryService queryService,
            QuizCommandService commandService,
            RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new QuizController(queryService, commandService))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
