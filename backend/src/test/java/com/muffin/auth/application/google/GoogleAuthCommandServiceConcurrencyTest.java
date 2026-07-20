package com.muffin.auth.application.google;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.application.TokenPair;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.auth.domain.DeletedEmailRepository;
import com.muffin.auth.domain.GoogleIdTokenPayload;
import com.muffin.auth.domain.GoogleIdTokenVerifier;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
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
 * findByProviderAndProviderUserId 통과 이후 커밋 직전에 같은 구글 계정(sub)으로 다른 요청이 먼저 가입을 완료하는
 * 레이스 상황을 리포지토리 목킹으로 재현한다(SignupCommandServiceConcurrencyTest와 동일한 패턴).
 */
@ExtendWith(MockitoExtension.class)
class GoogleAuthCommandServiceConcurrencyTest {

    private static final String ID_TOKEN = "id-token";
    private static final String SUB = "sub-race";
    private static final String EMAIL = "race@example.com";

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

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

    private GoogleAuthCommandService googleAuthCommandService;

    @BeforeEach
    void setUp() {
        googleAuthCommandService = new GoogleAuthCommandService(
                googleIdTokenVerifier,
                authRepository,
                userRepository,
                deletedEmailRepository,
                accessTokenProvider,
                refreshTokenIssuer,
                entityManager);
    }

    @Test
    @DisplayName("같은 구글 계정으로 동시 최초 로그인 시 uk_provider_user 위반이면 실패시키지 않고 승자로 로그인 처리하며, " + "이 요청이 만든 자신의 User 행은 지운다")
    void authenticate_concurrentFirstLogin_recoversAsLoginAndCleansUpOwnUser() {
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleIdTokenPayload(SUB, EMAIL, "레이스"));

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "userId", 99L);
            return user;
        });

        when(authRepository.saveAndFlush(any(Auth.class)))
                .thenThrow(new DataIntegrityViolationException("uk_provider_user"));

        User winnerUser = User.register(null, "winner-uuid", "레이스", null);
        ReflectionTestUtils.setField(winnerUser, "userId", 1L);
        Auth winnerAuth = Auth.createGoogle(1L, EMAIL, SUB);
        when(authRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerAuth));
        when(userRepository.findById(1L)).thenReturn(Optional.of(winnerUser));

        when(accessTokenProvider.issue(1L, "USER")).thenReturn("access-token");
        when(refreshTokenIssuer.issue(1L)).thenReturn("refresh-token");

        TokenPair result = googleAuthCommandService.authenticate(ID_TOKEN);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(entityManager).clear();
        verify(userRepository).deleteById(99L);
    }

    @Test
    @DisplayName("uk_provider_email 위반(다른 구글 계정이 같은 이메일 사용 중)이면 EMAIL_ALREADY_IN_USE로 실패하고 "
            + "자신의 User 행을 따로 지우지 않는다(트랜잭션 롤백에 맡김)")
    void authenticate_differentAccountSameEmail_throwsEmailAlreadyInUse() {
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleIdTokenPayload(SUB, EMAIL, "레이스"));

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "userId", 99L);
            return user;
        });

        when(authRepository.saveAndFlush(any(Auth.class)))
                .thenThrow(new DataIntegrityViolationException("uk_provider_email"));
        when(authRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> googleAuthCommandService.authenticate(ID_TOKEN))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_409_001"));

        verify(userRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("알 수 없는 무결성 위반이면 그대로 전파한다")
    void authenticate_unrelatedDataIntegrityViolation_propagatesAsIs() {
        when(googleIdTokenVerifier.verify(ID_TOKEN)).thenReturn(new GoogleIdTokenPayload(SUB, EMAIL, "레이스"));

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "userId", 99L);
            return user;
        });

        DataIntegrityViolationException unrelated = new DataIntegrityViolationException("some_other_constraint");
        when(authRepository.saveAndFlush(any(Auth.class))).thenThrow(unrelated);
        when(authRepository.findByProviderAndProviderUserId(AuthProvider.GOOGLE, SUB))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> googleAuthCommandService.authenticate(ID_TOKEN))
                .isSameAs(unrelated);
    }
}
