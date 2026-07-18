package com.muffin.auth.application.emailverification;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.auth.domain.PasswordEncoder;
import com.muffin.auth.domain.emailverification.EmailVerification;
import com.muffin.auth.domain.emailverification.EmailVerificationRepository;
import com.muffin.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** verifyCode의 시도 횟수 증가가 실패(예외) 경로에서도 실제로 커밋되는지, 성공 시 Auth.emailVerified가 함께 갱신되는지 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class EmailVerificationCommandServiceTest {

    private static final String EMAIL = "verify-attempt@example.com";
    private static final String CODE = "123456";

    @Autowired
    private EmailVerificationCommandService emailVerificationCommandService;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailVerificationProperties properties;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = authRepository
                .save(Auth.createLocal(1L, EMAIL, "password1", "encoded"))
                .getUserId();
    }

    @AfterEach
    void cleanUp() {
        emailVerificationRepository.deleteAll();
        authRepository.deleteAll();
    }

    @Test
    @DisplayName("코드 불일치 시 attemptCount 증가가 예외에도 불구하고 커밋된다")
    void mismatchIncrementsAttemptCountDespiteException() {
        EmailVerification verification =
                emailVerificationRepository.save(EmailVerification.create(EMAIL, passwordEncoder.encode(CODE), 5));

        assertThatThrownBy(() -> emailVerificationCommandService.verifyCode(userId, "000000"))
                .isInstanceOf(GeneralException.class);

        EmailVerification reloaded = emailVerificationRepository
                .findById(verification.getEmailVerificationId())
                .orElseThrow();
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("최대 시도 횟수를 넘기면 이후 요청은 LOCKED로 거부된다(브루트포스 차단)")
    void locksAfterMaxAttempts() {
        emailVerificationRepository.save(EmailVerification.create(EMAIL, passwordEncoder.encode(CODE), 5));

        int maxAttempts = properties.maxAttempts();
        for (int i = 0; i < maxAttempts; i++) {
            assertThatThrownBy(() -> emailVerificationCommandService.verifyCode(userId, "000000"))
                    .isInstanceOf(GeneralException.class);
        }

        // 시도 횟수가 실제로 누적됐어야 잠금이 걸린다. 누적이 안 됐다면(회귀) 아래는 CODE_MISMATCH만 반복되고 LOCKED는 오지 않는다.
        assertThatThrownBy(() -> emailVerificationCommandService.verifyCode(userId, CODE))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_423_001"));
    }

    @Test
    @DisplayName("코드 일치 시 인증 성공 및 Auth.emailVerified가 true로 바뀐다")
    void verifySuccessMarksAuthEmailVerified() {
        emailVerificationRepository.save(EmailVerification.create(EMAIL, passwordEncoder.encode(CODE), 5));

        emailVerificationCommandService.verifyCode(userId, CODE);

        Auth reloaded = authRepository.findByUserId(userId).orElseThrow();
        assertThat(reloaded.isEmailVerified()).isTrue();
    }
}
