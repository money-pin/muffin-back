package com.muffin.scrap.presentation;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.scrap.application.ScrapCommandService;
import com.muffin.scrap.presentation.dto.ScrapResponse;
import java.time.OffsetDateTime;
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

/** CONTENT-06 뉴스 스크랩/해제 API의 REST Docs 스니펫을 생성한다(스크랩/해제 성공 + 404/403 실패). */
@ExtendWith(RestDocumentationExtension.class)
class ScrapControllerDocsTest {

    private static final long NEWS_ID = 1024L;

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
    @DisplayName("스크랩 성공 응답 문서화")
    void documentScrap(RestDocumentationContextProvider restDocumentation) throws Exception {
        ScrapResponse response = ScrapResponse.scrapped(NEWS_ID, OffsetDateTime.parse("2026-05-08T14:30:00+09:00"));
        MockMvc mockMvc = mockMvcWith(stubReturning(response), restDocumentation);

        mockMvc.perform(put("/api/news/{newsId}/scrap", NEWS_ID))
                .andExpect(status().isOk())
                .andDo(document(
                        "scrap-create",
                        pathParameters(parameterWithName("newsId").description("스크랩할 뉴스 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.newsId").description("대상 뉴스 ID"),
                                fieldWithPath("result.isScrapped").description("스크랩 상태(항상 true)"),
                                fieldWithPath("result.scrappedAt")
                                        .description("최초 스크랩 시각(KST). 이미 스크랩한 뉴스면 최초 저장 시각"))));
    }

    @Test
    @DisplayName("스크랩 해제 성공 응답 문서화")
    void documentUnscrap(RestDocumentationContextProvider restDocumentation) throws Exception {
        ScrapResponse response = ScrapResponse.unscrapped(NEWS_ID);
        MockMvc mockMvc = mockMvcWith(stubReturning(response), restDocumentation);

        mockMvc.perform(delete("/api/news/{newsId}/scrap", NEWS_ID))
                .andExpect(status().isOk())
                .andDo(document(
                        "scrap-delete",
                        pathParameters(parameterWithName("newsId").description("스크랩 해제할 뉴스 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.newsId").description("대상 뉴스 ID"),
                                fieldWithPath("result.isScrapped").description("스크랩 상태(항상 false)"))));
    }

    @Test
    @DisplayName("존재하지 않는 뉴스 스크랩 시 404(CONTENT_404_001) 문서화")
    void documentScrapNotFound(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(stubThrowing(NewsErrorCode.NEWS_NOT_FOUND), restDocumentation);

        mockMvc.perform(put("/api/news/{newsId}/scrap", NEWS_ID))
                .andExpect(status().isNotFound())
                .andDo(document(
                        "scrap-not-found",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(CONTENT_404_001)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("상세 원인"))));
    }

    @Test
    @DisplayName("아직 공개되지 않은 뉴스 스크랩 시 403(CONTENT_403_001) 문서화")
    void documentScrapNotPublished(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(stubThrowing(NewsErrorCode.NEWS_NOT_PUBLISHED), restDocumentation);

        mockMvc.perform(put("/api/news/{newsId}/scrap", NEWS_ID))
                .andExpect(status().isForbidden())
                .andDo(document(
                        "scrap-not-published",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(CONTENT_403_001)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("상세 원인"))));
    }

    private ScrapCommandService stubReturning(ScrapResponse response) {
        return new ScrapCommandService(null, null, null, null) {
            @Override
            public ScrapResponse scrap(Long userId, Long newsId) {
                return response;
            }

            @Override
            public ScrapResponse unscrap(Long userId, Long newsId) {
                return response;
            }
        };
    }

    private ScrapCommandService stubThrowing(NewsErrorCode errorCode) {
        return new ScrapCommandService(null, null, null, null) {
            @Override
            public ScrapResponse scrap(Long userId, Long newsId) {
                throw new NewsException(errorCode);
            }
        };
    }

    private MockMvc mockMvcWith(ScrapCommandService stubService, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new ScrapController(stubService))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
