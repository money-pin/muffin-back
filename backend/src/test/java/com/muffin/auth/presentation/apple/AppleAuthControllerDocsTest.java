package com.muffin.auth.presentation.apple;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.application.JwtProperties;
import com.muffin.auth.application.TokenPair;
import com.muffin.auth.application.social.SocialAuthCommandService;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.auth.presentation.AuthController;
import com.muffin.auth.presentation.RefreshTokenCookieHelper;
import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 애플 로그인 API의 REST Docs 스니펫을 생성한다(성공/대표 실패 케이스). */
@ExtendWith(RestDocumentationExtension.class)
class AppleAuthControllerDocsTest {

    private final RefreshTokenCookieHelper refreshTokenCookieHelper =
            new RefreshTokenCookieHelper(new JwtProperties("test-secret", 60, 30));

    @Test
    @DisplayName("애플 로그인/가입 성공 문서화")
    void documentAppleAuthSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        SocialAuthCommandService stub = new SocialAuthCommandService(null, null, null, null, null, null, null, null) {
            @Override
            public TokenPair authenticate(AuthProvider provider, String idToken, String nameHint) {
                return new TokenPair("access-token-example", "refresh-token-example");
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(
                        post("/auth/apple")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"idToken\":\"apple-identity-token-example\",\"givenName\":\"길동\",\"familyName\":\"홍\"}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "apple-auth-success",
                        requestFields(
                                fieldWithPath("idToken").description("애플 SDK가 발급한 identity token"),
                                fieldWithPath("givenName").optional().description("이름. 애플이 최초 로그인 때만 제공하므로 이후에는 생략"),
                                fieldWithPath("familyName").optional().description("성. 애플이 최초 로그인 때만 제공하므로 이후에는 생략")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.accessToken").description("발급된 access token"))));
    }

    @Test
    @DisplayName("애플 인증 실패(유효하지 않은 ID Token) 문서화")
    void documentAppleAuthInvalidToken(RestDocumentationContextProvider restDocumentation) throws Exception {
        SocialAuthCommandService stub = new SocialAuthCommandService(null, null, null, null, null, null, null, null) {
            @Override
            public TokenPair authenticate(AuthProvider provider, String idToken, String nameHint) {
                throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"bogus-token\"}"))
                .andExpect(status().isUnauthorized())
                .andDo(document(
                        "apple-auth-invalid-token",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_401_004)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    private MockMvc mockMvcOf(SocialAuthCommandService stub, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new AuthController(stub, null, null, null, refreshTokenCookieHelper))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
