package com.muffin.news.presentation;

import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.application.term.TermCommandService;
import com.muffin.news.application.term.TermQueryService;
import com.muffin.news.presentation.dto.TermResponse;
import com.muffin.news.presentation.dto.TermSaveResponse;
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** CONTENT-05 용어 사전 조회 API의 REST Docs 스니펫을 생성한다. */
@ExtendWith(RestDocumentationExtension.class)
class TermControllerDocsTest {

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
    @DisplayName("용어 사전 조회 성공 문서화")
    void documentSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        TermResponse response = new TermResponse(12L, "양적완화", "중앙은행이 시중에 돈을 더 많이 풀어서 경제를 활성화하는 정책이에요.", false);
        MockMvc mockMvc = mockMvcOf(stubReturning(response), restDocumentation);

        mockMvc.perform(get("/api/terms/{termId}", 12L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "term-get-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("termId").description("조회할 용어 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.termId").description("경제 용어 식별자"),
                                fieldWithPath("result.term").description("경제 용어명"),
                                fieldWithPath("result.content").description("경제 용어 설명"),
                                fieldWithPath("result.isSaved").description("현재 사용자의 용어 저장 여부"))));
    }

    @Test
    @DisplayName("용어 사전 조회 실패(용어 없음) 문서화")
    void documentNotFound(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcOf(stubThrowingNotFound(), restDocumentation);

        mockMvc.perform(get("/api/terms/{termId}", 999L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isNotFound())
                .andDo(document(
                        "term-get-not-found",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("termId").description("조회할 용어 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(CONTENT_404_002)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("용어 저장 성공 문서화")
    void documentSaveSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        TermSaveResponse response = new TermSaveResponse(12L, "기준금리", true, LocalDateTime.of(2026, 7, 26, 13, 30));
        MockMvc mockMvc = mockMvcOf(
                stubReturning(new TermResponse(12L, "기준금리", "설명", false)),
                commandReturning(response),
                restDocumentation);

        mockMvc.perform(put("/api/terms/{termId}/saved-term", 12L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "term-save-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("termId").description("저장할 용어 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.termId").description("경제 용어 식별자"),
                                fieldWithPath("result.term").description("경제 용어명"),
                                fieldWithPath("result.isSaved").description("저장 여부(true)"),
                                fieldWithPath("result.savedAt").description("저장 시각"))));
    }

    @Test
    @DisplayName("용어 저장 실패(용어 없음) 문서화")
    void documentSaveNotFound(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcOf(
                stubReturning(new TermResponse(12L, "기준금리", "설명", false)),
                commandThrowingNotFound(),
                restDocumentation);

        mockMvc.perform(put("/api/terms/{termId}/saved-term", 999L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isNotFound())
                .andDo(document(
                        "term-save-not-found",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("termId").description("저장할 용어 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(CONTENT_404_002)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("용어 저장 해제 성공 문서화")
    void documentUnsaveSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        TermSaveResponse response = new TermSaveResponse(12L, "기준금리", false, null);
        MockMvc mockMvc = mockMvcOf(
                stubReturning(new TermResponse(12L, "기준금리", "설명", true)),
                commandReturning(response),
                restDocumentation);

        mockMvc.perform(delete("/api/terms/{termId}/saved-term", 12L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "term-unsave-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("termId").description("저장 해제할 용어 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.termId").description("경제 용어 식별자"),
                                fieldWithPath("result.term").description("경제 용어명"),
                                fieldWithPath("result.isSaved").description("저장 여부(false)"),
                                fieldWithPath("result.savedAt").description("저장 해제 상태이므로 null"))));
    }

    @Test
    @DisplayName("용어 저장 해제 실패(용어 없음) 문서화")
    void documentUnsaveNotFound(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcOf(
                stubReturning(new TermResponse(12L, "기준금리", "설명", true)), commandThrowingNotFound(), restDocumentation);

        mockMvc.perform(delete("/api/terms/{termId}/saved-term", 999L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isNotFound())
                .andDo(document(
                        "term-unsave-not-found",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("termId").description("저장 해제할 용어 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(CONTENT_404_002)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    private TermQueryService stubReturning(TermResponse response) {
        return new TermQueryService(null, null, null) {
            @Override
            public TermResponse getTerm(Long userId, Long termId) {
                return response;
            }
        };
    }

    private TermQueryService stubThrowingNotFound() {
        return new TermQueryService(null, null, null) {
            @Override
            public TermResponse getTerm(Long userId, Long termId) {
                throw new NewsException(NewsErrorCode.NEWS_TERM_NOT_FOUND);
            }
        };
    }

    private MockMvc mockMvcOf(TermQueryService stubService, RestDocumentationContextProvider restDocumentation) {
        return mockMvcOf(stubService, commandStub(), restDocumentation);
    }

    private MockMvc mockMvcOf(
            TermQueryService stubService,
            TermCommandService commandStub,
            RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new TermController(stubService, commandStub))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    private TermCommandService commandStub() {
        return new TermCommandService(null, null, null);
    }

    private TermCommandService commandReturning(TermSaveResponse response) {
        return new TermCommandService(null, null, null) {
            @Override
            public TermSaveResponse saveTerm(Long userId, Long termId) {
                return response;
            }

            @Override
            public TermSaveResponse unsaveTerm(Long userId, Long termId) {
                return response;
            }
        };
    }

    private TermCommandService commandThrowingNotFound() {
        return new TermCommandService(null, null, null) {
            @Override
            public TermSaveResponse saveTerm(Long userId, Long termId) {
                throw new NewsException(NewsErrorCode.NEWS_TERM_NOT_FOUND);
            }

            @Override
            public TermSaveResponse unsaveTerm(Long userId, Long termId) {
                throw new NewsException(NewsErrorCode.NEWS_TERM_NOT_FOUND);
            }
        };
    }
}
