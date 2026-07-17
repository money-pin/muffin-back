package com.muffin.auth.presentation.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muffin.auth.application.exception.AuthErrorCode;
import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.auth.domain.DeletedEmailRepository;
import com.muffin.auth.domain.GoogleIdTokenPayload;
import com.muffin.auth.domain.GoogleIdTokenVerifier;
import com.muffin.auth.domain.RefreshTokenRepository;
import com.muffin.global.apiPayload.exception.GeneralException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 구글 OAuth 통합 API가 실제 서비스/저장소/Security까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoogleAuthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private DeletedEmailRepository deletedEmailRepository;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
        deletedEmailRepository.deleteAll();
    }

    @Test
    @DisplayName("신규 구글 계정 + termsAgreed=true면 200, 자동 가입되고 쿠키가 내려간다")
    void authenticate_newAccount_success() throws Exception {
        when(googleIdTokenVerifier.verify("valid-id-token"))
                .thenReturn(new GoogleIdTokenPayload("google-sub-x", "x@example.com", "홍길동"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("valid-id-token", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").exists())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().maxAge("refreshToken", greaterThan(0)));

        assertThat(authRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("신규 구글 계정 + termsAgreed=false면 400 (AUTH_400_002)")
    void authenticate_newAccount_termsNotAgreed() throws Exception {
        when(googleIdTokenVerifier.verify("valid-id-token-2"))
                .thenReturn(new GoogleIdTokenPayload("google-sub-y", "y@example.com", "홍길동"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("valid-id-token-2", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("AUTH_400_002")));
    }

    @Test
    @DisplayName("idToken이 비어있으면 400 (COMMON_400_002)")
    void authenticate_blankIdToken() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_400_002")));
    }

    @Test
    @DisplayName("이미 가입된 구글 계정으로 재요청하면 200, 새 계정을 만들지 않고 로그인만 된다")
    void authenticate_existingAccount_logsIn() throws Exception {
        User user = userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
        authRepository.save(Auth.createGoogle(user.getUserId(), "existing@example.com", "google-sub-existing"));
        when(googleIdTokenVerifier.verify("existing-id-token"))
                .thenReturn(new GoogleIdTokenPayload("google-sub-existing", "existing@example.com", "홍길동"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("existing-id-token", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").exists());

        assertThat(authRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴한 구글 계정이면 403 (AUTH_403_002)")
    void authenticate_withdrawnAccount() throws Exception {
        User user = userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
        authRepository.save(Auth.createGoogle(user.getUserId(), "withdrawn@example.com", "google-sub-withdrawn"));
        user.withdraw();
        userRepository.save(user);
        when(googleIdTokenVerifier.verify("withdrawn-id-token"))
                .thenReturn(new GoogleIdTokenPayload("google-sub-withdrawn", "withdrawn@example.com", "홍길동"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("withdrawn-id-token", false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_403_002")));
    }

    @Test
    @DisplayName("정지된 구글 계정이면 403 (AUTH_403_003)")
    void authenticate_suspendedAccount() throws Exception {
        User user = userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
        authRepository.save(Auth.createGoogle(user.getUserId(), "suspended@example.com", "google-sub-suspended"));
        user.suspend();
        userRepository.save(user);
        when(googleIdTokenVerifier.verify("suspended-id-token"))
                .thenReturn(new GoogleIdTokenPayload("google-sub-suspended", "suspended@example.com", "홍길동"));

        mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("suspended-id-token", false))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_403_003")));
    }

    @Test
    @DisplayName("유효하지 않은 구글 ID Token이면 401 (AUTH_401_004)")
    void authenticate_invalidGoogleToken() throws Exception {
        when(googleIdTokenVerifier.verify("bogus-id-token"))
                .thenThrow(new GeneralException(AuthErrorCode.INVALID_GOOGLE_TOKEN));

        mockMvc.perform(post("/api/auth/google")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("bogus-id-token", true))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_401_004")));

        assertThat(authRepository.count()).isZero();
    }

    private record RequestBody(String idToken, boolean termsAgreed) {}
}
