package com.muffin.notification.infrastructure.apns;

import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.application.dispatch.PushResult;
import com.muffin.notification.application.dispatch.PushSender;
import com.muffin.notification.domain.devicetoken.ApnsEnvironment;
import com.muffin.notification.domain.devicetoken.DeviceToken;
import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** 사용자의 기기 토큰으로 APNs에 알림을 보낸다. 앱은 알림을 탭하면 페이로드의 deepLink 화면을 연다. */
@Slf4j
@RequiredArgsConstructor
public class ApnsPushSender implements PushSender {

    private static final String PRODUCTION_HOST = "https://api.push.apple.com";
    private static final String SANDBOX_HOST = "https://api.sandbox.push.apple.com";

    private final RestClient apnsRestClient;
    private final ApnsProviderToken providerToken;
    private final String topic;
    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    public PushResult send(Long userId, PushMessage message) {
        Optional<DeviceToken> found = deviceTokenRepository.findByUserId(userId);
        if (found.isEmpty()) {
            return PushResult.NO_DEVICE;
        }
        DeviceToken device = found.get();

        try {
            apnsRestClient
                    .post()
                    .uri(hostOf(device.getEnvironment()) + "/3/device/{token}", device.getToken())
                    .header("authorization", "bearer " + providerToken.get())
                    .header("apns-topic", topic)
                    .header("apns-push-type", "alert")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload(message))
                    .retrieve()
                    .toBodilessEntity();
            return PushResult.SENT;
        } catch (HttpClientErrorException e) {
            if (isDeadToken(e)) {
                deviceTokenRepository.delete(device);
                return PushResult.NO_DEVICE;
            }
            if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                providerToken.invalidate();
            }
            log.warn(
                    "APNs rejected push: userId={} status={} body={}",
                    userId,
                    e.getStatusCode().value(),
                    e.getResponseBodyAsString());
            return PushResult.FAILED;
        }
    }

    private static String hostOf(ApnsEnvironment environment) {
        return switch (environment) {
            case PRODUCTION -> PRODUCTION_HOST;
            case SANDBOX -> SANDBOX_HOST;
        };
    }

    // 410은 앱 삭제 등으로 토큰이 무효해진 것이고, BadDeviceToken은 토큰 형식이나 환경(SANDBOX/PRODUCTION)이 틀린 것이다.
    // DeviceTokenNotForTopic 같은 다른 400은 우리 설정(번들 ID) 문제라 토큰을 지우지 않는다. 지우면 설정 실수 하나로 전원의 토큰이 사라진다.
    private static boolean isDeadToken(HttpClientErrorException e) {
        return e.getStatusCode() == HttpStatus.GONE
                || (e.getStatusCode() == HttpStatus.BAD_REQUEST
                        && e.getResponseBodyAsString().contains("\"BadDeviceToken\""));
    }

    private static Map<String, Object> payload(PushMessage message) {
        return Map.of(
                "aps",
                Map.of("alert", Map.of("title", message.title(), "body", message.body()), "sound", "default"),
                "deepLink",
                message.deepLink());
    }
}
