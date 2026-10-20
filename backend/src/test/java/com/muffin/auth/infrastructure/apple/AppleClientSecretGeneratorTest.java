package com.muffin.auth.infrastructure.apple;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.application.AppleProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 실제 .p8 대신 테스트에서 만든 P-256 키로 서명하고, 짝인 공개키로 서명과 클레임을 검증한다. */
class AppleClientSecretGeneratorTest {

    private static final Instant NOW = Instant.parse("2026-10-20T00:00:00Z");

    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
    }

    /** 애플에서 받는 .p8과 같은 PKCS#8 PEM을 만들고, 배포 설정처럼 base64 한 줄로 감싼다. */
    private String base64P8() {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(keyPair.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        return Base64.getEncoder().encodeToString(pem.getBytes(StandardCharsets.UTF_8));
    }

    private AppleProperties propertiesWithKey(String privateKey) {
        return new AppleProperties(
                "kr.ai.muffin", "unused", "unused", "TEAMID1234", "KEYID12345", privateKey, "unused", null, null);
    }

    @Test
    @DisplayName("ES256 서명과 애플이 요구하는 헤더(kid)·클레임(iss/sub/aud/iat/exp)으로 client_secret을 만든다")
    void generate_producesAppleClientSecret() throws Exception {
        AppleClientSecretGenerator generator =
                new AppleClientSecretGenerator(propertiesWithKey(base64P8()), Clock.fixed(NOW, ZoneOffset.UTC));

        SignedJWT jwt = SignedJWT.parse(generator.generate());

        assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic())))
                .isTrue();
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo("KEYID12345");

        JWTClaimsSet claims = jwt.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("TEAMID1234");
        assertThat(claims.getSubject()).isEqualTo("kr.ai.muffin");
        assertThat(claims.getAudience()).isEqualTo(List.of("https://appleid.apple.com"));
        assertThat(claims.getIssueTime().toInstant()).isEqualTo(NOW);
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(NOW.plusSeconds(300));
    }

    @Test
    @DisplayName("키가 비어 있으면 생성은 되지만 client_secret을 만들 때 IllegalStateException")
    void generate_withoutKey_fails() {
        AppleClientSecretGenerator generator = new AppleClientSecretGenerator(propertiesWithKey(""));

        assertThatThrownBy(generator::generate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("설정되지 않았습니다");
    }

    @Test
    @DisplayName("EC 개인키로 읽을 수 없는 값이면 생성 시점에 IllegalStateException")
    void constructor_invalidKey_fails() {
        String notAKey = Base64.getEncoder()
                .encodeToString("-----BEGIN PRIVATE KEY-----\nAAAA\n-----END PRIVATE KEY-----".getBytes());

        assertThatThrownBy(() -> new AppleClientSecretGenerator(propertiesWithKey(notAKey)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EC 개인키로 읽을 수 없습니다");
    }

    @Test
    @DisplayName("base64가 아닌 값(패딩이 깨진 값)이어도 같은 IllegalStateException으로 감싼다")
    void constructor_brokenBase64_fails() {
        assertThatThrownBy(() -> new AppleClientSecretGenerator(propertiesWithKey("abc=d")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EC 개인키로 읽을 수 없습니다");
    }
}
