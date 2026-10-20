package com.muffin.auth.application.social;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.application.TokenPair;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.SocialIdTokenVerifier;
import com.muffin.auth.domain.SocialIdentity;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedemail.DeletedEmailRepository;
import com.muffin.auth.domain.deletedemail.EmailHasher;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * findByProviderAndProviderUserId 통과 이후 커밋 직전에 같은 소셜 계정(sub)으로 다른 요청이 먼저 가입을 완료하는
 * 레이스 상황을 리포지토리 목킹으로 재현한다.
 */
@ExtendWith(MockitoExtension.class)
class SocialAuthCommandServiceConcurrencyTest {

    private static final String ID_TOKEN = "id-token";
    private static final String SUB = "sub-race";
    private static final String EMAIL = "race@example.com";
    private static final SocialIdentity IDENTITY = new SocialIdentity(SUB, EMAIL, "레이스");

    @Mock
    private SocialIdTokenVerifier googleIdTokenVerifier;

    @Mock
    private AuthRepository authRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DeletedEmailRepository deletedEmailRepository;

    @Mock
    private AccessTokenProvider accessTokenProvider;

    @Mock
    private RefreshTokenIssuer refreshTokenIssuer;

    @Mock
    private EntityManager entityManager;

    @Mock
    private EmailHasher emailHasher;

    private SocialAuthCommandService socialAuthCommandService;

    @BeforeEach
    void setUp() {
        when(googleIdTokenVerifier.provider()).thenReturn(AuthProvider.GOOGLE);
        socialAuthCommandService = new SocialAuthCommandService(
                List.of(googleIdTokenVerifier),
                authRepository,
                userRepository,
                deletedEmailRepository,
                accessTokenProvider,
                refreshTokenIssuer,
                entityManager,
                emailHasher);
    }

    @Test
    @DisplayName("같은 구글 계정으로 동시 최초 로그인 시 uk_provider_user 위반이면 실패시키지 않고 승자로 로그인 처리하며, " + "이 요청이 만든 자신의 User 행은 지운다")
    void authenticate_concurrentFirstLogin_recoversAsLoginAndCleansUpOwnUser() {
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(IDENTITY);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "userId", 99L);
            return user;
        });

        when(authRepository.saveAndFlush(any(Auth.class)))
                .thenThrow(new DataIntegrityViolationException("uk_provider_user"));

        User winnerUser = User.register(null, "winner-uuid", "레이스", null);
        ReflectionTestUtils.setField(winnerUser, "userId", 1L);
        Auth winnerAuth = Auth.createSocial(1L, AuthProvider.GOOGLE, EMAIL, SUB);
        when(authRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerAuth));
        when(userRepository.findById(1L)).thenReturn(Optional.of(winnerUser));

        when(accessTokenProvider.issue(1L, "USER")).thenReturn("access-token");
        when(refreshTokenIssuer.issue(1L)).thenReturn("refresh-token");

        TokenPair result = socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(entityManager).clear();
        verify(userRepository).deleteById(99L);
    }

    @Test
    @DisplayName("알 수 없는 무결성 위반이면 그대로 전파한다")
    void authenticate_unrelatedDataIntegrityViolation_propagatesAsIs() {
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(IDENTITY);

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "userId", 99L);
            return user;
        });

        DataIntegrityViolationException unrelated = new DataIntegrityViolationException("some_other_constraint");
        when(authRepository.saveAndFlush(any(Auth.class))).thenThrow(unrelated);
        when(authRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> socialAuthCommandService.authenticate(AuthProvider.GOOGLE, ID_TOKEN))
                .isSameAs(unrelated);
    }
}
