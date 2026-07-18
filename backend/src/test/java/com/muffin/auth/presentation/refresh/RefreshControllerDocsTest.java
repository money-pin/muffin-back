package com.muffin.auth.presentation.refresh;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.application.JwtProperties;
import com.muffin.auth.application.TokenPair;
import com.muffin.auth.application.exception.AuthErrorCode;
import com.muffin.auth.application.refresh.RefreshCommandService;
import com.muffin.auth.presentation.RefreshTokenCookieHelper;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Access/Refresh Token 재발급 API의 REST Docs 스니펫을 생성한다(성공/대표 실패 케이스). */
@ExtendWith(RestDocumentationExtension.class)
class RefreshControllerDocsTest {

    private final RefreshTokenCookieHelper refreshTokenCookieHelper =
            new RefreshTokenCookieHelper(new JwtProperties("test-secret", 60, 30));

    @Test
    @DisplayName("재발급 성공 문서화")
    void documentRefreshSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        RefreshCommandService stub = new RefreshCommandService(null, null, null) {
            @Override
            public TokenPair refresh(String rawRefreshToken) {
                return new TokenPair("access-token-example", "refresh-token-example");
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/token/refresh").cookie(new Cookie("refreshToken", "old-refresh-token-example")))
                .andExpect(status().isOk())
                .andDo(document(
                        "token-refresh-success",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.accessToken").description("새로 발급된 access token"))));
    }

    @Test
    @DisplayName("재발급 실패(refresh token 쿠키 없음) 문서화")
    void documentRefreshMissingCookie(RestDocumentationContextProvider restDocumentation) throws Exception {
        RefreshCommandService stub = new RefreshCommandService(null, null, null);
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/token/refresh"))
                .andExpect(status().isUnauthorized())
                .andDo(document(
                        "token-refresh-missing-cookie",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_401_003)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("재발급 실패(유효하지 않은 refresh token) 문서화")
    void documentRefreshInvalidToken(RestDocumentationContextProvider restDocumentation) throws Exception {
        RefreshCommandService stub = new RefreshCommandService(null, null, null) {
            @Override
            public TokenPair refresh(String rawRefreshToken) {
                throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/token/refresh").cookie(new Cookie("refreshToken", "bogus-token")))
                .andExpect(status().isUnauthorized())
                .andDo(document(
                        "token-refresh-invalid",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_401_003)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    private MockMvc mockMvcOf(RefreshCommandService stub, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new RefreshController(stub, refreshTokenCookieHelper))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
