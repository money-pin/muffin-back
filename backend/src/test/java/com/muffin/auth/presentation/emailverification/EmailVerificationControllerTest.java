package com.muffin.auth.presentation.emailverification;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.PasswordEncoder;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.emailverification.EmailVerification;
import com.muffin.auth.domain.emailverification.EmailVerificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 이메일 인증번호 발송/확인 API가 실제 서비스/저장소/Security까지 엮여서 동작하는지 검증한다. */
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

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @AfterEach
    void cleanUp() {
        emailVerificationRepository.deleteAll();
        authRepository.deleteAll();
    }

    private String bearerTokenFor(Long userId) {
        return "Bearer " + accessTokenProvider.issue(userId, "USER");
    }

    @Test
    @DisplayName("발송: 인증되지 않은 계정이면 200 성공, expiresIn 응답")
    void sendCode_success() throws Exception {
        Auth auth = authRepository.save(Auth.createLocal(1L, "new@example.com", "password1", "encoded"));

        mockMvc.perform(post("/api/auth/email/verification").header("Authorization", bearerTokenFor(auth.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess", is(true)))
                .andExpect(jsonPath("$.result.expiresIn").exists());
    }

    @Test
    @DisplayName("발송: Authorization 헤더가 없으면 401")
    void sendCode_unauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/email/verification")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("발송: 이미 인증된 계정이면 409 (AUTH_409_002)")
    void sendCode_alreadyVerified() throws Exception {
        Auth auth = authRepository.save(Auth.createGoogle(1L, "verified@example.com", "google-sub-1"));

        mockMvc.perform(post("/api/auth/email/verification").header("Authorization", bearerTokenFor(auth.getUserId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("AUTH_409_002")));
    }

    @Test
    @DisplayName("확인: 발송된 코드와 일치하면 200 성공")
    void verifyCode_success() throws Exception {
        Auth auth = authRepository.save(Auth.createLocal(1L, "verify@example.com", "password1", "encoded"));
        emailVerificationRepository.save(
                EmailVerification.create("verify@example.com", passwordEncoder.encode("123456"), 5));
        String body = objectMapper.writeValueAsString(new ConfirmRequestBody("123456"));

        mockMvc.perform(post("/api/auth/email/verification/confirm")
                        .header("Authorization", bearerTokenFor(auth.getUserId()))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess", is(true)));
    }

    @Test
    @DisplayName("확인: 코드가 다르면 400 (AUTH_400_001)")
    void verifyCode_mismatch() throws Exception {
        Auth auth = authRepository.save(Auth.createLocal(1L, "verify2@example.com", "password1", "encoded"));
        emailVerificationRepository.save(
                EmailVerification.create("verify2@example.com", passwordEncoder.encode("123456"), 5));
        String body = objectMapper.writeValueAsString(new ConfirmRequestBody("000000"));

        mockMvc.perform(post("/api/auth/email/verification/confirm")
                        .header("Authorization", bearerTokenFor(auth.getUserId()))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("AUTH_400_001")));
    }

    @Test
    @DisplayName("확인: Authorization 헤더가 없으면 401")
    void verifyCode_unauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(new ConfirmRequestBody("123456"));

        mockMvc.perform(post("/api/auth/email/verification/confirm")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    private record ConfirmRequestBody(String code) {}
}
