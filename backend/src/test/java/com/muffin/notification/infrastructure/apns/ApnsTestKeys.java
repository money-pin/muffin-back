package com.muffin.notification.infrastructure.apns;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 실제 .p8 대신 테스트에서 만든 P-256 키. 배포 설정처럼 PKCS#8 PEM을 base64 한 줄로 감싼다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ApnsTestKeys {

    static KeyPair newKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    static String base64P8(KeyPair keyPair) {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    }
}
