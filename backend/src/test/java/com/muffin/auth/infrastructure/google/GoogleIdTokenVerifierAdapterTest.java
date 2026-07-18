package com.muffin.auth.infrastructure.google;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.muffin.auth.application.GoogleProperties;
import com.muffin.auth.domain.GoogleIdTokenPayload;
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

/**
 * 실제 구글 서명 검증(JWKS)은 흉내낼 수 없으니, 서명 검증을 통과한 이후의 클레임 대조 로직(iss/aud/sub/email)만
 * 검증한다. googleJwtDecoder를 목킹해 decode()가 반환하는 Jwt를 직접 구성한다.
 */
@ExtendWith(MockitoExtension.class)
class GoogleIdTokenVerifierAdapterTest {

    private static final String CLIENT_ID = "test-google-client-id";
    private static final String RAW_TOKEN = "raw-id-token";

    @Mock
    private JwtDecoder googleJwtDecoder;

    private GoogleIdTokenVerifierAdapter verifier;

    @BeforeEach
    void setUp() {
        verifier = new GoogleIdTokenVerifierAdapter(googleJwtDecoder, new GoogleProperties(CLIENT_ID));
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
    @DisplayName("서명/발급자/대상이 모두 유효하면 sub/email/name을 반환한다")
    void verify_success() {
        when(googleJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(Map.of(
                        "iss", "https://accounts.google.com",
                        "aud", List.of(CLIENT_ID),
                        "sub", "google-sub-1",
                        "email", "user@example.com",
                        "name", "홍길동")));

        GoogleIdTokenPayload payload = verifier.verify(RAW_TOKEN);

        assertThat(payload.sub()).isEqualTo("google-sub-1");
        assertThat(payload.email()).isEqualTo("user@example.com");
        assertThat(payload.name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("서명 검증 자체가 실패하면(BadJwtException) INVALID_GOOGLE_TOKEN")
    void verify_signatureInvalid() {
        when(googleJwtDecoder.decode(RAW_TOKEN)).thenThrow(new BadJwtException("bad signature"));

        assertThatThrownBy(() -> verifier.verify(RAW_TOKEN))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_401_004"));
    }

    @Test
    @DisplayName("발급자(iss)가 구글이 아니면 INVALID_GOOGLE_TOKEN")
    void verify_wrongIssuer() {
        when(googleJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(Map.of(
                        "iss", "https://evil.example.com",
                        "aud", List.of(CLIENT_ID),
                        "sub", "google-sub-1",
                        "email", "user@example.com")));

        assertThatThrownBy(() -> verifier.verify(RAW_TOKEN))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_401_004"));
    }

    @Test
    @DisplayName("대상(aud)이 우리 클라이언트가 아니면 INVALID_GOOGLE_TOKEN")
    void verify_wrongAudience() {
        when(googleJwtDecoder.decode(RAW_TOKEN))
                .thenReturn(jwtWithClaims(Map.of(
                        "iss", "https://accounts.google.com",
                        "aud", List.of("other-client-id"),
                        "sub", "google-sub-1",
                        "email", "user@example.com")));

        assertThatThrownBy(() -> verifier.verify(RAW_TOKEN))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode().getCode())
                        .isEqualTo("AUTH_401_004"));
    }
}
