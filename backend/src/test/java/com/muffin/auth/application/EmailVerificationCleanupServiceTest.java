package com.muffin.auth.application;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.domain.EmailVerification;
import com.muffin.auth.domain.EmailVerificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 만료된 미인증 레코드만 정리하고, 인증 성공(verified=true) 레코드는 만료돼도 남겨두는지 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
class EmailVerificationCleanupServiceTest {

    @Autowired
    private EmailVerificationCleanupService emailVerificationCleanupService;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @AfterEach
    void cleanUp() {
        emailVerificationRepository.deleteAll();
    }

    @Test
    @DisplayName("만료된 미인증 레코드는 삭제된다")
    void deletesExpiredUnverified() {
        EmailVerification expiredUnverified =
                emailVerificationRepository.save(EmailVerification.create("expired@example.com", "hash", -1));

        emailVerificationCleanupService.cleanupExpired();

        assertThat(emailVerificationRepository.findById(expiredUnverified.getEmailVerificationId()))
                .isEmpty();
    }

    @Test
    @DisplayName("아직 만료되지 않은 미인증 레코드는 남는다")
    void keepsNotYetExpiredUnverified() {
        EmailVerification notExpired =
                emailVerificationRepository.save(EmailVerification.create("valid@example.com", "hash", 5));

        emailVerificationCleanupService.cleanupExpired();

        assertThat(emailVerificationRepository.findById(notExpired.getEmailVerificationId()))
                .isPresent();
    }

    @Test
    @DisplayName("만료됐어도 인증 성공(verified=true)한 레코드는 남는다")
    void keepsExpiredVerified() {
        EmailVerification expiredVerified = EmailVerification.create("verified@example.com", "hash", -1);
        expiredVerified.markVerified();
        emailVerificationRepository.save(expiredVerified);

        emailVerificationCleanupService.cleanupExpired();

        assertThat(emailVerificationRepository.findById(expiredVerified.getEmailVerificationId()))
                .isPresent();
    }
}
