package com.muffin.auth.presentation.emailverification;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.application.emailverification.EmailVerificationCommandService;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.global.apiPayload.handler.GeneralExceptionAdvice;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 이메일 인증번호 발송/확인 API의 REST Docs 스니펫을 생성한다(성공/대표 실패 케이스). */
@ExtendWith(RestDocumentationExtension.class)
class EmailVerificationControllerDocsTest {

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
    @DisplayName("발송 성공 문서화")
    void documentSendSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        EmailVerificationCommandService stub = new EmailVerificationCommandService(null, null, null, null, null, null) {
            @Override
            public long sendCode(Long userId) {
                return 300L;
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/email/verification"))
                .andExpect(status().isOk())
                .andDo(document(
                        "email-verification-send-success",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("result.expiresIn").description("인증번호 만료까지 남은 시간(초)"))));
    }

    @Test
    @DisplayName("발송 실패(이미 인증된 이메일) 문서화")
    void documentSendAlreadyVerified(RestDocumentationContextProvider restDocumentation) throws Exception {
        EmailVerificationCommandService stub = new EmailVerificationCommandService(null, null, null, null, null, null) {
            @Override
            public long sendCode(Long userId) {
                throw new AuthException(AuthErrorCode.EMAIL_ALREADY_VERIFIED);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/email/verification"))
                .andExpect(status().isConflict())
                .andDo(document(
                        "email-verification-send-already-verified",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_409_002)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("확인 성공 문서화")
    void documentVerifySuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        EmailVerificationCommandService stub = new EmailVerificationCommandService(null, null, null, null, null, null) {
            @Override
            public void verifyCode(Long userId, String code) {}
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/email/verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "email-verification-confirm-success",
                        requestFields(fieldWithPath("code").description("사용자가 입력한 인증번호")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"))));
    }

    @Test
    @DisplayName("확인 실패(코드 불일치) 문서화")
    void documentVerifyMismatch(RestDocumentationContextProvider restDocumentation) throws Exception {
        EmailVerificationCommandService stub = new EmailVerificationCommandService(null, null, null, null, null, null) {
            @Override
            public void verifyCode(Long userId, String code) {
                throw new AuthException(AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/email/verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andDo(document(
                        "email-verification-confirm-mismatch",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_400_001)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    private MockMvc mockMvcOf(
            EmailVerificationCommandService stub, RestDocumentationContextProvider restDocumentation) {
        return MockMvcBuilders.standaloneSetup(new EmailVerificationController(stub))
                .setControllerAdvice(new GeneralExceptionAdvice())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
