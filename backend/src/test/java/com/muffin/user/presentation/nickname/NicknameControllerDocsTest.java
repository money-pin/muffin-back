package com.muffin.user.presentation.nickname;

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
import com.muffin.user.application.nickname.NicknameQueryService;
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

/** 닉네임 중복 조회 API의 REST Docs 스니펫을 생성한다(사용 가능/이미 사용 중 두 케이스). */
@ExtendWith(RestDocumentationExtension.class)
class NicknameControllerDocsTest {

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
    @DisplayName("사용 가능한 닉네임 조회 문서화")
    void documentAvailable(RestDocumentationContextProvider restDocumentation) throws Exception {
        NicknameQueryService stub = new NicknameQueryService(null) {
            @Override
            public boolean isAvailable(String nickname) {
                return true;
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(get("/api/users/nickname/check").param("nickname", "길동이"))
                .andExpect(status().isOk())
                .andDo(document(
                        "nickname-check-available",
                        queryParameters(parameterWithName("nickname").description("중복 확인할 닉네임")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.available").description("사용 가능 여부(true=사용 가능)"))));
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임 조회 문서화")
    void documentAlreadyTaken(RestDocumentationContextProvider restDocumentation) throws Exception {
        NicknameQueryService stub = new NicknameQueryService(null) {
            @Override
            public boolean isAvailable(String nickname) {
                return false;
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(get("/api/users/nickname/check").param("nickname", "길동이"))
                .andExpect(status().isOk())
                .andDo(document(
                        "nickname-check-taken",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.available").description("사용 가능 여부(false=이미 사용 중)"))));
    }

    private MockMvc mockMvcOf(NicknameQueryService stub, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new NicknameController(stub))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
