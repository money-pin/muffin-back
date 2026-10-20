package com.muffin.auth.application.withdraw;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.application.withdraw.AppleAuthorizationClient.AppleTokens;
import com.muffin.auth.domain.SocialIdentity;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedaccount.DeletedAccountRepository;
import com.muffin.auth.domain.deletedaccount.IdentityHasher;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.auth.infrastructure.apple.AppleIdTokenVerifierAdapter;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class WithdrawCommandServiceTest {

    private static final String EMAIL = "withdraw-target@example.com";

    @Autowired
    private WithdrawCommandService withdrawCommandService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private DeletedAccountRepository deletedAccountRepository;

    @Autowired
    private RefreshTokenIssuer refreshTokenIssuer;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private IdentityHasher identityHasher;

    @MockitoBean
    private KakaoAccountUnlinker kakaoAccountUnlinker;

    @MockitoBean
    private AppleAuthorizationClient appleAuthorizationClient;

    @MockitoBean
    private AppleIdTokenVerifierAdapter appleIdTokenVerifier;

    @BeforeEach
    void stubAppleProvider() {
        when(appleIdTokenVerifier.provider()).thenReturn(AuthProvider.APPLE);
    }

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();
        deletedAccountRepository.deleteAll();
        authRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createUser() {
        return userRepository.save(User.register(null, UUID.randomUUID().toString(), "홍길동", null));
    }

    private Auth createAuth(Long userId) {
        return authRepository.save(Auth.createSocial(userId, AuthProvider.GOOGLE, EMAIL, "google-sub-" + userId));
    }

    @Test
    @DisplayName("탈퇴하면 이름·이메일·sub가 지워지고 상태가 WITHDRAWN, provider+sub 해시가 남으며 refresh token이 삭제된다")
    void withdraw_success() {
        User user = createUser();
        Auth auth = createAuth(user.getUserId());
        refreshTokenIssuer.issue(user.getUserId());

        withdrawCommandService.withdraw(user.getUserId(), null);

        User updatedUser = userRepository.findById(user.getUserId()).orElseThrow();
        assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(updatedUser.getName()).isNull();

        Auth updatedAuth = authRepository.findByUserId(user.getUserId()).orElseThrow();
        assertThat(updatedAuth.getEmail()).isNull();
        assertThat(updatedAuth.getProviderUserId()).isEqualTo("withdrawn-" + auth.getAuthId());

        assertThat(deletedAccountRepository.existsByIdentityHashAndDeletedAtAfter(
                        identityHasher.hash(AuthProvider.GOOGLE, "google-sub-" + user.getUserId()),
                        LocalDateTime.now().minusMinutes(1)))
                .isTrue();

        assertThat(refreshTokenRepository.findByUserId(user.getUserId())).isEmpty();
    }

    @Test
    @DisplayName("카카오 계정이 탈퇴하면 커밋 후 원본 회원번호로 카카오 연결 끊기를 호출한다")
    void withdraw_kakaoAccount_unlinksAfterCommit() {
        User user = createUser();
        authRepository.save(Auth.createSocial(user.getUserId(), AuthProvider.KAKAO, null, "3812345678"));

        withdrawCommandService.withdraw(user.getUserId(), null);

        verify(kakaoAccountUnlinker, timeout(2000)).unlink("3812345678");
    }

    @Test
    @DisplayName("카카오가 아닌 계정이 탈퇴하면 카카오 연결 끊기를 호출하지 않는다")
    void withdraw_googleAccount_doesNotUnlinkKakao() {
        User user = createUser();
        createAuth(user.getUserId());

        withdrawCommandService.withdraw(user.getUserId(), null);

        verify(kakaoAccountUnlinker, after(500).never()).unlink(any());
    }

    @Test
    @DisplayName("애플 계정은 code 교환 후 본인 확인이 되면 탈퇴되고, 커밋 후 애플 refresh token을 철회한다")
    void withdraw_appleAccount_revokesAfterCommit() {
        User user = createAppleUser("apple-sub");
        when(appleAuthorizationClient.exchange("apple-code")).thenReturn(new AppleTokens("apple-refresh", "apple-id"));
        when(appleIdTokenVerifier.verify("apple-id")).thenReturn(new SocialIdentity("apple-sub", null, null));

        withdrawCommandService.withdraw(user.getUserId(), "apple-code");

        assertThat(statusOf(user)).isEqualTo(UserStatus.WITHDRAWN);
        verify(appleAuthorizationClient, timeout(2000)).revoke("apple-refresh");
    }

    @Test
    @DisplayName("애플 계정인데 code가 없으면 AUTH_400_003, 아무것도 바뀌지 않는다")
    void withdraw_appleAccount_withoutCode_fails() {
        User user = createAppleUser("apple-sub");

        assertErrorCode(() -> withdrawCommandService.withdraw(user.getUserId(), null), "AUTH_400_003");

        assertThat(statusOf(user)).isEqualTo(UserStatus.ACTIVE);
        verify(appleAuthorizationClient, never()).exchange(any());
    }

    @Test
    @DisplayName("애플이 code를 거절하면(만료·재사용) AUTH_400_004, 아무것도 바뀌지 않는다")
    void withdraw_appleAccount_invalidCode_fails() {
        User user = createAppleUser("apple-sub");
        when(appleAuthorizationClient.exchange("expired-code"))
                .thenThrow(new AuthException(AuthErrorCode.INVALID_APPLE_AUTHORIZATION_CODE));

        assertErrorCode(() -> withdrawCommandService.withdraw(user.getUserId(), "expired-code"), "AUTH_400_004");

        assertThat(statusOf(user)).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("code가 다른 애플 계정의 것이면 AUTH_400_005, 아무것도 바뀌지 않고 철회도 하지 않는다")
    void withdraw_appleAccount_otherAccountCode_fails() {
        User user = createAppleUser("apple-sub");
        when(appleAuthorizationClient.exchange("other-code")).thenReturn(new AppleTokens("other-refresh", "other-id"));
        when(appleIdTokenVerifier.verify("other-id")).thenReturn(new SocialIdentity("someone-else", null, null));

        assertErrorCode(() -> withdrawCommandService.withdraw(user.getUserId(), "other-code"), "AUTH_400_005");

        assertThat(statusOf(user)).isEqualTo(UserStatus.ACTIVE);
        verify(appleAuthorizationClient, after(500).never()).revoke(any());
    }

    private User createAppleUser(String appleSub) {
        User user = createUser();
        authRepository.save(Auth.createSocial(user.getUserId(), AuthProvider.APPLE, null, appleSub));
        return user;
    }

    private UserStatus statusOf(User user) {
        return userRepository.findById(user.getUserId()).orElseThrow().getStatus();
    }

    private void assertErrorCode(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOf(GeneralException.class).satisfies(e -> assertThat(
                        ((GeneralException) e).getErrorCode().getCode())
                .isEqualTo(code));
    }

    @Test
    @DisplayName("이미 탈퇴한 계정이면 IllegalStateException")
    void withdraw_alreadyWithdrawn() {
        User user = createUser();
        createAuth(user.getUserId());
        withdrawCommandService.withdraw(user.getUserId(), null);

        assertThatThrownBy(() -> withdrawCommandService.withdraw(user.getUserId(), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("정지된 계정이면 IllegalStateException")
    void withdraw_suspendedAccount() {
        User user = createUser();
        createAuth(user.getUserId());
        user.suspend();
        userRepository.save(user);

        assertThatThrownBy(() -> withdrawCommandService.withdraw(user.getUserId(), null))
                .isInstanceOf(IllegalStateException.class);
    }
}
