package com.muffin.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muffin.notification.domain.devicetoken.ApnsEnvironment;
import com.muffin.notification.domain.devicetoken.DeviceToken;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeviceTokenTest {

    @Test
    @DisplayName("사용자, 토큰, 환경으로 등록한다")
    void register() {
        DeviceToken deviceToken = DeviceToken.register(1L, "a1b2c3", ApnsEnvironment.PRODUCTION);

        assertThat(deviceToken.getUserId()).isEqualTo(1L);
        assertThat(deviceToken.getToken()).isEqualTo("a1b2c3");
        assertThat(deviceToken.getEnvironment()).isEqualTo(ApnsEnvironment.PRODUCTION);
    }

    @Test
    @DisplayName("토큰이 비어 있거나 사용자·환경이 없으면 만들 수 없다")
    void rejectsMissingValues() {
        assertThatThrownBy(() -> DeviceToken.register(1L, " ", ApnsEnvironment.SANDBOX))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeviceToken.register(null, "a1b2c3", ApnsEnvironment.SANDBOX))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DeviceToken.register(1L, "a1b2c3", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("종료 시각이 없으면 만료되지 않고, 종료 시각이 지나면 만료된다")
    void isExpired() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 9, 10, 0);
        DeviceToken current = DeviceToken.register(1L, "current", ApnsEnvironment.PRODUCTION);
        DeviceToken leaving = DeviceToken.register(1L, "leaving", ApnsEnvironment.PRODUCTION);
        ReflectionTestUtils.setField(leaving, "expiresAt", now);

        assertThat(current.isExpired(now)).isFalse();
        assertThat(leaving.isExpired(now.minusSeconds(1))).isFalse();
        assertThat(leaving.isExpired(now)).isTrue();
    }

    @Test
    @DisplayName("현재 기기로 만들면 주인과 환경이 바뀌고 종료 시각이 지워진다")
    void makeCurrentDeviceOf() {
        DeviceToken deviceToken = DeviceToken.register(1L, "a1b2c3", ApnsEnvironment.PRODUCTION);
        ReflectionTestUtils.setField(deviceToken, "expiresAt", LocalDateTime.of(2026, 11, 9, 10, 0));

        deviceToken.makeCurrentDeviceOf(2L, ApnsEnvironment.SANDBOX);

        assertThat(deviceToken.getUserId()).isEqualTo(2L);
        assertThat(deviceToken.getEnvironment()).isEqualTo(ApnsEnvironment.SANDBOX);
        assertThat(deviceToken.getExpiresAt()).isNull();
    }
}
