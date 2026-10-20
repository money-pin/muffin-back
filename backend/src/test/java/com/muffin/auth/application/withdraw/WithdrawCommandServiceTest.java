package com.muffin.auth.application.withdraw;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedaccount.DeletedAccountRepository;
import com.muffin.auth.domain.deletedaccount.IdentityHasher;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import com.muffin.user.domain.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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

        withdrawCommandService.withdraw(user.getUserId());

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
    @DisplayName("이미 탈퇴한 계정이면 IllegalStateException")
    void withdraw_alreadyWithdrawn() {
        User user = createUser();
        createAuth(user.getUserId());
        withdrawCommandService.withdraw(user.getUserId());

        assertThatThrownBy(() -> withdrawCommandService.withdraw(user.getUserId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("정지된 계정이면 IllegalStateException")
    void withdraw_suspendedAccount() {
        User user = createUser();
        createAuth(user.getUserId());
        user.suspend();
        userRepository.save(user);

        assertThatThrownBy(() -> withdrawCommandService.withdraw(user.getUserId()))
                .isInstanceOf(IllegalStateException.class);
    }
}
