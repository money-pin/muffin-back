package com.muffin.auth.application.login;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.application.TokenPair;
import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.auth.domain.PasswordEncoder;
import com.muffin.auth.domain.RefreshTokenRepository;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LoginCommandServiceTest {

    private static final String EMAIL = "login@example.com";
    private static final String PASSWORD = "password1";

    @Autowired
    private LoginCommandService loginCommandService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User registerLocalAccount() {
        User user = userRepository.save(
                User.register(null, java.util.UUID.randomUUID().toString(), "홍길동", null));
        authRepository.save(Auth.createLocal(user.getUserId(), EMAIL, PASSWORD, passwordEncoder.encode(PASSWORD)));
        return user;
    }

    @Test
    @DisplayName("정상 로그인 시 access/refresh token이 발급된다")
    void loginLocal_success() {
        registerLocalAccount();

        TokenPair result = loginCommandService.loginLocal(EMAIL, PASSWORD);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("가입되지 않은 이메일이면 INVALID_CREDENTIALS(AUTH_401_002)")
    void loginLocal_emailNotFound() {
        assertThatThrownBy(() -> loginCommandService.loginLocal("nobody@example.com", PASSWORD))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_401_002"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 INVALID_CREDENTIALS(AUTH_401_002), 이메일 존재 여부와 같은 메시지")
    void loginLocal_wrongPassword() {
        registerLocalAccount();

        assertThatThrownBy(() -> loginCommandService.loginLocal(EMAIL, "wrongpass1"))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_401_002"));
    }

    @Test
    @DisplayName("탈퇴한 계정이면 WITHDRAWN_ACCOUNT(AUTH_403_002)")
    void loginLocal_withdrawnAccount() {
        User user = registerLocalAccount();
        user.withdraw();
        userRepository.save(user);

        assertThatThrownBy(() -> loginCommandService.loginLocal(EMAIL, PASSWORD))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_403_002"));
    }

    @Test
    @DisplayName("정지된 계정이면 SUSPENDED_ACCOUNT(AUTH_403_003)")
    void loginLocal_suspendedAccount() {
        User user = registerLocalAccount();
        user.suspend();
        userRepository.save(user);

        assertThatThrownBy(() -> loginCommandService.loginLocal(EMAIL, PASSWORD))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_403_003"));
    }

    @Test
    @DisplayName("GOOGLE로 가입된 이메일에 로컬 로그인을 시도하면 INVALID_CREDENTIALS(AUTH_401_002), 계정 존재 여부가 드러나지 않는다")
    void loginLocal_googleAccountEmail() {
        User user = userRepository.save(
                User.register(null, java.util.UUID.randomUUID().toString(), "홍길동", null));
        authRepository.save(Auth.createGoogle(user.getUserId(), EMAIL, "google-sub-1"));

        assertThatThrownBy(() -> loginCommandService.loginLocal(EMAIL, PASSWORD))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_401_002"));
    }

    @Test
    @DisplayName("비밀번호를 5회 연속 틀리면 6번째 시도부터는 정답을 넣어도 LOGIN_LOCKED(AUTH_423_002)로 거부된다")
    void loginLocal_locksAfterFiveFailures() {
        registerLocalAccount();

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> loginCommandService.loginLocal(EMAIL, "wrongpass1"))
                    .isInstanceOf(GeneralException.class)
                    .satisfies(e -> assertThat(
                                    ((GeneralException) e).getErrorCode().getCode())
                            .isEqualTo("AUTH_401_002"));
        }

        // 잠긴 이후에는 올바른 비밀번호를 넣어도 코드 비교 전에 잠금으로 거부되어야 한다(브루트포스 방지).
        assertThatThrownBy(() -> loginCommandService.loginLocal(EMAIL, PASSWORD))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_423_002"));
    }

    @Test
    @DisplayName("잠기기 전에 로그인에 성공하면 실패 횟수가 초기화되어 다시 5번의 기회가 주어진다")
    void loginLocal_successResetsFailureCount() {
        registerLocalAccount();

        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> loginCommandService.loginLocal(EMAIL, "wrongpass1"))
                    .isInstanceOf(GeneralException.class);
        }
        loginCommandService.loginLocal(EMAIL, PASSWORD);

        Auth reloaded = authRepository
                .findByProviderAndEmail(com.muffin.auth.domain.enums.AuthProvider.LOCAL, EMAIL)
                .orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
        assertThat(reloaded.isLoginLocked()).isFalse();
    }
}
