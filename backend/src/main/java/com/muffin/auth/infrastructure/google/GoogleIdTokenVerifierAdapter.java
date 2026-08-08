package com.muffin.auth.infrastructure.google;

import com.muffin.auth.application.GoogleProperties;
import com.muffin.auth.domain.GoogleIdTokenPayload;
import com.muffin.auth.domain.GoogleIdTokenVerifier;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/** GoogleIdTokenVerifier의 JWKS 기반 구현. 구글 공개키로 서명을 검증하고 발급자(iss)/대상(aud)을 대조한다. */
@Component
@RequiredArgsConstructor
public class GoogleIdTokenVerifierAdapter implements GoogleIdTokenVerifier {

    private static final Set<String> VALID_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final JwtDecoder googleJwtDecoder;
    private final GoogleProperties googleProperties;

    @Override
    public GoogleIdTokenPayload verify(String idToken) {
        Jwt jwt;
        try {
            jwt = googleJwtDecoder.decode(idToken);
        } catch (JwtException e) {
            throw new AuthException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
        }

        String issuer = jwt.getClaimAsString("iss");
        if (issuer == null || !VALID_ISSUERS.contains(issuer) || !audienceMatches(jwt.getAudience())) {
            throw new AuthException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
        }

        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (sub == null || email == null) {
            throw new AuthException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
        }

        return new GoogleIdTokenPayload(sub, email, jwt.getClaimAsString("name"));
    }

    private boolean audienceMatches(List<String> audience) {
        return audience != null && audience.contains(googleProperties.clientId());
    }
}
