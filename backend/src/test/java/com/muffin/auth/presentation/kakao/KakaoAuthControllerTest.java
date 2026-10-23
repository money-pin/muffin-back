package com.muffin.auth.presentation.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.muffin.auth.domain.SocialIdentity;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedaccount.DeletedAccountRepository;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.auth.infrastructure.kakao.KakaoIdTokenVerifierAdapter;
import com.muffin.user.domain.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 카카오 로그인 API가 실제 서비스/저장소/Security까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KakaoAuthControllerTest {

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
    private DeletedAccountRepository deletedAccountRepository;

    @MockitoBean
    private KakaoIdTokenVerifierAdapter kakaoIdTokenVerifier;

    @BeforeEach
    void stubProvider() {
        when(kakaoIdTokenVerifier.provider()).thenReturn(AuthProvider.KAKAO);
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
        deletedAccountRepository.deleteAll();
    }

    @Test
    @DisplayName("신규 카카오 계정이면 200, 이메일 없이 KAKAO 계정으로 가입되고 쿠키가 내려간다")
    void authenticate_newAccount_success() throws Exception {
        when(kakaoIdTokenVerifier.verify("valid-id-token")).thenReturn(new SocialIdentity("3812345678", null, "길동이"));

        mockMvc.perform(post("/auth/kakao")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("valid-id-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").exists())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().maxAge("refreshToken", greaterThan(0)));

        Auth auth = authRepository
                .findByProviderAndProviderUserId(AuthProvider.KAKAO, "3812345678")
                .orElseThrow();
        assertThat(auth.getEmail()).isNull();
    }

    @Test
    @DisplayName("idToken이 비어있으면 400 (COMMON_400_002)")
    void authenticate_blankIdToken() throws Exception {
        mockMvc.perform(post("/auth/kakao")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_400_002")));
    }

    @Test
    @DisplayName("유효하지 않은 카카오 ID Token이면 401 (AUTH_401_004)")
    void authenticate_invalidToken() throws Exception {
        when(kakaoIdTokenVerifier.verify("bogus-id-token"))
                .thenThrow(new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN));

        mockMvc.perform(post("/auth/kakao")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("bogus-id-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_401_004")));

        assertThat(authRepository.count()).isZero();
    }

    private record RequestBody(String idToken) {}
}
