package com.muffin.notification.infrastructure.apns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.muffin.notification.application.dispatch.PushSender;
import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import com.muffin.notification.infrastructure.LoggingPushSender;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class ApnsConfigTest {

    private final ApnsConfig config = new ApnsConfig();
    private final DeviceTokenRepository deviceTokenRepository = mock(DeviceTokenRepository.class);

    private PushSender pushSenderWith(ApnsProperties properties) {
        return config.pushSender(
                properties, RestClient.create(), deviceTokenRepository, mock(DeviceTokenDeliveryGuard.class));
    }

    @Test
    @DisplayName("APNS_PRIVATE_KEY가 비어 있으면 로그만 남기는 발송기를 쓴다")
    void withoutKey_usesLoggingSender() {
        assertThat(pushSenderWith(new ApnsProperties("", "", "", ""))).isInstanceOf(LoggingPushSender.class);
        assertThat(pushSenderWith(new ApnsProperties(null, null, null, null))).isInstanceOf(LoggingPushSender.class);
    }

    @Test
    @DisplayName("키와 나머지 값이 모두 있으면 APNs 발송기를 쓴다")
    void withKey_usesApnsSender() throws Exception {
        String key = ApnsTestKeys.base64P8(ApnsTestKeys.newKeyPair());

        assertThat(pushSenderWith(new ApnsProperties("TEAMID1234", "KEYID12345", key, "kr.ai.muffin")))
                .isInstanceOf(ApnsPushSender.class);
    }

    @Test
    @DisplayName("키는 있는데 Key ID가 비어 있으면 기동 시점에 실패한다")
    void withKeyButNoKeyId_fails() throws Exception {
        String key = ApnsTestKeys.base64P8(ApnsTestKeys.newKeyPair());

        assertThatThrownBy(() -> pushSenderWith(new ApnsProperties("TEAMID1234", "", key, "kr.ai.muffin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APNS_KEY_ID");
    }

    @Test
    @DisplayName("EC 개인키로 읽을 수 없는 키면 기동 시점에 실패한다")
    void invalidKey_fails() {
        String notAKey = Base64.getEncoder()
                .encodeToString("-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----".getBytes());

        assertThatThrownBy(
                        () -> pushSenderWith(new ApnsProperties("TEAMID1234", "KEYID12345", notAKey, "kr.ai.muffin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APNS_PRIVATE_KEY");
    }
}
