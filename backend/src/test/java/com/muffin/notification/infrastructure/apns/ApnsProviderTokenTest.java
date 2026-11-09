package com.muffin.notification.infrastructure.apns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApnsProviderTokenTest {

    private static final Instant NOW = Instant.parse("2026-11-09T00:00:00Z");

    private KeyPair keyPair;
    private Clock clock;
    private ApnsProviderToken providerToken;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = ApnsTestKeys.newKeyPair();
        clock = mock(Clock.class);
        providerToken = new ApnsProviderToken("TEAMID1234", "KEYID12345", (ECPrivateKey) keyPair.getPrivate(), clock);
    }

    @Test
    @DisplayName("ES256으로 서명하고 헤더 kid, 클레임 iss·iat를 애플 형식으로 담는다")
    void get_signsAppleProviderToken() throws Exception {
        when(clock.instant()).thenReturn(NOW);

        SignedJWT jwt = SignedJWT.parse(providerToken.get());

        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())))
                .isTrue();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEYID12345");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("TEAMID1234");
        assertThat(jwt.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("50분 안에는 같은 토큰을 재사용하고, 50분이 지나면 새로 만든다")
    void get_reusesFor50Minutes() {
        when(clock.instant()).thenReturn(NOW, NOW.plus(Duration.ofMinutes(49)), NOW.plus(Duration.ofMinutes(50)));

        String first = providerToken.get();
        String within = providerToken.get();
        String after = providerToken.get();

        assertThat(within).isEqualTo(first);
        assertThat(after).isNotEqualTo(first);
    }

    @Test
    @DisplayName("무효화하면 시간이 지나지 않았어도 다음 호출에서 새로 만든다")
    void invalidate_forcesNewToken() {
        when(clock.instant()).thenReturn(NOW);
        String first = providerToken.get();

        providerToken.invalidate();

        assertThat(providerToken.get()).isNotEqualTo(first);
    }
}
