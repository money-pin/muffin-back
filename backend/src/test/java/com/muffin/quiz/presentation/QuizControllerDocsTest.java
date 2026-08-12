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
import com.muffin.quiz.presentation.dto.QuizAttemptProgressResponse;
import com.muffin.quiz.presentation.dto.QuizAttemptRequest;
import com.muffin.quiz.presentation.dto.QuizAttemptResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryDetailResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryDetailSummaryResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryListResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryOptionResponse;
import com.muffin.quiz.presentation.dto.QuizHistoryQuestionResponse;
import com.muffin.quiz.presentation.dto.QuizHistorySummaryResponse;
import com.muffin.quiz.presentation.dto.QuizOptionResponse;
import com.muffin.quiz.presentation.dto.QuizProgressResponse;
import com.muffin.quiz.presentation.dto.QuizQuestionResponse;
import com.muffin.quiz.presentation.dto.QuizResultProgressResponse;
import com.muffin.quiz.presentation.dto.QuizResultResponse;
import com.muffin.quiz.presentation.dto.QuizRewardResponse;
import com.muffin.quiz.presentation.dto.TodayQuizResponse;
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
                        "ETF는 무엇의 약자일까요?",
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
                                fieldWithPath("result.progress.nextQuestionOrder")
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
                                "ETF는 무엇의 약자일까요?",
                                List.of(
                                        new QuizOptionResponse(1001L, 1, "상장지수펀드 (Exchange Traded Fund)"),
                                        new QuizOptionResponse(1002L, 2, "개인종합자산관리계좌 (ISA)"),
                                        new QuizOptionResponse(1003L, 3, "퇴직연금 (IRP)"))),
                        new QuizQuestionResponse(
                                102L,
                                2,
                                "반도체 산업에서 파운드리란 무엇을 의미하나요?",
                                List.of(
                                        new QuizOptionResponse(1004L, 1, "반도체 설계 전문 회사"),
                                        new QuizOptionResponse(1005L, 2, "반도체 생산 전문 회사"),
                                        new QuizOptionResponse(1006L, 3, "반도체 유통 회사")))));
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
                                fieldWithPath("result.progress.nextQuestionOrder")
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
                                fieldWithPath("result.progress.nextQuestionOrder")
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

        mockMvc.perform(post("/api/quizzes/{quizId}/attempts", 101L)
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

    @Test
    @DisplayName("지난 퀴즈 복습 목록 조회 성공 문서화")
    void documentQuizHistoryListSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        QuizHistoryListResponse response = new QuizHistoryListResponse(List.of(
                new QuizHistorySummaryResponse(LocalDate.of(2026, 5, 8), 3, 2, 1),
                new QuizHistorySummaryResponse(LocalDate.of(2026, 5, 7), 3, 1, 2)));
        MockMvc mockMvc = mockMvcOf(historyQueryStub(response, null), commandStub(null), restDocumentation);

        mockMvc.perform(get("/api/quizzes/history").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "quiz-history-list-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.histories[].quizDate").description("복습 가능한 퀴즈 날짜"),
                                fieldWithPath("result.histories[].totalCount").description("해당 날짜 전체 문항 수"),
                                fieldWithPath("result.histories[].correctCount").description("해당 날짜 정답 문항 수"),
                                fieldWithPath("result.histories[].incorrectCount")
                                        .description("해당 날짜 오답 문항 수"))));
    }

    @Test
    @DisplayName("지난 퀴즈 복습 목록 빈 결과 문서화")
    void documentQuizHistoryListEmpty(RestDocumentationContextProvider restDocumentation) throws Exception {
        QuizHistoryListResponse response = new QuizHistoryListResponse(List.of());
        MockMvc mockMvc = mockMvcOf(historyQueryStub(response, null), commandStub(null), restDocumentation);

        mockMvc.perform(get("/api/quizzes/history").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "quiz-history-list-empty",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.histories").description("복습 가능한 퀴즈 날짜 목록. 없으면 빈 배열"))));
    }

    @Test
    @DisplayName("지난 퀴즈 복습 상세 조회 성공 문서화")
    void documentQuizHistoryDetailSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        QuizHistoryDetailResponse response = new QuizHistoryDetailResponse(
                LocalDate.of(2026, 5, 8),
                new QuizHistoryDetailSummaryResponse(3, 2, 1),
                List.of(
                        new QuizHistoryQuestionResponse(
                                101L,
                                1,
                                "반도체 산업에서 '파운드리'란 무엇을 의미하나요?",
                                true,
                                1002L,
                                1002L,
                                List.of(
                                        new QuizHistoryOptionResponse(1001L, 1, "반도체 설계 전문 회사", false, false),
                                        new QuizHistoryOptionResponse(1002L, 2, "반도체 생산 전문 회사", true, true),
                                        new QuizHistoryOptionResponse(1003L, 3, "반도체 유통 회사", false, false)),
                                "파운드리는 반도체 생산만 전문적으로 하는 회사를 의미해요."),
                        new QuizHistoryQuestionResponse(
                                103L,
                                3,
                                "메모리 반도체와 시스템 반도체의 차이는?",
                                false,
                                1007L,
                                1009L,
                                List.of(
                                        new QuizHistoryOptionResponse(1007L, 1, "가격 차이", true, false),
                                        new QuizHistoryOptionResponse(1008L, 2, "크기 차이", false, false),
                                        new QuizHistoryOptionResponse(1009L, 3, "용도 차이", false, true)),
                                "메모리 반도체는 데이터를 저장하고, 시스템 반도체는 데이터를 처리하는 용도로 사용돼요.")));
        MockMvc mockMvc = mockMvcOf(historyQueryStub(null, response), commandStub(null), restDocumentation);

        mockMvc.perform(get("/api/quizzes/history/{date}", "2026-05-08").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "quiz-history-detail-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("date").description("조회할 퀴즈 날짜(yyyy-MM-dd)")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.quizDate").description("복습 퀴즈 날짜"),
                                fieldWithPath("result.summary.totalCount").description("전체 문항 수"),
                                fieldWithPath("result.summary.correctCount").description("정답 문항 수"),
                                fieldWithPath("result.summary.incorrectCount").description("오답 문항 수"),
                                fieldWithPath("result.questions[].quizId").description("퀴즈 문항 ID"),
                                fieldWithPath("result.questions[].questionOrder")
                                        .description("문항 순서"),
                                fieldWithPath("result.questions[].question").description("문제 텍스트"),
                                fieldWithPath("result.questions[].isCorrect").description("사용자 정답 여부"),
                                fieldWithPath("result.questions[].selectedOptionId")
                                        .description("사용자가 선택한 선택지 ID"),
                                fieldWithPath("result.questions[].correctOptionId")
                                        .description("정답 선택지 ID"),
                                fieldWithPath("result.questions[].options[].optionId")
                                        .description("선택지 ID"),
                                fieldWithPath("result.questions[].options[].optionOrder")
                                        .description("선택지 순서"),
                                fieldWithPath("result.questions[].options[].content")
                                        .description("선택지 내용"),
                                fieldWithPath("result.questions[].options[].isSelected")
                                        .description("사용자가 선택한 선택지 여부"),
                                fieldWithPath("result.questions[].options[].isCorrect")
                                        .description("정답 선택지 여부"),
                                fieldWithPath("result.questions[].explanation").description("문항 해설"))));
    }

    @Test
    @DisplayName("지난 퀴즈 복습 상세 빈 결과 문서화")
    void documentQuizHistoryDetailEmpty(RestDocumentationContextProvider restDocumentation) throws Exception {
        QuizHistoryDetailResponse response = new QuizHistoryDetailResponse(
                LocalDate.of(2026, 5, 8), new QuizHistoryDetailSummaryResponse(0, 0, 0), List.of());
        MockMvc mockMvc = mockMvcOf(historyQueryStub(null, response), commandStub(null), restDocumentation);

        mockMvc.perform(get("/api/quizzes/history/{date}", "2026-05-08").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "quiz-history-detail-empty",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("date").description("조회할 퀴즈 날짜(yyyy-MM-dd)")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.quizDate").description("복습 퀴즈 날짜"),
                                fieldWithPath("result.summary.totalCount").description("전체 문항 수"),
                                fieldWithPath("result.summary.correctCount").description("정답 문항 수"),
                                fieldWithPath("result.summary.incorrectCount").description("오답 문항 수"),
                                fieldWithPath("result.questions").description("복습 문항 목록. 기록이 없으면 빈 배열"))));
    }

    @Test
    @DisplayName("지난 퀴즈 복습 상세 미래 날짜 실패 문서화")
    void documentQuizHistoryDetailFutureDate(RestDocumentationContextProvider restDocumentation) throws Exception {
        QuizQueryService queryService = new QuizQueryService(null, null, null) {
            @Override
            public QuizHistoryDetailResponse getQuizHistoryDetail(Long userId, String date) {
                throw new GeneralException(QuizErrorCode.QUIZ_HISTORY_FUTURE_DATE);
            }
        };
        MockMvc mockMvc = mockMvcOf(queryService, commandStub(null), restDocumentation);

        mockMvc.perform(get("/api/quizzes/history/{date}", "2099-01-01").header("Authorization", AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "quiz-history-detail-future-date",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("date").description("조회할 퀴즈 날짜(yyyy-MM-dd)")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(QUIZ_400_002)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("지난 퀴즈 복습 상세 날짜 형식 실패 문서화")
    void documentQuizHistoryDetailInvalidDateFormat(RestDocumentationContextProvider restDocumentation)
            throws Exception {
        QuizQueryService queryService = new QuizQueryService(null, null, null) {
            @Override
            public QuizHistoryDetailResponse getQuizHistoryDetail(Long userId, String date) {
                throw new GeneralException(QuizErrorCode.QUIZ_HISTORY_INVALID_DATE_FORMAT);
            }
        };
        MockMvc mockMvc = mockMvcOf(queryService, commandStub(null), restDocumentation);

        mockMvc.perform(get("/api/quizzes/history/{date}", "2026-05").header("Authorization", AUTHORIZATION))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "quiz-history-detail-invalid-date-format",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("date").description("조회할 퀴즈 날짜(yyyy-MM-dd)")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(QUIZ_400_003)"),
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

    private QuizQueryService historyQueryStub(
            QuizHistoryListResponse listResponse, QuizHistoryDetailResponse detailResponse) {
        return new QuizQueryService(null, null, null) {
            @Override
            public QuizHistoryListResponse getQuizHistories(Long userId) {
                return listResponse;
            }

            @Override
            public QuizHistoryDetailResponse getQuizHistoryDetail(Long userId, String date) {
                return detailResponse;
            }
        };
    }

    private QuizCommandService commandStub(QuizAttemptResponse response) {
        return new QuizCommandService(null, null, null, null, null) {
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
