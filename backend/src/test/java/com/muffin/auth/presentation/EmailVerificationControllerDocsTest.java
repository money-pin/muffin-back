package com.muffin.auth.presentation;

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.application.EmailVerificationCommandService;
import com.muffin.auth.application.exception.AuthErrorCode;
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

/** 이메일 인증번호 발송/확인 API의 REST Docs 스니펫을 생성한다(성공/대표 실패 케이스). */
@ExtendWith(RestDocumentationExtension.class)
class EmailVerificationControllerDocsTest {

    @Test
    @DisplayName("발송 성공 문서화")
    void documentSendSuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        EmailVerificationCommandService stub = new EmailVerificationCommandService(null, null, null, null, null, null) {
            @Override
            public void sendCode(String email) {}
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "email-verification-send-success",
                        requestFields(fieldWithPath("email").description("인증번호를 받을 이메일")),
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드"),
                                fieldWithPath("message").description("응답 메시지"))));
    }

    @Test
    @DisplayName("발송 실패(이미 사용 중인 이메일) 문서화")
    void documentSendDuplicate(RestDocumentationContextProvider restDocumentation) throws Exception {
        EmailVerificationCommandService stub = new EmailVerificationCommandService(null, null, null, null, null, null) {
            @Override
            public void sendCode(String email) {
                throw new GeneralException(AuthErrorCode.EMAIL_ALREADY_IN_USE);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\"}"))
                .andExpect(status().isConflict())
                .andDo(document(
                        "email-verification-send-duplicate",
                        responseFields(
                                fieldWithPath("isSuccess").description("성공 여부"),
                                fieldWithPath("code").description("응답 코드(AUTH_409_001)"),
                                fieldWithPath("message").description("응답 메시지"),
                                fieldWithPath("errorDetail").description("에러 상세 메시지 목록"))));
    }

    @Test
    @DisplayName("확인 성공 문서화")
    void documentVerifySuccess(RestDocumentationContextProvider restDocumentation) throws Exception {
        EmailVerificationCommandService stub = new EmailVerificationCommandService(null, null, null, null, null, null) {
            @Override
            public void verifyCode(String email, String code) {}
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/email/verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andDo(document(
                        "email-verification-confirm-success",
                        requestFields(
                                fieldWithPath("email").description("인증번호를 발송받은 이메일"),
                                fieldWithPath("code").description("사용자가 입력한 인증번호")),
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
            public void verifyCode(String email, String code) {
                throw new GeneralException(AuthErrorCode.EMAIL_VERIFICATION_CODE_MISMATCH);
            }
        };
        MockMvc mockMvc = mockMvcOf(stub, restDocumentation);

        mockMvc.perform(post("/api/auth/email/verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"code\":\"000000\"}"))
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
                .apply(documentationConfiguration(restDocumentation)
                        .operationPreprocessors()
                        .withRequestDefaults(prettyPrint())
                        .withResponseDefaults(prettyPrint()))
                .build();
    }
}
