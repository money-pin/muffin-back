package com.muffin.auth.application.social;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.muffin.auth.application.TokenPair;
import com.muffin.auth.application.withdraw.WithdrawCommandService;
import com.muffin.auth.domain.SocialIdentity;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedaccount.DeletedAccount;
import com.muffin.auth.domain.deletedaccount.DeletedAccountRepository;
import com.muffin.auth.domain.deletedaccount.IdentityHasher;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.auth.infrastructure.google.GoogleIdTokenVerifierAdapter;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

/** 구글 verifier는 실제 서명 검증을 하지 않도록 @MockitoBean으로 대체하고, 그 이후 로직(자동 가입/로그인)을 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class SocialAuthCommandServiceTest {

    private static final String ID_TOKEN = "id-token";

    @Autowired
    private SocialAuthCommandService socialAuthCommandService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private DeletedAccountRepository deletedAccountRepository;

    @Autowired
    private IdentityHasher identityHasher;

    @Autowired
    private WithdrawCommandService withdrawCommandService;

    @MockitoBean
    private GoogleIdTokenVerifierAdapter googleIdTokenVerifier;

    @BeforeEach
    void stubProvider() {
        when(googleIdTokenVerifier.provider()).thenReturn(AuthProvider.GOOGLE);
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
        deletedAccountRepository.deleteAll();
    }

    private void stubPayload(String sub, String email, String name) {
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(new SocialIdentity(sub, email, name));
    }

    @Test
    @DisplayName("처음 로그인하는 구글 계정이면 자동 가입되고 토큰이 발급된다(약관도 자동 동의)")
    void authenticate_newAccount_signsUp() {
        stubPayload("google-sub-1", "new@example.com", "홍길동");

        TokenPair result = socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();

        Auth auth = authRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-1")
                .orElseThrow();
        assertThat(auth.getEmail()).isEqualTo("new@example.com");

        User user = userRepository.findById(auth.getUserId()).orElseThrow();
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.isTermAgreement()).isTrue();
    }

    @Test
    @DisplayName("이미 가입된 구글 계정이면 로그인만 처리되고 새 계정을 만들지 않는다")
    void authenticate_existingAccount_logsIn() {
        stubPayload("google-sub-3", "existing@example.com", "홍길동");
        socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN);
        assertThat(authRepository.count()).isEqualTo(1);

        TokenPair result = socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(authRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴 후 30일 이내에 같은 소셜 계정으로 다시 로그인하면 RECENTLY_WITHDRAWN_ACCOUNT(AUTH_409_003)")
    void authenticate_afterWithdraw_within30Days_blocked() {
        stubPayload("google-sub-4", "rejoin@example.com", "홍길동");
        signUpAndWithdraw();

        assertThatThrownBy(() -> socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_409_003"));
    }

    @Test
    @DisplayName("탈퇴 후 30일이 지나면 같은 소셜 계정으로 새 계정이 만들어진다")
    void authenticate_afterWithdraw_after30Days_signsUpAsNewAccount() {
        stubPayload("google-sub-5", "rejoin@example.com", "홍길동");
        Long withdrawnUserId = signUpAndWithdraw();
        deletedAccountRepository.findAll().forEach(deleted -> {
            ReflectionTestUtils.setField(
                    deleted, "deletedAt", LocalDateTime.now().minusDays(31));
            deletedAccountRepository.save(deleted);
        });

        socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN);

        Auth rejoined = authRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-5")
                .orElseThrow();
        assertThat(rejoined.getUserId()).isNotEqualTo(withdrawnUserId);
    }

    @Test
    @DisplayName("sub가 같아도 provider가 다른 탈퇴 기록이면 차단되지 않는다")
    void authenticate_sameSubWithdrawnOnOtherProvider_notBlocked() {
        deletedAccountRepository.save(DeletedAccount.of(identityHasher.hash(AuthProvider.KAKAO, "same-sub")));
        stubPayload("same-sub", "other@example.com", "홍길동");

        assertThatNoException().isThrownBy(() -> socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN));
    }

    private Long signUpAndWithdraw() {
        socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN);
        Long userId = authRepository.findAll().get(0).getUserId();
        withdrawCommandService.withdraw(userId);
        return userId;
    }

    @Test
    @DisplayName("다른 계정과 이메일이 같아도 sub가 다르면 별개 계정으로 가입된다")
    void authenticate_newAccount_sameEmailAsOtherAccount_signsUpSeparately() {
        stubPayload("google-sub-a", "shared@example.com", "홍길동");
        socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN);
        stubPayload("google-sub-b", "shared@example.com", "홍길동");

        socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN);

        Auth first = authRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-a")
                .orElseThrow();
        Auth second = authRepository
                .findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-b")
                .orElseThrow();
        assertThat(first.getUserId()).isNotEqualTo(second.getUserId());
        assertThat(userRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("구글 name이 6자를 초과해도(예: 학교 Workspace 계정) 잘라서 저장하고 가입에 성공한다")
    void authenticate_newAccount_longNameIsTruncated() {
        stubPayload("google-sub-6", "student@g.hongik.ac.kr", "산업경영공학과 홍길동 20211234");

        TokenPair result = socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN);

        assertThat(result.accessToken()).isNotBlank();
        User user = userRepository
                .findById(authRepository
                        .findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-6")
                        .orElseThrow()
                        .getUserId())
                .orElseThrow();
        assertThat(user.getName()).isEqualTo("산업경영공학");
    }

    @Test
    @DisplayName("탈퇴한 계정이면 WITHDRAWN_ACCOUNT(AUTH_403_002)")
    void authenticate_withdrawnAccount() {
        stubPayload("google-sub-5", "withdrawn@example.com", "홍길동");
        socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN);
        User user = userRepository
                .findById(authRepository
                        .findByProviderAndProviderUserId(AuthProvider.GOOGLE, "google-sub-5")
                        .orElseThrow()
                        .getUserId())
                .orElseThrow();
        user.withdraw();
        userRepository.save(user);

        assertThatThrownBy(() -> socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_403_002"));
    }
}
