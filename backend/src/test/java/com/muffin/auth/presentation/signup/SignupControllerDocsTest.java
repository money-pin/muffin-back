package com.muffin.auth.presentation.signup;

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
import com.muffin.auth.application.signup.SignupCommandService;
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

/** 로컬 회원가입 API의 REST Docs 스니펫을 생성한다(성공/대표 실패 케이스). */
@ExtendWith(RestDocumentationExtension.class)
class SignupControllerDocsTest {

    private final RefreshTokenCookieHelper refreshTokenCookieHelper =
            new RefreshTokenCookieHelper(new JwtProperties("test-secret", 60, 30));

    @Test
    @DisplayName("회원가입 성공 문서화")
    void documentSignupSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        SignupCommandService stub = new SignupCommandService(null, null, null, null, null, null) {
            @Override
            public TokenPair signupLocal(String email, String rawPassword, String name, boolean termsAgreed) {
                return new TokenPair("access-token-example", "refresh-token-example");
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"user@example.com\",\"password\":\"password1\",\"name\":\"홍길동\",\"termsAgreed\":true}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "signup-local-success",
                        requestFields(
                                fieldWithPath("email").description("로그인에 사용할 이메일"),
                                fieldWithPath("password").description("비밀번호(영문+숫자 8~16자)"),
                                fieldWithPath("name").description("이름"),
                                fieldWithPath("termsAgreed").description("약관 동의 여부")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.accessToken").description("발급된 access token"))));
    }

    @Test
    @DisplayName("회원가입 실패(약관 미동의) 문서화")
    void documentSignupTermsNotAgreed(RestDocumentationContextProvider restDocumentation) throws Exception {
        SignupCommandService stub = new SignupCommandService(null, null, null, null, null, null) {
            @Override
            public TokenPair signupLocal(String email, String rawPassword, String name, boolean termsAgreed) {
                throw new GeneralException(AuthErrorCode.TERMS_NOT_AGREED);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"user@example.com\",\"password\":\"password1\",\"name\":\"홍길동\",\"termsAgreed\":false}"))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "signup-local-terms-not-agreed",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_400_002)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("회원가입 실패(이미 사용 중인 이메일) 문서화")
    void documentSignupDuplicateEmail(RestDocumentationContextProvider restDocumentation) throws Exception {
        SignupCommandService stub = new SignupCommandService(null, null, null, null, null, null) {
            @Override
            public TokenPair signupLocal(String email, String rawPassword, String name, boolean termsAgreed) {
                throw new GeneralException(AuthErrorCode.EMAIL_ALREADY_IN_USE);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"user@example.com\",\"password\":\"password1\",\"name\":\"홍길동\",\"termsAgreed\":true}"))
                .andExpect(status().isConflict())
                .andDo(document(
                        "signup-local-duplicate-email",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_409_001)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("회원가입 실패(최근 탈퇴한 이메일) 문서화")
    void documentSignupRecentlyDeletedEmail(RestDocumentationContextProvider restDocumentation) throws Exception {
        SignupCommandService stub = new SignupCommandService(null, null, null, null, null, null) {
            @Override
            public TokenPair signupLocal(String email, String rawPassword, String name, boolean termsAgreed) {
                throw new GeneralException(AuthErrorCode.RECENTLY_DELETED_EMAIL);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(
                        post("/api/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"user@example.com\",\"password\":\"password1\",\"name\":\"홍길동\",\"termsAgreed\":true}"))
                .andExpect(status().isConflict())
                .andDo(document(
                        "signup-local-recently-deleted-email",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_409_003)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    private MockMvc mockMvcOf(SignupCommandService stub, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new SignupController(stub, refreshTokenCookieHelper))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
