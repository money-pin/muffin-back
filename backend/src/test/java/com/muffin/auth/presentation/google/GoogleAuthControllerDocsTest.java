package com.muffin.auth.presentation.google;

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
import com.muffin.auth.application.exception.AuthErrorCode;
import com.muffin.auth.application.google.GoogleAuthCommandService;
import com.muffin.auth.presentation.RefreshTokenCookieHelper;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 구글 OAuth 통합 API의 REST Docs 스니펫을 생성한다(성공/대표 실패 케이스). */
@ExtendWith(RestDocumentationExtension.class)
class GoogleAuthControllerDocsTest {

    private final RefreshTokenCookieHelper refreshTokenCookieHelper =
            new RefreshTokenCookieHelper(new JwtProperties("test-secret", 60, 30));

    @Test
    @DisplayName("구글 로그인/가입 성공 문서화")
    void documentGoogleAuthSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        GoogleAuthCommandService stub = new GoogleAuthCommandService(null, null, null, null, null, null) {
            @Override
            public TokenPair authenticate(String idToken, boolean termsAgreed) {
                return new TokenPair("access-token-example", "refresh-token-example");
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-id-token-example\",\"termsAgreed\":true}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "google-auth-success",
                        requestFields(
                                fieldWithPath("idToken").description("구글 Sign-In SDK가 발급한 ID Token"),
                                fieldWithPath("termsAgreed").description("약관 동의 여부(신규 가입일 때만 검사)")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.accessToken").description("발급된 access token"))));
    }

    @Test
    @DisplayName("구글 가입 실패(약관 미동의) 문서화")
    void documentGoogleAuthTermsNotAgreed(RestDocumentationContextProvider restDocumentation) throws Exception {
        GoogleAuthCommandService stub = new GoogleAuthCommandService(null, null, null, null, null, null) {
            @Override
            public TokenPair authenticate(String idToken, boolean termsAgreed) {
                throw new GeneralException(AuthErrorCode.TERMS_NOT_AGREED);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-id-token-example\",\"termsAgreed\":false}"))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "google-auth-terms-not-agreed",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_400_002)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("구글 인증 실패(유효하지 않은 ID Token) 문서화")
    void documentGoogleAuthInvalidToken(RestDocumentationContextProvider restDocumentation) throws Exception {
        GoogleAuthCommandService stub = new GoogleAuthCommandService(null, null, null, null, null, null) {
            @Override
            public TokenPair authenticate(String idToken, boolean termsAgreed) {
                throw new GeneralException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"bogus-token\",\"termsAgreed\":true}"))
                .andExpect(status().isUnauthorized())
                .andDo(document(
                        "google-auth-invalid-token",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_401_004)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    private MockMvc mockMvcOf(GoogleAuthCommandService stub, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new GoogleAuthController(stub, refreshTokenCookieHelper))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
