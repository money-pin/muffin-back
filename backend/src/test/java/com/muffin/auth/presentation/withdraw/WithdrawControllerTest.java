package com.muffin.auth.presentation.withdraw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedemail.DeletedEmailRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.enums.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 탈퇴 API가 실제 서비스/저장소/Security까지 엮여서 동작하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WithdrawControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenProvider accessTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private DeletedEmailRepository deletedEmailRepository;

    @AfterEach
    void cleanUp() {
        deletedEmailRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createUser() {
        return userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
    }

    private String bearerTokenFor(Long userId) {
        return "Bearer " + accessTokenProvider.issue(userId, "USER");
    }

    @Test
    @DisplayName("인증된 상태에서 탈퇴하면 200, 계정이 WITHDRAWN 상태가 되고 쿠키가 만료된다")
    void withdraw_success() throws Exception {
        User user = createUser();
        authRepository.save(Auth.createLocal(user.getUserId(), "wd1@example.com", "password1", "encoded"));

        mockMvc.perform(delete("/api/auth/account").header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess", is(true)))
                .andExpect(cookie().maxAge("refreshToken", 0));

        assertThat(userRepository.findById(user.getUserId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWN);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 401")
    void withdraw_unauthorized() throws Exception {
        mockMvc.perform(delete("/api/auth/account")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이미 탈퇴한 계정이면 409 (COMMON_409_001)")
    void withdraw_alreadyWithdrawn() throws Exception {
        User user = createUser();
        authRepository.save(Auth.createLocal(user.getUserId(), "wd2@example.com", "password1", "encoded"));
        String bearer = bearerTokenFor(user.getUserId());
        mockMvc.perform(delete("/api/auth/account").header("Authorization", bearer))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/auth/account").header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("COMMON_409_001")));
    }

    @Test
    @DisplayName("GOOGLE 계정도 탈퇴하면 200, WITHDRAWN 상태가 되고 이메일이 익명화된다")
    void withdraw_googleAccount_success() throws Exception {
        User user = createUser();
        Auth auth = authRepository.save(Auth.createGoogle(user.getUserId(), "wd-google@example.com", "google-sub-wd"));

        mockMvc.perform(delete("/api/auth/account").header("Authorization", bearerTokenFor(user.getUserId())))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refreshToken", 0));

        assertThat(userRepository.findById(user.getUserId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.WITHDRAWN);
        assertThat(authRepository.findByUserId(user.getUserId()).orElseThrow().getEmail())
                .isEqualTo("withdrawn-" + auth.getAuthId() + "@deleted.local");
    }
}
