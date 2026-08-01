package com.muffin.auth.domain;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.domain.enums.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuthTest {

    @Nested
    @DisplayName("createLocal")
    class CreateLocal {

        @Test
        @DisplayName("정상 생성 시 provider=LOCAL, emailVerified=false")
        void success() {
            Auth auth = Auth.createLocal(1L, "test@example.com", "password1", "encoded");

            assertThat(auth.getProvider()).isEqualTo(AuthProvider.LOCAL);
            assertThat(auth.isEmailVerified()).isFalse();
            assertThat(auth.getEmail()).isEqualTo("test@example.com");
            assertThat(auth.getPasswordHash()).isEqualTo("encoded");
        }

        @Test
        @DisplayName("비밀번호 7자 → 실패")
        void passwordTooShort() {
            assertThatThrownBy(() -> Auth.createLocal(1L, "test@example.com", "abc1234", "encoded"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("비밀번호 8자(최솟값) → 성공")
        void passwordMinLength() {
            assertThatNoException().isThrownBy(() -> Auth.createLocal(1L, "test@example.com", "abc12345", "encoded"));
        }

        @Test
        @DisplayName("비밀번호 16자(최댓값) → 성공")
        void passwordMaxLength() {
            assertThatNoException()
                    .isThrownBy(() -> Auth.createLocal(1L, "test@example.com", "abcdefgh12345678", "encoded"));
        }

        @Test
        @DisplayName("비밀번호 17자 → 실패")
        void passwordTooLong() {
            assertThatThrownBy(() -> Auth.createLocal(1L, "test@example.com", "abcdefgh123456789", "encoded"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("영문만(숫자 없음) → 실패")
        void passwordNoDigit() {
            assertThatThrownBy(() -> Auth.createLocal(1L, "test@example.com", "abcdefgh", "encoded"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("숫자만(영문 없음) → 실패")
        void passwordNoLetter() {
            assertThatThrownBy(() -> Auth.createLocal(1L, "test@example.com", "12345678", "encoded"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("허용된 특수문자(! @ # $ % ^ & * ( ) - _ = + . ?) 포함 → 성공")
        void passwordAllowedSpecialChars() {
            assertThatNoException()
                    .isThrownBy(() -> Auth.createLocal(1L, "test@example.com", "ab1!@#$%^&*()-_=", "encoded"));
        }

        @Test
        @DisplayName("허용되지 않은 특수문자(~) 포함 → 실패")
        void passwordDisallowedSpecialChar() {
            assertThatThrownBy(() -> Auth.createLocal(1L, "test@example.com", "abc12345~", "encoded"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("이메일 형식 위반 → 실패")
        void invalidEmail() {
            assertThatThrownBy(() -> Auth.createLocal(1L, "not-an-email", "password1", "encoded"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null userId → NullPointerException")
        void nullUserId() {
            assertThatThrownBy(() -> Auth.createLocal(null, "test@example.com", "password1", "encoded"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null encodedPassword → NullPointerException")
        void nullEncodedPassword() {
            assertThatThrownBy(() -> Auth.createLocal(1L, "test@example.com", "password1", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("createGoogle")
    class CreateGoogle {

        @Test
        @DisplayName("정상 생성 시 provider=GOOGLE, emailVerified=true 자동 설정")
        void success() {
            Auth auth = Auth.createGoogle(1L, "test@example.com", "google-id-123");

            assertThat(auth.getProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(auth.isEmailVerified()).isTrue();
            assertThat(auth.getProviderUserId()).isEqualTo("google-id-123");
        }

        @Test
        @DisplayName("기존 LOCAL 계정 userId로 Google 연동(A 정책) → 성공")
        void linkToExistingLocalUser() {
            Long existingUserId = 10L;

            Auth auth = Auth.createGoogle(existingUserId, "test@example.com", "google-id-123");

            assertThat(auth.getUserId()).isEqualTo(existingUserId);
            assertThat(auth.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        }

        @Test
        @DisplayName("null providerUserId → NullPointerException")
        void nullProviderUserId() {
            assertThatThrownBy(() -> Auth.createGoogle(1L, "test@example.com", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null userId → NullPointerException")
        void nullUserId() {
            assertThatThrownBy(() -> Auth.createGoogle(null, "test@example.com", "google-id-123"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("verifyEmail")
    class VerifyEmail {

        @Test
        @DisplayName("이메일 인증 처리 → emailVerified=true")
        void success() {
            Auth auth = Auth.createLocal(1L, "test@example.com", "password1", "encoded");
            assertThat(auth.isEmailVerified()).isFalse();

            auth.verifyEmail();

            assertThat(auth.isEmailVerified()).isTrue();
        }
    }

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        @Test
        @DisplayName("정상 변경 → passwordHash 교체")
        void success() {
            Auth auth = Auth.createLocal(1L, "test@example.com", "password1", "encoded_old");

            auth.changePassword("newPass1", "encoded_new");

            assertThat(auth.getPasswordHash()).isEqualTo("encoded_new");
        }

        @Test
        @DisplayName("GOOGLE 계정은 비밀번호 변경 불가 → IllegalStateException")
        void cannotChangePasswordForSocialAccount() {
            Auth auth = Auth.createGoogle(1L, "test@example.com", "google-id-123");

            assertThatThrownBy(() -> auth.changePassword("newPass1", "encoded_new"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("비밀번호 형식 위반 → IllegalArgumentException")
        void invalidPasswordFormat() {
            Auth auth = Auth.createLocal(1L, "test@example.com", "password1", "encoded");

            assertThatThrownBy(() -> auth.changePassword("short", "encoded_new"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null encodedPassword → NullPointerException")
        void nullEncodedPassword() {
            Auth auth = Auth.createLocal(1L, "test@example.com", "password1", "encoded");

            assertThatThrownBy(() -> auth.changePassword("newPass1", null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("로그인 실패 잠금")
    class LoginLockout {

        @Test
        @DisplayName("생성 직후에는 잠겨있지 않고 실패 횟수가 0이다")
        void notLockedInitially() {
            Auth auth = Auth.createLocal(1L, "test@example.com", "password1", "encoded");

            assertThat(auth.isLoginLocked()).isFalse();
            assertThat(auth.getFailedLoginAttempts()).isZero();
        }

        @Test
        @DisplayName("실패 횟수가 임계치 미만이면 잠기지 않는다")
        void notLockedBelowThreshold() {
            Auth auth = Auth.createLocal(1L, "test@example.com", "password1", "encoded");

            for (int i = 0; i < 4; i++) {
                auth.recordFailedLogin(5, 30);
            }

            assertThat(auth.isLoginLocked()).isFalse();
        }

        @Test
        @DisplayName("실패 횟수가 임계치에 도달하면 잠긴다")
        void locksAtThreshold() {
            Auth auth = Auth.createLocal(1L, "test@example.com", "password1", "encoded");

            for (int i = 0; i < 5; i++) {
                auth.recordFailedLogin(5, 30);
            }

            assertThat(auth.isLoginLocked()).isTrue();
        }

        @Test
        @DisplayName("잠금 시간이 지나면 자동으로 해제된 것으로 판단한다(lockMinutes=0으로 즉시 만료 재현)")
        void lockExpiresAfterDuration() {
            Auth auth = Auth.createLocal(1L, "test@example.com", "password1", "encoded");

            auth.recordFailedLogin(1, 0);

            assertThat(auth.isLoginLocked()).isFalse();
        }

        @Test
        @DisplayName("잠금이 만료된 뒤 한 번만 더 틀려도 곧바로 재잠기지 않는다(만료 시 실패 횟수 초기화)")
        void doesNotRelockImmediatelyAfterExpiry() {
            Auth auth = Auth.createLocal(1L, "test@example.com", "password1", "encoded");
            for (int i = 0; i < 5; i++) {
                auth.recordFailedLogin(5, 0); // lockMinutes=0으로 매번 즉시 만료된 상태 재현
            }
            assertThat(auth.isLoginLocked()).isFalse(); // 잠금은 이미 만료됨
            assertThat(auth.getFailedLoginAttempts()).isEqualTo(5);

            auth.recordFailedLogin(5, 30);

            assertThat(auth.isLoginLocked()).isFalse();
            assertThat(auth.getFailedLoginAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("resetLoginAttempts 호출 시 실패 횟수와 잠금이 모두 해제된다")
        void resetClearsLockAndCount() {
            Auth auth = Auth.createLocal(1L, "test@example.com", "password1", "encoded");
            for (int i = 0; i < 5; i++) {
                auth.recordFailedLogin(5, 30);
            }
            assertThat(auth.isLoginLocked()).isTrue();

            auth.resetLoginAttempts();

            assertThat(auth.isLoginLocked()).isFalse();
        }
    }
}
