package com.muffin.auth.presentation.refresh;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Access/Refresh Token 재발급 API가 실제 서비스/저장소까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenIssuer refreshTokenIssuer;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createUser() {
        return userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
    }

    @Test
    @DisplayName("유효한 refresh token 쿠키로 재발급 성공, 새 refreshToken이 다시 Cookie로 내려간다")
    void refresh_success() throws Exception {
        User user = createUser();
        String rawToken = refreshTokenIssuer.issue(user.getUserId());

        mockMvc.perform(post("/auth/token/refresh").cookie(new Cookie("refreshToken", rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").exists())
                .andExpect(cookie().exists("refreshToken"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(cookie().secure("refreshToken", true))
                .andExpect(cookie().sameSite("refreshToken", "Strict"))
                .andExpect(cookie().path("refreshToken", "/auth"))
                .andExpect(cookie().maxAge("refreshToken", greaterThan(0)));
    }

    @Test
    @DisplayName("refresh token 쿠키가 없으면 401 (AUTH_401_003)")
    void refresh_missingCookie() throws Exception {
        mockMvc.perform(post("/auth/token/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_401_003")));
    }

    @Test
    @DisplayName("유효하지 않은 refresh token 쿠키면 401 (AUTH_401_003)")
    void refresh_invalidCookie() throws Exception {
        mockMvc.perform(post("/auth/token/refresh").cookie(new Cookie("refreshToken", "bogus-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("AUTH_401_003")));
    }

    @Test
    @DisplayName("탈퇴한 계정이면 403 (AUTH_403_002)")
    void refresh_withdrawnAccount() throws Exception {
        User user = createUser();
        String rawToken = refreshTokenIssuer.issue(user.getUserId());
        user.withdraw();
        userRepository.save(user);

        mockMvc.perform(post("/auth/token/refresh").cookie(new Cookie("refreshToken", rawToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("AUTH_403_002")));
    }
}
