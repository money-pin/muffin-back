package com.muffin.auth.presentation.apple;

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
import com.muffin.auth.infrastructure.apple.AppleIdTokenVerifierAdapter;
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

/** 애플 로그인 API가 실제 서비스/저장소/Security까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppleAuthControllerTest {

    private static final String SUB = "001234.abcdef0123456789.0123";

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
    private AppleIdTokenVerifierAdapter appleIdTokenVerifier;

    @BeforeEach
    void stubProvider() {
        when(appleIdTokenVerifier.provider()).thenReturn(AuthProvider.APPLE);
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
        deletedAccountRepository.deleteAll();
    }

    @Test
    @DisplayName("최초 로그인이면 200, 성+이름을 합친 이름으로 APPLE 계정이 가입되고 쿠키가 내려간다")
    void authenticate_firstLogin_signsUpWithName() throws Exception {
        when(appleIdTokenVerifier.verify("valid-id-token"))
                .thenReturn(new SocialIdentity(SUB, "abc@privaterelay.appleid.com", null));

        mockMvc.perform(post("/auth/apple")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("valid-id-token", "길동", "홍"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").exists())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().maxAge("refreshToken", greaterThan(0)));

        assertThat(nameOfAppleUser()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("합친 이름이 6자를 넘으면 이름(givenName)만 저장한다")
    void authenticate_longFullName_usesGivenNameOnly() throws Exception {
        when(appleIdTokenVerifier.verify("valid-id-token")).thenReturn(new SocialIdentity(SUB, null, null));

        mockMvc.perform(post("/auth/apple")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("valid-id-token", "John", "Kim"))))
                .andExpect(status().isOk());

        assertThat(nameOfAppleUser()).isEqualTo("John");
    }

    @Test
    @DisplayName("이미 가입된 계정이면 이후 요청의 이름은 무시한다")
    void authenticate_existingAccount_ignoresName() throws Exception {
        when(appleIdTokenVerifier.verify("valid-id-token")).thenReturn(new SocialIdentity(SUB, null, null));
        mockMvc.perform(post("/auth/apple")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("valid-id-token", "길동", "홍"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/apple")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("valid-id-token", "철수", "김"))))
                .andExpect(status().isOk());

        assertThat(authRepository.count()).isEqualTo(1);
        assertThat(nameOfAppleUser()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("이름 없이 요청해도(두 번째 이후 로그인) 가입된다")
    void authenticate_withoutName_signsUp() throws Exception {
        when(appleIdTokenVerifier.verify("valid-id-token")).thenReturn(new SocialIdentity(SUB, null, null));

        mockMvc.perform(post("/auth/apple")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("valid-id-token", null, null))))
                .andExpect(status().isOk());

        assertThat(nameOfAppleUser()).isNull();
    }

    @Test
    @DisplayName("idToken이 비어있으면 400 (COMMON_400_002)")
    void authenticate_blankIdToken() throws Exception {
        mockMvc.perform(post("/auth/apple")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("COMMON_400_002")));
    }

    @Test
    @DisplayName("유효하지 않은 애플 ID Token이면 401 (AUTH_401_004)")
    void authenticate_invalidToken() throws Exception {
        when(appleIdTokenVerifier.verify("bogus-id-token"))
                .thenThrow(new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN));

        mockMvc.perform(post("/auth/apple")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new RequestBody("bogus-id-token", null, null))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_401_004")));

        assertThat(authRepository.count()).isZero();
    }

    private String nameOfAppleUser() {
        Auth auth = authRepository
                .findByProviderAndProviderUserId(AuthProvider.APPLE, SUB)
                .orElseThrow();
        return userRepository.findById(auth.getUserId()).orElseThrow().getName();
    }

    private record RequestBody(String idToken, String givenName, String familyName) {}
}
