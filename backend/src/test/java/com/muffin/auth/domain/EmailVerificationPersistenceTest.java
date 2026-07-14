package com.muffin.auth.domain;

import static org.assertj.core.api.Assertions.*;

import com.muffin.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * isWithinCooldown()은 JPA Auditing이 채워주는 createdAt에 의존하므로, 순수 단위 테스트가 아니라 영속화 이후 값으로 검증해야 한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailVerificationPersistenceTest {

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    @Test
    @DisplayName("저장 직후에는 쿨다운 이내(true)")
    void withinCooldown() {
        EmailVerification saved = emailVerificationRepository.saveAndFlush(
                EmailVerification.create("test@example.com", "hashed-code", 5));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.isWithinCooldown(3600)).isTrue();
    }

    @Test
    @DisplayName("쿨다운 0초 → 저장 시점이 이미 지났으므로 false")
    void cooldownElapsed() {
        EmailVerification saved = emailVerificationRepository.saveAndFlush(
                EmailVerification.create("test@example.com", "hashed-code", 5));

        assertThat(saved.isWithinCooldown(0)).isFalse();
    }
}
