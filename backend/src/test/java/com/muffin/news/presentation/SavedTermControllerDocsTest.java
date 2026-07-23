package com.muffin.news.presentation;

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
import com.muffin.news.application.term.TermQueryService;
import com.muffin.news.presentation.dto.response.SavedTermListResponse;
import com.muffin.news.presentation.dto.response.SavedTermListResponse.SavedTermItem;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Pageable;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 저장한 용어 목록 조회 API의 REST Docs 스니펫을 생성한다. */
@ExtendWith(RestDocumentationExtension.class)
class SavedTermControllerDocsTest {

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
    @DisplayName("저장한 용어 목록 조회 문서화")
    void documentGetSavedTerms(RestDocumentationContextProvider restDocumentation) throws Exception {
        TermQueryService stub = new TermQueryService(null, null, null) {
            @Override
            public SavedTermListResponse getSavedTerms(Long userId, Pageable pageable, String sortParam) {
                return new SavedTermListResponse(
                        List.of(
                                new SavedTermItem(12L, "기준금리", "중앙은행이 금융기관과 거래할 때 기준이 되는 금리입니다.", LocalDateTime.now()),
                                new SavedTermItem(
                                        21L, "ETF", "특정 지수나 자산의 가격 움직임을 따라가도록 설계된 상장지수펀드입니다.", LocalDateTime.now())),
                        0,
                        20,
                        false);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(get("/api/mypage/saved-terms")
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "recent"))
                .andExpect(status().isOk())
                .andDo(document(
                        "mypage-saved-terms-get",
                        queryParameters(
                                parameterWithName("page").description("페이지 번호(0부터 시작, 기본 0)"),
                                parameterWithName("size").description("페이지 크기(최대 50, 기본 20)"),
                                parameterWithName("sort")
                                        .description("정렬 기준: recent(기본, 최근 저장순) 또는 alphabetical(가나다순)")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.savedTerms[].termId").description("용어 ID"),
                                fieldWithPath("result.savedTerms[].term").description("용어명"),
                                fieldWithPath("result.savedTerms[].content").description("용어 설명"),
                                fieldWithPath("result.savedTerms[].savedAt").description("저장 시각"),
                                fieldWithPath("result.page").description("현재 페이지 번호"),
                                fieldWithPath("result.size").description("페이지 크기"),
                                fieldWithPath("result.hasNext").description("다음 페이지 존재 여부"))));
    }

    private MockMvc mockMvcOf(TermQueryService stub, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new SavedTermController(stub))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
