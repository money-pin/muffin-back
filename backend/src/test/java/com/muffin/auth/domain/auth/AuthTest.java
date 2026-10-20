package com.muffin.auth.domain.auth;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.domain.enums.AuthProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuthTest {

    @Nested
    @DisplayName("createGoogle")
    class CreateGoogle {

        @Test
        @DisplayName("정상 생성 시 provider=GOOGLE, providerUserId가 설정된다")
        void success() {
            Auth auth = Auth.createGoogle(1L, "test@example.com", "google-id-123");

            assertThat(auth.getProvider()).isEqualTo(AuthProvider.GOOGLE);
            assertThat(auth.getProviderUserId()).isEqualTo("google-id-123");
            assertThat(auth.getEmail()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("이메일이 없어도(null) 생성된다")
        void nullEmail() {
            Auth auth = Auth.createGoogle(1L, null, "google-id-123");

            assertThat(auth.getEmail()).isNull();
        }

        @Test
        @DisplayName("이메일 형식 오류 → IllegalArgumentException")
        void invalidEmail() {
            assertThatThrownBy(() -> Auth.createGoogle(1L, "not-an-email", "google-id-123"))
                    .isInstanceOf(IllegalArgumentException.class);
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
}
