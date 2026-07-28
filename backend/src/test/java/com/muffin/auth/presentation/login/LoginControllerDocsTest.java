package com.muffin.auth.presentation.login;

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
import com.muffin.auth.application.login.LoginCommandService;
import com.muffin.auth.presentation.AuthController;
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

/** 로컬 로그인 API의 REST Docs 스니펫을 생성한다(성공/대표 실패 케이스). */
@ExtendWith(RestDocumentationExtension.class)
class LoginControllerDocsTest {

    private final RefreshTokenCookieHelper refreshTokenCookieHelper =
            new RefreshTokenCookieHelper(new JwtProperties("test-secret", 60, 30));

    @Test
    @DisplayName("로그인 성공 문서화")
    void documentLoginSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        LoginCommandService stub = new LoginCommandService(null, null, null, null, null, null) {
            @Override
            public TokenPair loginLocal(String email, String rawPassword) {
                return new TokenPair("access-token-example", "refresh-token-example");
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password1\"}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "login-local-success",
                        requestFields(
                                fieldWithPath("email").description("가입 시 사용한 이메일"),
                                fieldWithPath("password").description("비밀번호")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.accessToken").description("발급된 access token"))));
    }

    @Test
    @DisplayName("로그인 실패(이메일/비밀번호 불일치) 문서화")
    void documentLoginInvalidCredentials(RestDocumentationContextProvider restDocumentation) throws Exception {
        LoginCommandService stub = new LoginCommandService(null, null, null, null, null, null) {
            @Override
            public TokenPair loginLocal(String email, String rawPassword) {
                throw new GeneralException(AuthErrorCode.INVALID_CREDENTIALS);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"wrongpass1\"}"))
                .andExpect(status().isUnauthorized())
                .andDo(document(
                        "login-local-invalid-credentials",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_401_002)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("로그인 실패(탈퇴한 계정) 문서화")
    void documentLoginWithdrawnAccount(RestDocumentationContextProvider restDocumentation) throws Exception {
        LoginCommandService stub = new LoginCommandService(null, null, null, null, null, null) {
            @Override
            public TokenPair loginLocal(String email, String rawPassword) {
                throw new GeneralException(AuthErrorCode.WITHDRAWN_ACCOUNT);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password1\"}"))
                .andExpect(status().isForbidden())
                .andDo(document(
                        "login-local-withdrawn-account",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_403_002)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("로그인 실패(잠금) 문서화")
    void documentLoginLocked(RestDocumentationContextProvider restDocumentation) throws Exception {
        LoginCommandService stub = new LoginCommandService(null, null, null, null, null, null) {
            @Override
            public TokenPair loginLocal(String email, String rawPassword) {
                throw new GeneralException(AuthErrorCode.LOGIN_LOCKED);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"password1\"}"))
                .andExpect(status().isLocked())
                .andDo(document(
                        "login-local-locked",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_423_002)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    private MockMvc mockMvcOf(LoginCommandService stub, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(
                        new AuthController(null, stub, null, null, null, null, refreshTokenCookieHelper))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
