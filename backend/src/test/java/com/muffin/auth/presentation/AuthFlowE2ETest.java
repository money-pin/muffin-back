package com.muffin.auth.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.muffin.auth.application.emailverification.EmailSender;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.emailverification.EmailVerificationRepository;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.user.domain.UserRepository;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * signup → login, signup → 발급된 access token으로 이메일 인증까지, 이번 세션에서 만든 여러 API가 실제로
 * 엮여서 동작하는 end-to-end 흐름을 검증한다. 각 기능의 단위/컨트롤러 테스트는 이미 존재하므로, 여기서는 "기능 간 연결점"만
 * 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowE2ETest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private EmailSender emailSender;

    @AfterEach
    void cleanUp() {
        emailVerificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("회원가입 → 발급받은 access token으로 이메일 인증 발송/확인까지 성공하고 Auth.emailVerified가 true로 바뀐다")
    void signupThenVerifyEmail() throws Exception {
        String email = "e2e-verify@example.com";
        String accessToken = signup(email, "password1", "홍길동");

        AtomicReference<String> capturedCode = new AtomicReference<>();
        doAnswer(invocation -> {
                    capturedCode.set(invocation.getArgument(1));
                    return null;
                })
                .when(emailSender)
                .sendVerificationCode(eq(email), anyString());

        mockMvc.perform(post("/api/auth/email/verification").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.expiresIn").exists());
        assertThat(capturedCode.get()).isNotBlank();

        String confirmBody = objectMapper.writeValueAsString(new ConfirmBody(capturedCode.get()));
        mockMvc.perform(post("/api/auth/email/verification/confirm")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType("application/json")
                        .content(confirmBody))
                .andExpect(status().isOk());

        Auth auth =
                authRepository.findByProviderAndEmail(AuthProvider.LOCAL, email).orElseThrow();
        assertThat(auth.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("회원가입 직후 발급된 자격 증명으로 곧바로 로그인해도 동일하게 성공한다")
    void signupThenLogin() throws Exception {
        String email = "e2e-login@example.com";
        signup(email, "password1", "홍길동");

        String loginBody = objectMapper.writeValueAsString(new LoginBody(email, "password1"));
        mockMvc.perform(post("/auth/login").contentType("application/json").content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").exists());
    }

    private String signup(String email, String password, String name) throws Exception {
        String signupBody = objectMapper.writeValueAsString(new SignupBody(email, password, name, true));

        MvcResult result = mockMvc.perform(
                        post("/auth/signup").contentType("application/json").content(signupBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("result").get("accessToken").asText();
    }

    private record SignupBody(String email, String password, String name, boolean termsAgreed) {}

    private record LoginBody(String email, String password) {}

    private record ConfirmBody(String code) {}
}
