package com.muffin.mypage.presentation;

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
import com.muffin.mypage.application.MypageScrapQueryService;
import com.muffin.mypage.domain.exception.MypageException;
import com.muffin.mypage.domain.exception.code.MypageErrorCode;
import com.muffin.mypage.presentation.dto.ScrapListResponse;
import com.muffin.mypage.presentation.dto.ScrapListResponse.ScrapItem;
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

/** MYPAGE-04-1 스크랩한 뉴스 목록 조회 API의 REST Docs 스니펫을 생성한다(정상/빈 상태/400/404). */
@ExtendWith(RestDocumentationExtension.class)
class MypageControllerDocsTest {

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
    @DisplayName("스크랩 목록 정상 응답 문서화")
    void documentScrapList(RestDocumentationContextProvider restDocumentation) throws Exception {
        ScrapListResponse response = new ScrapListResponse(
                List.of(new ScrapItem(
                        1024L,
                        "미국 반도체 수출 규제 완화",
                        "반도체",
                        "https://.../thumb.jpg",
                        3120L,
                        OffsetDateTime.parse("2026-05-07T09:00:00+09:00"),
                        OffsetDateTime.parse("2026-05-08T14:30:00+09:00"))),
                "eyJzIjoiU0FWRURfREVTQyJ9",
                true);
        MockMvc mockMvc = mockMvcWith(stubReturning(response), restDocumentation);

        mockMvc.perform(get("/api/mypage/scraps").param("sort", "SAVED_DESC").param("size", "20"))
                .andExpect(status().isOk())
                .andDo(document(
                        "scrap-list",
                        queryParameters(
                                parameterWithName("sort")
                                        .optional()
                                        .description("정렬. SAVED_DESC/PUBLISHED_DESC/VIEW_DESC (기본 SAVED_DESC)"),
                                parameterWithName("cursor").optional().description("이전 응답의 nextCursor"),
                                parameterWithName("size").optional().description("페이지 크기 (1~50, 기본 10)")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.items[].newsId").description("뉴스 ID"),
                                fieldWithPath("result.items[].title").description("뉴스 제목"),
                                fieldWithPath("result.items[].categoryName").description("카테고리 표시명"),
                                fieldWithPath("result.items[].thumbnailUrl").description("썸네일 URL"),
                                fieldWithPath("result.items[].viewCount").description("조회수(VIEW_DESC 정렬 기준)"),
                                fieldWithPath("result.items[].publishedAt")
                                        .description("발행 시각(KST, PUBLISHED_DESC 정렬 기준)"),
                                fieldWithPath("result.items[].scrappedAt").description("스크랩 시각(KST, SAVED_DESC 정렬 기준)"),
                                fieldWithPath("result.nextCursor").description("다음 페이지 커서. 다음 페이지가 없으면 null"),
                                fieldWithPath("result.hasNext").description("다음 페이지 존재 여부"))));
    }

    @Test
    @DisplayName("스크랩이 없는 빈 상태 응답 문서화")
    void documentEmptyScrapList(RestDocumentationContextProvider restDocumentation) throws Exception {
        ScrapListResponse response = new ScrapListResponse(List.of(), null, false);
        MockMvc mockMvc = mockMvcWith(stubReturning(response), restDocumentation);

        mockMvc.perform(get("/api/mypage/scraps"))
                .andExpect(status().isOk())
                .andDo(document(
                        "scrap-list-empty",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.items").description("빈 배열(스크랩 없음)"),
                                fieldWithPath("result.nextCursor").description("다음 페이지가 없어 null"),
                                fieldWithPath("result.hasNext").description("다음 페이지 존재 여부(false)"))));
    }

    @Test
    @DisplayName("허용되지 않는 요청 값이면 400(MYPAGE_400_004) 문서화")
    void documentInvalidRequest(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(stubThrowing(MypageErrorCode.INVALID_PAGE_REQUEST), restDocumentation);

        mockMvc.perform(get("/api/mypage/scraps").param("sort", "LATEST"))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "scrap-list-invalid",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(MYPAGE_400_004)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("허용값/입력값 등 상세 원인"))));
    }

    @Test
    @DisplayName("사용자 정보를 찾을 수 없으면 404(MYPAGE_404_001) 문서화")
    void documentUserNotFound(RestDocumentationContextProvider restDocumentation) throws Exception {
        MockMvc mockMvc = mockMvcWith(stubThrowing(MypageErrorCode.USER_NOT_FOUND), restDocumentation);

        mockMvc.perform(get("/api/mypage/scraps"))
                .andExpect(status().isNotFound())
                .andDo(document(
                        "scrap-list-user-not-found",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부(false)"),
                                fieldWithPath("code").description("에러 코드(MYPAGE_404_001)"),
                                fieldWithPath("message").description("에러 메시지"),
                                fieldWithPath("errorDetail").description("상세 원인"))));
    }

    private MypageScrapQueryService stubReturning(ScrapListResponse response) {
        return new MypageScrapQueryService(null, null, null, null) {
            @Override
            public ScrapListResponse getScraps(Long userId, String sortParam, String cursorParam, int size) {
                return response;
            }
        };
    }

    private MypageScrapQueryService stubThrowing(MypageErrorCode errorCode) {
        return new MypageScrapQueryService(null, null, null, null) {
            @Override
            public ScrapListResponse getScraps(Long userId, String sortParam, String cursorParam, int size) {
                throw new MypageException(errorCode, "detail");
            }
        };
    }

    private MockMvc mockMvcWith(
            MypageScrapQueryService stubService, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new MypageController(stubService, null, null, null))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
