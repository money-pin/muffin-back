package com.muffin.notification.infrastructure.apns;

import com.muffin.global.crypto.EcPrivateKeys;
import com.muffin.notification.application.dispatch.PushSender;
import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import com.muffin.notification.infrastructure.LoggingPushSender;
import java.net.http.HttpClient;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ApnsConfig {

    /** APNs는 HTTP/2만 받는다. 애플은 발송마다 새로 연결하지 말고 연결을 유지하라고 권장해 빈 하나를 재사용한다. */
    @Bean
    public RestClient apnsRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    /** 키가 없으면(로컬, 키 등록 전 배포) 로그만 남긴다. 키가 있는데 다른 값이 비었거나 키를 읽을 수 없으면 기동을 실패시킨다. */
    @Bean
    public PushSender pushSender(
            ApnsProperties properties,
            RestClient apnsRestClient,
            DeviceTokenRepository deviceTokenRepository,
            DeviceTokenDeliveryGuard deliveryGuard) {
        if (!properties.isConfigured()) {
            return new LoggingPushSender();
        }
        requireText(properties.keyId(), "APNS_KEY_ID");
        requireText(properties.teamId(), "APPLE_TEAM_ID");
        requireText(properties.topic(), "APPLE_BUNDLE_ID");

        ApnsProviderToken providerToken = new ApnsProviderToken(
                properties.teamId(), properties.keyId(), readPrivateKey(properties.privateKey()), Clock.systemUTC());
        return new ApnsPushSender(
                apnsRestClient, providerToken, properties.topic(), deviceTokenRepository, deliveryGuard);
    }

    private static ECPrivateKey readPrivateKey(String base64Pem) {
        try {
            return EcPrivateKeys.fromBase64Pem(base64Pem);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APNS_PRIVATE_KEY를 EC 개인키로 읽을 수 없습니다.", e);
        }
    }

    private static void requireText(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("APNS_PRIVATE_KEY가 설정되어 있으면 " + envName + "도 필요합니다.");
        }
    }
}
