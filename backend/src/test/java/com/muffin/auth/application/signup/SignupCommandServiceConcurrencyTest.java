package com.muffin.auth.application.signup;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.PasswordEncoder;
import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.deletedemail.DeletedEmailRepository;
import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * existsByEmail 통과 이후 커밋 직전에 다른 트랜잭션이 먼저 같은 이메일로 가입을 완료하는 레이스 상황을 리포지토리 목킹으로
 * 재현한다(EtfPriceWriterConcurrencyTest와 동일한 패턴).
 */
@ExtendWith(MockitoExtension.class)
class SignupCommandServiceConcurrencyTest {

    private static final String EMAIL = "race@example.com";
    private static final String PASSWORD = "password1";
    private static final String NAME = "홍길동";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthRepository authRepository;

    @Mock
    private DeletedEmailRepository deletedEmailRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccessTokenProvider accessTokenProvider;

    @Mock
    private RefreshTokenIssuer refreshTokenIssuer;

    private SignupCommandService signupCommandService;

    @BeforeEach
    void setUp() {
        signupCommandService = new SignupCommandService(
                userRepository,
                authRepository,
                deletedEmailRepository,
                passwordEncoder,
                accessTokenProvider,
                refreshTokenIssuer);
    }

    @Test
    @DisplayName("existsByEmail 통과 후 저장 시점에 unique 제약 위반이 나면 EMAIL_ALREADY_IN_USE(AUTH_409_001)로 번역된다")
    void signupLocal_dataIntegrityViolationOnSave_translatesToEmailAlreadyInUse() {
        when(authRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "userId", 1L);
            return user;
        });
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded");
        when(authRepository.saveAndFlush(any(Auth.class)))
                .thenThrow(new DataIntegrityViolationException("uk_provider_email"));

        assertThatThrownBy(() -> signupCommandService.signupLocal(EMAIL, PASSWORD, NAME, true))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_409_001"));
    }

    @Test
    @DisplayName("uk_provider_email과 무관한 무결성 위반이면 EMAIL_ALREADY_IN_USE로 감추지 않고 그대로 전파한다")
    void signupLocal_unrelatedDataIntegrityViolation_propagatesAsIs() {
        when(authRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "userId", 1L);
            return user;
        });
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded");
        DataIntegrityViolationException unrelated = new DataIntegrityViolationException("some_other_constraint");
        when(authRepository.saveAndFlush(any(Auth.class))).thenThrow(unrelated);

        assertThatThrownBy(() -> signupCommandService.signupLocal(EMAIL, PASSWORD, NAME, true))
                .isSameAs(unrelated);
    }
}
