package com.muffin.auth.presentation.logout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
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

/** 로그아웃 API가 실제 서비스/저장소/Security까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogoutControllerTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private RefreshTokenIssuer refreshTokenIssuer;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthRepository authRepository;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String bearerTokenFor(Long userId) {
        return "Bearer " + accessTokenProvider.issue(userId, "USER");
    }

    @Test
    @DisplayName("로그인된 상태에서 로그아웃하면 200, refresh token이 삭제되고 쿠키가 만료된다")
    void logout_success() throws Exception {
        refreshTokenIssuer.issue(USER_ID);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearerTokenFor(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess", is(true)))
                .andExpect(cookie().maxAge("refreshToken", 0));

        assertThat(refreshTokenRepository.findByUserId(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401")
    void logout_unauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GOOGLE 계정으로 로그인된 상태에서도 로그아웃하면 200, refresh token이 삭제된다")
    void logout_googleAccount_success() throws Exception {
        User user = userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
        authRepository.save(Auth.createGoogle(user.getUserId(), "google-logout@example.com", "google-sub-logout"));
        refreshTokenIssuer.issue(user.getUserId());

        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refreshToken", 0));

        assertThat(refreshTokenRepository.findByUserId(user.getUserId())).isEmpty();
    }
}
