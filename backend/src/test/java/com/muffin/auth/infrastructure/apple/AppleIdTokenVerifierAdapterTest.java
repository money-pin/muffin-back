package com.muffin.auth.infrastructure.apple;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.muffin.auth.application.AppleProperties;
import com.muffin.auth.domain.SocialIdentity;
import com.muffin.global.apiPayload.exception.GeneralException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** 서명 검증(JWKS)은 appleJwtDecoder를 목킹해 건너뛰고, 그 이후의 클레임 대조 로직(iss/aud/sub)만 검증한다. */
@ExtendWith(MockitoExtension.class)
class AppleIdTokenVerifierAdapterTest {

    private static final String BUNDLE_ID = "test.apple.bundle";
    private static final String ISSUER = "https://appleid.apple.com";
    private static final String RAW_TOKEN = "raw-id-token";
    private static final String SUB = "001234.abcdef0123456789.0123";

    @Mock
    private JwtDecoder appleJwtDecoder;

    private AppleIdTokenVerifierAdapter verifier;

    @BeforeEach
    void setUp() {
        verifier = new AppleIdTokenVerifierAdapter(appleJwtDecoder, new AppleProperties(BUNDLE_ID, "unused", ISSUER));
    }

    private Jwt jwtWithClaims(Map<String, Object> claims) {
        return Jwt.withTokenValue(RAW_TOKEN)
                .header("alg", "RS256")
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("서명/발급자/대상이 모두 유효하면 sub/email을 반환하고 name은 null이다")
    void verify_success() {
        when(appleJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(Map.of(
                        "iss",
                        ISSUER,
                        "aud",
                        List.of(BUNDLE_ID),
                        "sub",
                        SUB,
                        "email",
                        "abc@privaterelay.appleid.com")));

        SocialIdentity identity = verifier.verify(RAW_TOKEN);

        assertThat(identity.sub()).isEqualTo(SUB);
        assertThat(identity.email()).isEqualTo("abc@privaterelay.appleid.com");
        assertThat(identity.name()).isNull();
    }

    @Test
    @DisplayName("email 클레임이 없어도 통과한다")
    void verify_withoutEmail() {
        when(appleJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(Map.of("iss", ISSUER, "aud", List.of(BUNDLE_ID), "sub", SUB)));

        assertThat(verifier.verify(RAW_TOKEN).email()).isNull();
    }

    @Test
    @DisplayName("서명 검증 자체가 실패하면(BadJwtException) INVALID_SOCIAL_TOKEN")
    void verify_signatureInvalid() {
        when(appleJwtDecoder.decode(RAW_TOKEN)).thenThrow(new BadJwtException("bad signature"));

        assertInvalidToken();
    }

    @Test
    @DisplayName("발급자(iss)가 애플이 아니면 INVALID_SOCIAL_TOKEN")
    void verify_wrongIssuer() {
        when(appleJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(
                        Map.of("iss", "https://evil.example.com", "aud", List.of(BUNDLE_ID), "sub", SUB)));

        assertInvalidToken();
    }

    @Test
    @DisplayName("대상(aud)이 우리 Bundle ID가 아니면 INVALID_SOCIAL_TOKEN")
    void verify_wrongAudience() {
        when(appleJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(Map.of("iss", ISSUER, "aud", List.of("other.bundle"), "sub", SUB)));

        assertInvalidToken();
    }

    @Test
    @DisplayName("sub가 없으면 INVALID_SOCIAL_TOKEN")
    void verify_missingSub() {
        when(appleJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(Map.of("iss", ISSUER, "aud", List.of(BUNDLE_ID))));

        assertInvalidToken();
    }

    private void assertInvalidToken() {
        assertThatThrownBy(() -> verifier.verify(RAW_TOKEN))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_401_004"));
    }
}
