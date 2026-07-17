package com.muffin.auth.application.signup;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
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
class SignupCommandServiceTest {

    private static final String EMAIL = "signup@example.com";
    private static final String PASSWORD = "password1";
    private static final String NAME = "홍길동";

    @Autowired
    private SignupCommandService signupCommandService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("정상 가입 시 User+Auth가 생성되고 access/refresh token이 발급된다")
    void signupLocal_success() {
        SignupResult result = signupCommandService.signupLocal(EMAIL, PASSWORD, NAME, true);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();

        User user = userRepository.findAll().get(0);
        assertThat(user.getName()).isEqualTo(NAME);
        assertThat(user.getCharacterId()).isNull();
        assertThat(user.getNickname()).isNull();
        assertThat(user.isTermAgreement()).isTrue();

        Auth auth = authRepository.findByUserId(user.getUserId()).orElseThrow();
        assertThat(auth.getEmail()).isEqualTo(EMAIL);
        assertThat(auth.isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("약관 미동의 시 TERMS_NOT_AGREED(AUTH_400_002), User/Auth 생성 안 됨")
    void signupLocal_termsNotAgreed() {
        assertThatThrownBy(() -> signupCommandService.signupLocal(EMAIL, PASSWORD, NAME, false))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_400_002"));

        assertThat(userRepository.count()).isZero();
        assertThat(authRepository.count()).isZero();
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 EMAIL_ALREADY_IN_USE(AUTH_409_001), 기존 계정은 그대로")
    void signupLocal_duplicateEmail() {
        signupCommandService.signupLocal(EMAIL, PASSWORD, NAME, true);

        assertThatThrownBy(() -> signupCommandService.signupLocal(EMAIL, "password2", "다른이름", true))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_409_001"));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(authRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("비밀번호 형식이 틀리면 도메인에서 IllegalArgumentException, User/Auth 저장이 롤백된다")
    void signupLocal_invalidPasswordFormat_rollsBack() {
        assertThatThrownBy(() -> signupCommandService.signupLocal(EMAIL, "short1", NAME, true))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(userRepository.count()).isZero();
        assertThat(authRepository.count()).isZero();
    }
}
