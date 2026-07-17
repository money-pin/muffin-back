package com.muffin.auth.application.refresh;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.application.TokenPair;
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
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class RefreshCommandServiceTest {

    @Autowired
    private RefreshCommandService refreshCommandService;

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
    @DisplayName("유효한 refresh token으로 재발급하면 새 access/refresh token이 나오고, 기존 토큰은 더 이상 유효하지 않다(회전)")
    void refresh_success_rotatesToken() {
        User user = createUser();
        String oldRawToken = refreshTokenIssuer.issue(user.getUserId());

        TokenPair result = refreshCommandService.refresh(oldRawToken);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotEqualTo(oldRawToken);
        assertThat(refreshTokenIssuer.findValid(oldRawToken)).isEmpty();
        assertThat(refreshTokenIssuer.findValid(result.refreshToken())).isPresent();
    }

    @Test
    @DisplayName("존재하지 않는 refresh token이면 INVALID_REFRESH_TOKEN(AUTH_401_003)")
    void refresh_unknownToken() {
        assertThatThrownBy(() -> refreshCommandService.refresh("no-such-token"))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_401_003"));
    }

    @Test
    @DisplayName("탈퇴한 계정이면 WITHDRAWN_ACCOUNT(AUTH_403_002)")
    void refresh_withdrawnAccount() {
        User user = createUser();
        String rawToken = refreshTokenIssuer.issue(user.getUserId());
        user.withdraw();
        userRepository.save(user);

        assertThatThrownBy(() -> refreshCommandService.refresh(rawToken))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_403_002"));
    }

    @Test
    @DisplayName("정지된 계정이면 SUSPENDED_ACCOUNT(AUTH_403_003)")
    void refresh_suspendedAccount() {
        User user = createUser();
        String rawToken = refreshTokenIssuer.issue(user.getUserId());
        user.suspend();
        userRepository.save(user);

        assertThatThrownBy(() -> refreshCommandService.refresh(rawToken))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_403_003"));
    }
}
