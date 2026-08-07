package com.muffin.auth.domain.refreshtoken;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("만료시각 이전이면 isExpired는 false")
    void isExpired_beforeExpiry_false() {
        RefreshToken token =
                RefreshToken.issue(1L, "hash", LocalDateTime.now(KST).plusDays(1));

        assertThat(token.isExpired()).isFalse();
    }

    @Test
    @DisplayName("만료시각이 지났으면 isExpired는 true")
    void isExpired_afterExpiry_true() {
        RefreshToken token =
                RefreshToken.issue(1L, "hash", LocalDateTime.now(KST).minusSeconds(1));

        assertThat(token.isExpired()).isTrue();
    }

    @Test
    @DisplayName("만료시각과 현재 시각이 같으면 isExpired는 true(경계값 포함)")
    void isExpired_atExpiry_true() {
        LocalDateTime now = LocalDateTime.now(KST);
        RefreshToken token = RefreshToken.issue(1L, "hash", now);

        assertThat(token.isExpired()).isTrue();
    }

    @Test
    @DisplayName("rotate하면 해시값과 만료시각이 새 값으로 교체된다")
    void rotate_replacesHashAndExpiry() {
        RefreshToken token =
                RefreshToken.issue(1L, "old-hash", LocalDateTime.now(KST).plusDays(1));

        LocalDateTime newExpiry = LocalDateTime.now(KST).plusDays(30);
        token.rotate("new-hash", newExpiry);

        assertThat(token.getTokenHash()).isEqualTo("new-hash");
        assertThat(token.getExpiresAt()).isEqualTo(newExpiry);
    }
}
