package com.muffin.notification.infrastructure.apns;

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
import lombok.RequiredArgsConstructor;

/**
 * APNs 인증용 ES256 JWT(provider token). 애플은 발급 1시간이 지난 토큰을 거절하고, 20분 안에 다시 만들면 429로 막는다. 그래서 한 번 만든 토큰을 50분
 * 동안 재사용한다.
 */
@RequiredArgsConstructor
class ApnsProviderToken {

    private static final Duration REUSE_FOR = Duration.ofMinutes(50);

    private final String teamId;
    private final String keyId;
    private final ECPrivateKey privateKey;
    private final Clock clock;

    private String token;
    private Instant issuedAt;

    synchronized String get() {
        Instant now = clock.instant();
        if (token == null || !now.isBefore(issuedAt.plus(REUSE_FOR))) {
            token = sign(now);
            issuedAt = now;
        }
        return token;
    }

    /** 애플이 토큰을 거절(403)했을 때 다음 발송에서 새로 만들게 한다. */
    synchronized void invalidate() {
        token = null;
    }

    private String sign(Instant now) {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(),
                new JWTClaimsSet.Builder()
                        .issuer(teamId)
                        .issueTime(Date.from(now))
                        .build());
        try {
            jwt.sign(new ECDSASigner(privateKey));
        } catch (JOSEException e) {
            throw new IllegalStateException("APNs provider token 서명에 실패했습니다.", e);
        }
        return jwt.serialize();
    }
}
