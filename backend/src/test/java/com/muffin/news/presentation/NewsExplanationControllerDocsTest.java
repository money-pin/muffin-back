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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.application.explanation.NewsExplanationQueryService;
import com.muffin.news.presentation.dto.NewsExplanationCardResponse;
import com.muffin.news.presentation.dto.NewsExplanationCardsResponse;
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

/** CONTENT-04 뉴스 해설 카드 조회 API의 REST Docs 스니펫을 생성한다. */
@ExtendWith(RestDocumentationExtension.class)
class NewsExplanationControllerDocsTest {

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
    @DisplayName("뉴스 해설 카드 조회 성공 문서화")
    void documentSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        NewsExplanationCardsResponse response = new NewsExplanationCardsResponse(
                15L,
                List.of(new NewsExplanationCardResponse(
                        1, "기준금리란?", "기준금리", "한국은행이 금융기관과 거래할 때 기준이 되는 금리입니다. 뉴스 속 금리 인상 흐름을 이해하는 데 필요한 핵심 개념입니다.")));
        MockMvc mockMvc = mockMvcOf(stubReturning(response), restDocumentation);

        mockMvc.perform(get("/api/news/{newsId}/explanation-cards", 15L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "news-explanation-cards-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("newsId").description("조회할 뉴스 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.newsId").description("뉴스 ID"),
                                fieldWithPath("result.cards[].cardOrder").description("해설 카드 순서"),
                                fieldWithPath("result.cards[].title").description("해설 카드 제목"),
                                fieldWithPath("result.cards[].keyTerm").description("해설 카드 핵심 용어"),
                                fieldWithPath("result.cards[].content").description("해설 카드 본문"))));
    }

    @Test
    @DisplayName("뉴스 해설 카드 미완료 상태 문서화")
    void documentNotCompleted(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcOf(stubThrowing(NewsErrorCode.NEWS_EXPLANATION_NOT_COMPLETED), restDocumentation);

        mockMvc.perform(get("/api/news/{newsId}/explanation-cards", 15L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isConflict())
                .andDo(document(
                        "news-explanation-cards-not-completed",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("newsId").description("조회할 뉴스 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(CONTENT_409_001)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("뉴스 해설 카드 조회 실패(뉴스 없음) 문서화")
    void documentNewsNotFound(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcOf(stubThrowing(NewsErrorCode.NEWS_NOT_FOUND), restDocumentation);

        mockMvc.perform(get("/api/news/{newsId}/explanation-cards", 999L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isNotFound())
                .andDo(document(
                        "news-explanation-cards-not-found",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("newsId").description("조회할 뉴스 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(CONTENT_404_001)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    private NewsExplanationQueryService stubReturning(NewsExplanationCardsResponse response) {
        return new NewsExplanationQueryService(null, null) {
            @Override
            public NewsExplanationCardsResponse getExplanationCards(Long newsId) {
                return response;
            }
        };
    }

    private NewsExplanationQueryService stubThrowing(NewsErrorCode errorCode) {
        return new NewsExplanationQueryService(null, null) {
            @Override
            public NewsExplanationCardsResponse getExplanationCards(Long newsId) {
                throw new NewsException(errorCode);
            }
        };
    }

    private MockMvc mockMvcOf(
            NewsExplanationQueryService stubService, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new NewsController(null, stubService))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
