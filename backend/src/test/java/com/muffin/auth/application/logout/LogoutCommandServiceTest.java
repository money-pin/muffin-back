package com.muffin.auth.application.logout;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LogoutCommandServiceTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private LogoutCommandService logoutCommandService;

    @Autowired
    private RefreshTokenIssuer refreshTokenIssuer;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
    }

    @Test
    @DisplayName("로그아웃하면 해당 유저의 refresh token 행이 삭제된다")
    void logout_deletesRefreshToken() {
        refreshTokenIssuer.issue(USER_ID);
        assertThat(refreshTokenRepository.findByUserId(USER_ID)).isPresent();

        logoutCommandService.logout(USER_ID);

        assertThat(refreshTokenRepository.findByUserId(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("refresh token 행이 없어도 예외 없이 끝난다(멱등)")
    void logout_noExistingToken_doesNotThrow() {
        assertThatCode(() -> logoutCommandService.logout(USER_ID)).doesNotThrowAnyException();
    }
}
