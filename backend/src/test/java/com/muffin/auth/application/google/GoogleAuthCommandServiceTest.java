package com.muffin.auth.application.google;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.muffin.auth.application.TokenPair;
import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.auth.domain.DeletedEmail;
import com.muffin.auth.domain.DeletedEmailRepository;
import com.muffin.auth.domain.GoogleIdTokenPayload;
import com.muffin.auth.domain.GoogleIdTokenVerifier;
import com.muffin.auth.domain.RefreshTokenRepository;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** GoogleIdTokenVerifier는 실제 구글 서명 검증을 하지 않도록 @MockitoBean으로 대체하고, 그 이후 로직(자동 가입/로그인)을 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class GoogleAuthCommandServiceTest {

    private static final String ID_TOKEN = "id-token";

    @Autowired
    private GoogleAuthCommandService googleAuthCommandService;

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

    private void stubPayload(String sub, String email, String name) {
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleIdTokenPayload(sub, email, name));
    }

    @Test
    @DisplayName("처음 로그인하는 구글 계정이면 termsAgreed=true일 때 자동 가입되고 토큰이 발급된다")
    void authenticate_newAccount_signsUp() {
        stubPayload("google-sub-1", "new@example.com", "홍길동");

        TokenPair result = googleAuthCommandService.authenticate(ID_TOKEN, true);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();

        Auth auth = authRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1")
                .orElseThrow();
        assertThat(auth.getEmail()).isEqualTo("new@example.com");
        assertThat(auth.isEmailVerified()).isTrue();

        User user = userRepository.findById(auth.getUserId()).orElseThrow();
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.isTermAgreement()).isTrue();
    }

    @Test
    @DisplayName("처음 로그인하는 구글 계정인데 termsAgreed=false면 TERMS_NOT_AGREED(AUTH_400_002)")
    void authenticate_newAccount_termsNotAgreed() {
        stubPayload("google-sub-2", "new2@example.com", "홍길동");

        assertThatThrownBy(() -> googleAuthCommandService.authenticate(ID_TOKEN, false))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_400_002"));

        assertThat(authRepository.count()).isZero();
    }

    @Test
    @DisplayName("이미 가입된 구글 계정이면 termsAgreed와 무관하게 로그인만 처리되고 새 계정을 만들지 않는다")
    void authenticate_existingAccount_logsIn() {
        stubPayload("google-sub-3", "existing@example.com", "홍길동");
        googleAuthCommandService.authenticate(ID_TOKEN, true);
        assertThat(authRepository.count()).isEqualTo(1);

        TokenPair result = googleAuthCommandService.authenticate(ID_TOKEN, false);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(authRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("30일 이내 탈퇴한 이메일이면 신규 가입 시 RECENTLY_DELETED_EMAIL(AUTH_409_003)")
    void authenticate_newAccount_recentlyDeletedEmail() {
        deletedEmailRepository.save(DeletedEmail.of("deleted@example.com"));
        stubPayload("google-sub-4", "deleted@example.com", "홍길동");

        assertThatThrownBy(() -> googleAuthCommandService.authenticate(ID_TOKEN, true))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_409_003"));
    }

    @Test
    @DisplayName("탈퇴한 계정이면 WITHDRAWN_ACCOUNT(AUTH_403_002)")
    void authenticate_withdrawnAccount() {
        stubPayload("google-sub-5", "withdrawn@example.com", "홍길동");
        googleAuthCommandService.authenticate(ID_TOKEN, true);
        User user = userRepository
                .findById(authRepository
                        .findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-5")
                        .orElseThrow()
                        .getUserId())
                .orElseThrow();
        user.withdraw();
        userRepository.save(user);

        assertThatThrownBy(() -> googleAuthCommandService.authenticate(ID_TOKEN, false))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_403_002"));
    }
}
