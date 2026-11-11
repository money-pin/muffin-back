package com.muffin.auth.infrastructure.apple;

import com.muffin.auth.application.AppleProperties;
import com.muffin.auth.domain.SocialIdTokenVerifier;
import com.muffin.auth.domain.SocialIdentity;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * 애플 ID Token 검증의 JWKS 기반 구현. 애플 공개키로 서명을 검증하고 발급자(iss)/대상(aud)을 대조한다. email은 필수가 아니고,
 * 애플 토큰에는 이름이 없어 name은 항상 null이다(이름은 클라이언트가 첫 로그인 때 따로 보낸다).
 */
@Component
@RequiredArgsConstructor
public class AppleIdTokenVerifierAdapter implements SocialIdTokenVerifier {

    private final JwtDecoder appleJwtDecoder;
    private final AppleProperties appleProperties;

    @Override
    public AuthProvider provider() {
        return AuthProvider.APPLE;
    }

    @Override
    public SocialIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = appleJwtDecoder.decode(idToken);
        } catch (JwtException e) {
            throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }

        if (!appleProperties.issuer().equals(jwt.getClaimAsString("iss")) || !audienceMatches(jwt.getAudience())) {
            throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }

        String sub = jwt.getSubject();
        if (sub == null) {
            throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }

        return new SocialIdentity(sub, jwt.getClaimAsString("email"), null);
    }

    private boolean audienceMatches(List<String> audience) {
        return audience != null && audience.contains(appleProperties.bundleId());
    }
}
