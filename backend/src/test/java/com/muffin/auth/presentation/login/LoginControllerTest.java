package com.muffin.auth.presentation.login;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.auth.domain.PasswordEncoder;
import com.muffin.auth.domain.RefreshTokenRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 로컬 로그인 API가 실제 서비스/저장소까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginControllerTest {

    private static final String EMAIL = "login-controller@example.com";
    private static final String PASSWORD = "password1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User registerLocalAccount() {
        User user = userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
        authRepository.save(Auth.createLocal(user.getUserId(), EMAIL, PASSWORD, passwordEncoder.encode(PASSWORD)));
        return user;
    }

    @Test
    @DisplayName("로그인 성공 시 200, accessToken 반환, refreshToken은 HttpOnly Cookie로 내려간다")
    void login_success() throws Exception {
        registerLocalAccount();
        String body = objectMapper.writeValueAsString(new LoginRequestBody(EMAIL, PASSWORD));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess", is(true)))
                .andExpect(jsonPath("$.result.accessToken").exists())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true))
                .andExpect(cookie().sameSite("refreshToken", "Strict"))
                .andExpect(cookie().path("refreshToken", "/api/auth"))
                .andExpect(cookie().maxAge("refreshToken", greaterThan(0)));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 (AUTH_401_002)")
    void login_wrongPassword() throws Exception {
        registerLocalAccount();
        String body = objectMapper.writeValueAsString(new LoginRequestBody(EMAIL, "wrongpass1"));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_401_002")));
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 401 (AUTH_401_002)")
    void login_emailNotFound() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestBody("nobody@example.com", PASSWORD));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_401_002")));
    }

    @Test
    @DisplayName("같은 이메일이 GOOGLE 계정으로만 가입되어 있으면(LOCAL 없음) 401 (AUTH_401_002)")
    void login_googleOnlyAccount() throws Exception {
        User user = userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
        authRepository.save(Auth.createGoogle(user.getUserId(), EMAIL, "google-sub-login-test"));
        String body = objectMapper.writeValueAsString(new LoginRequestBody(EMAIL, PASSWORD));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_401_002")));
    }

    @Test
    @DisplayName("탈퇴한 계정이면 403 (AUTH_403_002)")
    void login_withdrawnAccount() throws Exception {
        User user = registerLocalAccount();
        user.withdraw();
        userRepository.save(user);
        String body = objectMapper.writeValueAsString(new LoginRequestBody(EMAIL, PASSWORD));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_403_002")));
    }

    @Test
    @DisplayName("정지된 계정이면 403 (AUTH_403_003)")
    void login_suspendedAccount() throws Exception {
        User user = registerLocalAccount();
        user.suspend();
        userRepository.save(user);
        String body = objectMapper.writeValueAsString(new LoginRequestBody(EMAIL, PASSWORD));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_403_003")));
    }

    @Test
    @DisplayName("이메일이 빈 값이면 400 (COMMON_400_002)")
    void login_blankEmail() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestBody("", PASSWORD));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_400_002")));
    }

    @Test
    @DisplayName("비밀번호가 빈 값이면 400 (COMMON_400_002)")
    void login_blankPassword() throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequestBody(EMAIL, ""));

        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_400_002")));
    }

    @Test
    @DisplayName("비밀번호를 5회 연속 틀리면 6번째 시도는 정답이어도 423 (AUTH_423_002)로 잠긴다")
    void login_locksAfterFiveFailures() throws Exception {
        registerLocalAccount();
        String wrongBody = objectMapper.writeValueAsString(new LoginRequestBody(EMAIL, "wrongpass1"));

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType("application/json")
                            .content(wrongBody))
                    .andExpect(status().isUnauthorized());
        }

        String correctBody = objectMapper.writeValueAsString(new LoginRequestBody(EMAIL, PASSWORD));
        mockMvc.perform(post("/api/auth/login").contentType("application/json").content(correctBody))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code", is("AUTH_423_002")))
                .andExpect(jsonPath("$.message", is("로그인 시도 횟수를 초과하여 계정이 잠겼습니다. 잠시 후 다시 시도해 주세요.")));
    }

    private record LoginRequestBody(String email, String password) {}
}
