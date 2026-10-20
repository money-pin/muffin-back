package com.muffin.auth.infrastructure.kakao;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.muffin.auth.application.KakaoProperties;
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

/** 서명 검증(JWKS)은 kakaoJwtDecoder를 목킹해 건너뛰고, 그 이후의 클레임 대조 로직(iss/aud/sub)만 검증한다. */
@ExtendWith(MockitoExtension.class)
class KakaoIdTokenVerifierAdapterTest {

    private static final String APP_KEY = "test-kakao-native-app-key";
    private static final String ISSUER = "https://kauth.kakao.com";
    private static final String RAW_TOKEN = "raw-id-token";

    @Mock
    private JwtDecoder kakaoJwtDecoder;

    private KakaoIdTokenVerifierAdapter verifier;

    @BeforeEach
    void setUp() {
        verifier = new KakaoIdTokenVerifierAdapter(kakaoJwtDecoder, new KakaoProperties(APP_KEY, "unused", ISSUER));
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
    @DisplayName("서명/발급자/대상이 모두 유효하면 sub/email/nickname을 반환한다")
    void verify_success() {
        when(kakaoJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(Map.of(
                        "iss", ISSUER,
                        "aud", List.of(APP_KEY),
                        "sub", "3812345678",
                        "email", "user@example.com",
                        "nickname", "길동이")));

        SocialIdentity identity = verifier.verify(RAW_TOKEN);

        assertThat(identity.sub()).isEqualTo("3812345678");
        assertThat(identity.email()).isEqualTo("user@example.com");
        assertThat(identity.name()).isEqualTo("길동이");
    }

    @Test
    @DisplayName("email 클레임이 없어도 통과하고 email은 null이다")
    void verify_withoutEmail() {
        when(kakaoJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(Map.of("iss", ISSUER, "aud", List.of(APP_KEY), "sub", "3812345678")));

        SocialIdentity identity = verifier.verify(RAW_TOKEN);

        assertThat(identity.sub()).isEqualTo("3812345678");
        assertThat(identity.email()).isNull();
        assertThat(identity.name()).isNull();
    }

    @Test
    @DisplayName("서명 검증 자체가 실패하면(BadJwtException) INVALID_SOCIAL_TOKEN")
    void verify_signatureInvalid() {
        when(kakaoJwtDecoder.decode(RAW_TOKEN)).thenThrow(new BadJwtException("bad signature"));

        assertInvalidToken();
    }

    @Test
    @DisplayName("발급자(iss)가 카카오가 아니면 INVALID_SOCIAL_TOKEN")
    void verify_wrongIssuer() {
        when(kakaoJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(
                        Map.of("iss", "https://evil.example.com", "aud", List.of(APP_KEY), "sub", "3812345678")));

        assertInvalidToken();
    }

    @Test
    @DisplayName("대상(aud)이 우리 앱 키가 아니면 INVALID_SOCIAL_TOKEN")
    void verify_wrongAudience() {
        when(kakaoJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(Map.of("iss", ISSUER, "aud", List.of("other-app-key"), "sub", "3812345678")));

        assertInvalidToken();
    }

    @Test
    @DisplayName("sub가 없으면 INVALID_SOCIAL_TOKEN")
    void verify_missingSub() {
        when(kakaoJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(Map.of("iss", ISSUER, "aud", List.of(APP_KEY))));

        assertInvalidToken();
    }

    private void assertInvalidToken() {
        assertThatThrownBy(() -> verifier.verify(RAW_TOKEN))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_401_004"));
    }
}
