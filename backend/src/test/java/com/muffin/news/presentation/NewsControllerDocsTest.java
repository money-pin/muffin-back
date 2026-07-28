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
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import com.muffin.news.application.exception.NewsErrorCode;
import com.muffin.news.application.exception.NewsException;
import com.muffin.news.application.query.NewsQueryService;
import com.muffin.news.domain.sectorimpact.enums.ImpactType;
import com.muffin.news.presentation.dto.NewsDetailResponse;
import com.muffin.news.presentation.dto.NewsDetailResponse.BodySegment;
import com.muffin.news.presentation.dto.NewsListResponse;
import com.muffin.news.presentation.dto.NewsListResponse.NewsListItem;
import com.muffin.news.presentation.dto.NewsSectorImpactResponse;
import com.muffin.news.presentation.dto.NewsSectorImpactResponse.SectorImpactItem;
import com.muffin.news.presentation.dto.NewsTodayResponse;
import com.muffin.news.presentation.dto.NewsTodayResponse.NewsTodayItem;
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

/** 뉴스 목록/오늘/상세/섹터 영향도 조회 API의 REST Docs 스니펫을 생성한다. */
@ExtendWith(RestDocumentationExtension.class)
class NewsControllerDocsTest {

    private static final String AUTHORIZATION = "Bearer {accessToken}";
    private static final LocalDateTime PUBLISHED_AT = LocalDateTime.of(2026, 7, 24, 9, 0);

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
    @DisplayName("전체 뉴스 목록 조회 문서화")
    void documentNewsList(RestDocumentationContextProvider restDocumentation) throws Exception {
        NewsListResponse response = new NewsListResponse(
                List.of(new NewsListItem(
                        101L,
                        1L,
                        "경제",
                        "기준금리 동결, 시장 전망은",
                        "한국은행이 기준금리를 동결했습니다.",
                        "매일경제",
                        PUBLISHED_AT,
                        "https://cdn.example.com/news/101.jpg",
                        1250L)),
                "eyJwdWJsaXNoZWRBdCI6IjIwMjYtMDctMjRUMDk6MDA6MDAiLCJuZXdzSWQiOjEwMX0",
                true);
        MockMvc mockMvc = mockMvcWith(stubNewsList(response), restDocumentation);

        mockMvc.perform(get("/api/news").param("size", "20").param("categoryId", "1"))
                .andExpect(status().isOk())
                .andDo(document(
                        "news-list-success",
                        queryParameters(
                                parameterWithName("cursor").optional().description("이전 응답의 nextCursor. 최초 요청 시 생략"),
                                parameterWithName("size").optional().description("페이지 크기 (1~50, 기본 20)"),
                                parameterWithName("categoryId").optional().description("카테고리 ID. 생략하면 전체 카테고리 조회")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.items[].newsId").description("뉴스 ID"),
                                fieldWithPath("result.items[].categoryId").description("카테고리 ID"),
                                fieldWithPath("result.items[].categoryName").description("카테고리 표시명"),
                                fieldWithPath("result.items[].title").description("뉴스 제목"),
                                fieldWithPath("result.items[].summary").description("뉴스 요약"),
                                fieldWithPath("result.items[].publisher").description("언론사"),
                                fieldWithPath("result.items[].publishedAt").description("발행 시각"),
                                fieldWithPath("result.items[].thumbnailUrl").description("원본 썸네일 URL. 없으면 null"),
                                fieldWithPath("result.items[].viewCount").description("조회수"),
                                fieldWithPath("result.nextCursor").optional().description("다음 페이지 커서. 다음 페이지가 없으면 생략"),
                                fieldWithPath("result.hasNext").description("다음 페이지 존재 여부"))));
    }

    @Test
    @DisplayName("전체 뉴스 목록 잘못된 페이지 크기 문서화")
    void documentNewsListInvalidSize(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(stubInvalidNewsList(), restDocumentation);

        mockMvc.perform(get("/api/news").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "news-list-invalid",
                        queryParameters(parameterWithName("size").description("허용 범위를 벗어난 페이지 크기")),
                        errorResponseFields("COMMON_400_001")));
    }

    @Test
    @DisplayName("오늘의 뉴스 조회 문서화")
    void documentTodayNews(RestDocumentationContextProvider restDocumentation) throws Exception {
        NewsTodayResponse response = new NewsTodayResponse(List.of(new NewsTodayItem(
                102L,
                2L,
                "증권",
                "오늘의 증시 주요 뉴스",
                "국내 증시의 주요 흐름을 정리했습니다.",
                "매일경제",
                PUBLISHED_AT,
                "https://cdn.example.com/news/102.jpg",
                980L)));
        MockMvc mockMvc = mockMvcWith(stubTodayNews(response), restDocumentation);

        mockMvc.perform(get("/api/news/today").header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "news-today-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.items[].newsId").description("뉴스 ID"),
                                fieldWithPath("result.items[].categoryId").description("카테고리 ID"),
                                fieldWithPath("result.items[].categoryName").description("카테고리 표시명"),
                                fieldWithPath("result.items[].title").description("뉴스 제목"),
                                fieldWithPath("result.items[].summary").description("뉴스 요약"),
                                fieldWithPath("result.items[].publisher").description("언론사"),
                                fieldWithPath("result.items[].publishedAt").description("발행 시각"),
                                fieldWithPath("result.items[].thumbnailUrl").description("원본 썸네일 URL. 없으면 null"),
                                fieldWithPath("result.items[].viewCount").description("조회수"))));
    }

    @Test
    @DisplayName("뉴스 상세 조회 문서화")
    void documentNewsDetail(RestDocumentationContextProvider restDocumentation) throws Exception {
        NewsDetailResponse response = new NewsDetailResponse(
                103L,
                103L,
                "환율 변동과 수출 기업",
                "경제",
                431L,
                "매일경제",
                PUBLISHED_AT,
                "https://cdn.example.com/news/103.jpg",
                "https://example.com/articles/103",
                List.of(BodySegment.text("기준금리는 시장 금리에 영향을 줍니다."), BodySegment.highlight("기준금리", 501L)),
                true);
        MockMvc mockMvc = mockMvcWith(stubNewsDetail(response), restDocumentation);

        mockMvc.perform(post("/api/news/{newsSummaryId}", 103L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "news-detail-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("newsSummaryId").description("조회할 뉴스 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.newsSummaryId").description("뉴스 요약 ID(현재 뉴스 ID와 동일)"),
                                fieldWithPath("result.newsId").description("뉴스 ID"),
                                fieldWithPath("result.title").description("뉴스 제목"),
                                fieldWithPath("result.categoryName").description("카테고리 표시명. 없으면 null"),
                                fieldWithPath("result.viewCount").description("상세 조회 반영 후 조회수"),
                                fieldWithPath("result.publisher").description("언론사"),
                                fieldWithPath("result.publishedAt").description("발행 시각"),
                                fieldWithPath("result.thumbnailUrl").description("원본 썸네일 URL. 없으면 null"),
                                fieldWithPath("result.originalUrl").description("원문 URL"),
                                fieldWithPath("result.bodySegments[].type")
                                        .description("본문 세그먼트 타입(TEXT 또는 HIGHLIGHT)"),
                                fieldWithPath("result.bodySegments[].text").description("본문 텍스트"),
                                fieldWithPath("result.bodySegments[].termId")
                                        .optional()
                                        .description("HIGHLIGHT 세그먼트의 용어 ID. TEXT에는 생략"),
                                fieldWithPath("result.isScrapped").description("현재 사용자의 스크랩 여부"))));
    }

    @Test
    @DisplayName("뉴스 상세 조회 비공개 뉴스 오류 문서화")
    void documentNewsDetailNotPublished(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(stubNewsDetailError(NewsErrorCode.NEWS_NOT_PUBLISHED), restDocumentation);

        mockMvc.perform(post("/api/news/{newsSummaryId}", 103L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isForbidden())
                .andDo(document(
                        "news-detail-not-published",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("newsSummaryId").description("조회할 뉴스 ID")),
                        errorResponseFields("CONTENT_403_001")));
    }

    @Test
    @DisplayName("뉴스 상세 조회 미존재 오류 문서화")
    void documentNewsDetailNotFound(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(stubNewsDetailError(NewsErrorCode.NEWS_NOT_FOUND), restDocumentation);

        mockMvc.perform(post("/api/news/{newsSummaryId}", 999L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isNotFound())
                .andDo(document(
                        "news-detail-not-found",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("newsSummaryId").description("조회할 뉴스 ID")),
                        errorResponseFields("CONTENT_404_001")));
    }

    @Test
    @DisplayName("뉴스 섹터별 영향도 조회 문서화")
    void documentSectorImpacts(RestDocumentationContextProvider restDocumentation) throws Exception {
        NewsSectorImpactResponse response = new NewsSectorImpactResponse(
                104L,
                List.of(
                        impact("DEPOSIT", "예금", ImpactType.POSITIVE),
                        impact("GOLD", "금", ImpactType.NEUTRAL),
                        impact("BOND", "채권", ImpactType.NEUTRAL),
                        impact("USD", "달러", ImpactType.NEGATIVE),
                        impact("TECH", "테크", ImpactType.POSITIVE),
                        impact("SEMICONDUCTOR", "반도체", ImpactType.POSITIVE),
                        impact("BIO", "바이오/제약", ImpactType.NEUTRAL),
                        impact("CRYPTO", "코인", ImpactType.NEGATIVE),
                        impact("AUTO", "자동차", ImpactType.NEUTRAL),
                        impact("ENERGY", "에너지", ImpactType.NEUTRAL),
                        impact("FINANCE", "금융", ImpactType.POSITIVE),
                        impact("DEFENSE", "방산", ImpactType.NEUTRAL)));
        MockMvc mockMvc = mockMvcWith(stubSectorImpacts(response), restDocumentation);

        mockMvc.perform(get("/api/news/{newsSummaryId}/sector-impacts", 104L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isOk())
                .andDo(document(
                        "news-sector-impacts-success",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("newsSummaryId").description("조회할 뉴스 ID")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.newsSummaryId").description("뉴스 ID"),
                                fieldWithPath("result.sectorImpacts[]").description("표시 순서로 정렬된 12개 섹터"),
                                fieldWithPath("result.sectorImpacts[].sectorCode")
                                        .description("섹터 코드"),
                                fieldWithPath("result.sectorImpacts[].sectorName")
                                        .description("섹터 표시명"),
                                fieldWithPath("result.sectorImpacts[].impact")
                                        .description("영향도(POSITIVE, NEUTRAL, NEGATIVE)"))));
    }

    @Test
    @DisplayName("뉴스 섹터별 영향도 미존재 오류 문서화")
    void documentSectorImpactsNotFound(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(stubSectorImpactsError(), restDocumentation);

        mockMvc.perform(get("/api/news/{newsSummaryId}/sector-impacts", 999L).header("Authorization", AUTHORIZATION))
                .andExpect(status().isNotFound())
                .andDo(document(
                        "news-sector-impacts-not-found",
                        requestHeaders(headerWithName("Authorization").description("Bearer access token")),
                        pathParameters(parameterWithName("newsSummaryId").description("조회할 뉴스 ID")),
                        errorResponseFields("CONTENT_404_001")));
    }

    private static SectorImpactItem impact(String code, String name, ImpactType impact) {
        return new SectorImpactItem(code, name, impact);
    }

    private static org.springframework.restdocs.payload.ResponseFieldsSnippet errorResponseFields(String code) {
        return responseFields(
                fieldWithPath("isSuccess").description("성공 여부(false)"),
                fieldWithPath("code").description("에러 코드(" + code + ")"),
                fieldWithPath("message").description("에러 메시지"),
                fieldWithPath("errorDetail").description("상세 원인"));
    }

    private NewsQueryService stubNewsList(NewsListResponse response) {
        return stubService(new NewsQueryStub() {
            @Override
            public NewsListResponse getNewsList(String cursor, int size, Long categoryId) {
                return response;
            }
        });
    }

    private NewsQueryService stubInvalidNewsList() {
        return stubService(new NewsQueryStub() {
            @Override
            public NewsListResponse getNewsList(String cursor, int size, Long categoryId) {
                throw new GeneralException(GeneralErrorCode.BAD_REQUEST, "size: 1 이상 50 이하여야 합니다.");
            }
        });
    }

    private NewsQueryService stubTodayNews(NewsTodayResponse response) {
        return stubService(new NewsQueryStub() {
            @Override
            public NewsTodayResponse getTodayNews() {
                return response;
            }
        });
    }

    private NewsQueryService stubNewsDetail(NewsDetailResponse response) {
        return stubService(new NewsQueryStub() {
            @Override
            public NewsDetailResponse getNewsDetail(Long userId, Long newsId) {
                return response;
            }
        });
    }

    private NewsQueryService stubNewsDetailError(NewsErrorCode errorCode) {
        return stubService(new NewsQueryStub() {
            @Override
            public NewsDetailResponse getNewsDetail(Long userId, Long newsId) {
                throw new NewsException(errorCode);
            }
        });
    }

    private NewsQueryService stubSectorImpacts(NewsSectorImpactResponse response) {
        return stubService(new NewsQueryStub() {
            @Override
            public NewsSectorImpactResponse getSectorImpacts(Long newsId) {
                return response;
            }
        });
    }

    private NewsQueryService stubSectorImpactsError() {
        return stubService(new NewsQueryStub() {
            @Override
            public NewsSectorImpactResponse getSectorImpacts(Long newsId) {
                throw new NewsException(NewsErrorCode.NEWS_NOT_FOUND);
            }
        });
    }

    private NewsQueryService stubService(NewsQueryStub stub) {
        return new NewsQueryService(null, null, null, null, null, null, null, null, null) {
            @Override
            public NewsListResponse getNewsList(String cursor, int size, Long categoryId) {
                return stub.getNewsList(cursor, size, categoryId);
            }

            @Override
            public NewsTodayResponse getTodayNews() {
                return stub.getTodayNews();
            }

            @Override
            public NewsDetailResponse getNewsDetail(Long userId, Long newsId) {
                return stub.getNewsDetail(userId, newsId);
            }

            @Override
            public NewsSectorImpactResponse getSectorImpacts(Long newsId) {
                return stub.getSectorImpacts(newsId);
            }
        };
    }

    private MockMvc mockMvcWith(NewsQueryService stubService, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new NewsController(stubService, null))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }

    private abstract static class NewsQueryStub {

        NewsListResponse getNewsList(String cursor, int size, Long categoryId) {
            throw new UnsupportedOperationException();
        }

        NewsTodayResponse getTodayNews() {
            throw new UnsupportedOperationException();
        }

        NewsDetailResponse getNewsDetail(Long userId, Long newsId) {
            throw new UnsupportedOperationException();
        }

        NewsSectorImpactResponse getSectorImpacts(Long newsId) {
            throw new UnsupportedOperationException();
        }
    }
}
