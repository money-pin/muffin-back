package com.muffin.auth.presentation;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.auth.domain.EmailVerification;
import com.muffin.auth.domain.EmailVerificationRepository;
import com.muffin.auth.domain.PasswordEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 이메일 인증번호 발송/확인 API가 실제 서비스/저장소까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmailVerificationControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        emailVerificationRepository.deleteAll();
        authRepository.deleteAll();
    }

    @Test
    @DisplayName("발송: 신규 이메일이면 200 성공")
    void sendCode_success() throws Exception {
        String body = objectMapper.writeValueAsString(new SendRequestBody("new@example.com"));

        mockMvc.perform(post("/api/auth/email/verification")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess", is(true)));
    }

    @Test
    @DisplayName("발송: 이메일 형식이 아니면 400 검증 에러")
    void sendCode_invalidEmailFormat() throws Exception {
        String body = objectMapper.writeValueAsString(new SendRequestBody("not-an-email"));

        mockMvc.perform(post("/api/auth/email/verification")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_400_002")));
    }

    @Test
    @DisplayName("발송: 이미 가입된 이메일이면 409")
    void sendCode_duplicateEmail() throws Exception {
        authRepository.save(Auth.createLocal(1L, "used@example.com", "password1", "encoded"));
        String body = objectMapper.writeValueAsString(new SendRequestBody("used@example.com"));

        mockMvc.perform(post("/api/auth/email/verification")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("AUTH_409_001")));
    }

    @Test
    @DisplayName("확인: 발송된 코드와 일치하면 200 성공")
    void verifyCode_success() throws Exception {
        emailVerificationRepository.save(
                EmailVerification.create("verify@example.com", passwordEncoder.encode("123456"), 5));
        String body = objectMapper.writeValueAsString(new ConfirmRequestBody("verify@example.com", "123456"));

        mockMvc.perform(post("/api/auth/email/verification/confirm")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess", is(true)));
    }

    @Test
    @DisplayName("확인: 코드가 다르면 400 (AUTH_400_001)")
    void verifyCode_mismatch() throws Exception {
        emailVerificationRepository.save(
                EmailVerification.create("verify2@example.com", passwordEncoder.encode("123456"), 5));
        String body = objectMapper.writeValueAsString(new ConfirmRequestBody("verify2@example.com", "000000"));

        mockMvc.perform(post("/api/auth/email/verification/confirm")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("AUTH_400_001")));
    }

    private record SendRequestBody(String email) {}

    private record ConfirmRequestBody(String email, String code) {}
}
