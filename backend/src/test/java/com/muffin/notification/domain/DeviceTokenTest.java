package com.muffin.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muffin.notification.domain.devicetoken.ApnsEnvironment;
import com.muffin.notification.domain.devicetoken.DeviceToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
