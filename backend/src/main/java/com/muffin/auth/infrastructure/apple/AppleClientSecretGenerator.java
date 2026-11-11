package com.muffin.auth.infrastructure.apple;

import com.muffin.auth.application.AppleProperties;
import com.muffin.global.crypto.EcPrivateKeys;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.ECPrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 애플 토큰 API의 client_secret(ES256 JWT)을 만든다. 탈퇴 때만 쓰므로 캐싱하지 않고 매번 짧은 유효시간으로 새로 서명한다
 * (애플 허용 최대치는 6개월).
 */
@Component
public class AppleClientSecretGenerator {

    private static final String AUDIENCE = "https://appleid.apple.com";
    private static final Duration LIFETIME = Duration.ofMinutes(5);

    private final AppleProperties appleProperties;
    private final ECPrivateKey privateKey;
    private final Clock clock;

    @Autowired
    public AppleClientSecretGenerator(AppleProperties appleProperties) {
        this(appleProperties, Clock.systemUTC());
    }

    AppleClientSecretGenerator(AppleProperties appleProperties, Clock clock) {
        this.appleProperties = appleProperties;
        this.privateKey = parsePrivateKey(appleProperties.privateKey());
        this.clock = clock;
    }

    public String generate() {
        if (privateKey == null) {
            throw new AppleClientSecretException("APPLE_PRIVATE_KEY가 설정되지 않았습니다.");
        }
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(appleProperties.teamId())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(LIFETIME)))
                .audience(AUDIENCE)
                .subject(appleProperties.bundleId())
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256)
                        .keyID(appleProperties.keyId())
                        .build(),
                claims);
        try {
            jwt.sign(new ECDSASigner(privateKey));
        } catch (JOSEException e) {
            throw new AppleClientSecretException("애플 client_secret 서명에 실패했습니다.", e);
        }
        return jwt.serialize();
    }

    /** 키가 비어 있으면(로컬) null로 두고 실제 호출 시점에 실패시킨다. */
    private static ECPrivateKey parsePrivateKey(String base64Pem) {
        if (base64Pem == null || base64Pem.isBlank()) {
            return null;
        }
        try {
            return EcPrivateKeys.fromBase64Pem(base64Pem);
        } catch (IllegalArgumentException e) {
            throw new AppleClientSecretException("APPLE_PRIVATE_KEY를 EC 개인키로 읽을 수 없습니다.", e);
        }
    }
}
