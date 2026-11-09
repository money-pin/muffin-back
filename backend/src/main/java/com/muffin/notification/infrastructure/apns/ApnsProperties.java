package com.muffin.notification.infrastructure.apns;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code muffin.push.apns.*} 설정값 바인딩. privateKey가 비어 있으면 APNs로 보내지 않고 로그만 남긴다({@link ApnsConfig}).
 *
 * @param topic 앱 번들 ID
 */
@ConfigurationProperties(prefix = "muffin.push.apns")
public record ApnsProperties(String teamId, String keyId, String privateKey, String topic) {

    public boolean isConfigured() {
        return privateKey != null && !privateKey.isBlank();
    }
}
