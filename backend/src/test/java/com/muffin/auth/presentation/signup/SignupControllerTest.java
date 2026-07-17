package com.muffin.auth.presentation.signup;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.user.domain.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 로컬 회원가입 API가 실제 서비스/저장소까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignupControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        authRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("가입 성공 시 201, accessToken 반환, refreshToken은 HttpOnly Cookie로 내려간다")
    void signup_success() throws Exception {
        String body =
                objectMapper.writeValueAsString(new SignupRequestBody("new@example.com", "password1", "홍길동", true));

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
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
    @DisplayName("약관 미동의 시 400 (AUTH_400_002)")
    void signup_termsNotAgreed() throws Exception {
        String body =
                objectMapper.writeValueAsString(new SignupRequestBody("terms@example.com", "password1", "홍길동", false));

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("AUTH_400_002")));
    }

    @Test
    @DisplayName("이메일 형식이 아니면 400 (COMMON_400_002)")
    void signup_invalidEmail() throws Exception {
        String body = objectMapper.writeValueAsString(new SignupRequestBody("not-an-email", "password1", "홍길동", true));

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_400_002")));
    }

    @Test
    @DisplayName("비밀번호 형식이 틀리면 400 (COMMON_400_001, 도메인 검증)")
    void signup_invalidPasswordFormat() throws Exception {
        String body = objectMapper.writeValueAsString(new SignupRequestBody("pw@example.com", "short1", "홍길동", true));

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_400_001")));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 409 (AUTH_409_001)")
    void signup_duplicateEmail() throws Exception {
        String body =
                objectMapper.writeValueAsString(new SignupRequestBody("dup@example.com", "password1", "홍길동", true));
        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("AUTH_409_001")));
    }

    @Test
    @DisplayName("이름이 빈 값이면 400 (COMMON_400_002)")
    void signup_blankName() throws Exception {
        String body = objectMapper.writeValueAsString(new SignupRequestBody("name@example.com", "password1", "", true));

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_400_002")));
    }

    @Test
    @DisplayName("이메일이 빈 값이면 400 (COMMON_400_002)")
    void signup_blankEmail() throws Exception {
        String body = objectMapper.writeValueAsString(new SignupRequestBody("", "password1", "홍길동", true));

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_400_002")));
    }

    @Test
    @DisplayName("비밀번호가 빈 값이면 400 (COMMON_400_002)")
    void signup_blankPassword() throws Exception {
        String body = objectMapper.writeValueAsString(new SignupRequestBody("blank-pw@example.com", "", "홍길동", true));

        mockMvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_400_002")));
    }

    private record SignupRequestBody(String email, String password, String name, boolean termsAgreed) {}
}
