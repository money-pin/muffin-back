package com.muffin.auth.domain.emailverification;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EmailVerificationTest {

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("정상 생성 시 verified=false, attemptCount=0")
        void success() {
            EmailVerification verification = EmailVerification.create("test@example.com", "hashed-code", 5);

            assertThat(verification.getEmail()).isEqualTo("test@example.com");
            assertThat(verification.getCodeHash()).isEqualTo("hashed-code");
            assertThat(verification.isVerified()).isFalse();
            assertThat(verification.getAttemptCount()).isZero();
            assertThat(verification.isExpired()).isFalse();
        }

        @Test
        @DisplayName("null email → NullPointerException")
        void nullEmail() {
            assertThatThrownBy(() -> EmailVerification.create(null, "hashed-code", 5))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null codeHash → NullPointerException")
        void nullCodeHash() {
            assertThatThrownBy(() -> EmailVerification.create("test@example.com", null, 5))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("isExpired")
    class IsExpired {

        @Test
        @DisplayName("만료시각 이전 → false")
        void notExpired() {
            EmailVerification verification = EmailVerification.create("test@example.com", "hashed-code", 5);

            assertThat(verification.isExpired()).isFalse();
        }

        @Test
        @DisplayName("만료시각 경과(0분) → true")
        void expired() {
            EmailVerification verification = EmailVerification.create("test@example.com", "hashed-code", 0);

            assertThat(verification.isExpired()).isTrue();
        }
    }

    @Nested
    @DisplayName("isLocked")
    class IsLocked {

        @Test
        @DisplayName("시도 횟수가 최대 미만 → false")
        void notLocked() {
            EmailVerification verification = EmailVerification.create("test@example.com", "hashed-code", 5);

            verification.increaseAttemptCount();

            assertThat(verification.isLocked(5)).isFalse();
        }

        @Test
        @DisplayName("시도 횟수가 최대 도달 → true")
        void locked() {
            EmailVerification verification = EmailVerification.create("test@example.com", "hashed-code", 5);

            for (int i = 0; i < 5; i++) {
                verification.increaseAttemptCount();
            }

            assertThat(verification.isLocked(5)).isTrue();
        }
    }

    @Nested
    @DisplayName("markVerified")
    class MarkVerified {

        @Test
        @DisplayName("인증 처리 → verified=true")
        void success() {
            EmailVerification verification = EmailVerification.create("test@example.com", "hashed-code", 5);

            verification.markVerified();

            assertThat(verification.isVerified()).isTrue();
        }
    }
}
