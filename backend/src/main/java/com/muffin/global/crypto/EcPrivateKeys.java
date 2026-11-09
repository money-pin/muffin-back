package com.muffin.global.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** 애플 .p8 키(애플 로그인·APNs)를 읽는다. 배포 env 파일은 여러 줄 값을 담을 수 없어 PEM 전체를 base64 한 줄로 감싸 둔다. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EcPrivateKeys {

    /** @throws IllegalArgumentException base64나 PKCS#8 EC 키로 읽을 수 없을 때 */
    public static ECPrivateKey fromBase64Pem(String base64Pem) {
        try {
            String pem = new String(Base64.getMimeDecoder().decode(base64Pem), StandardCharsets.UTF_8);
            String body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("EC 개인키로 읽을 수 없습니다.", e);
        }
    }
}
