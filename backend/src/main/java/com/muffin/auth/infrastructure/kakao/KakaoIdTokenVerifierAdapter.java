package com.muffin.auth.infrastructure.kakao;

import com.muffin.auth.application.KakaoProperties;
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
 * 카카오 ID Token 검증의 JWKS 기반 구현. 카카오 공개키로 서명을 검증하고 발급자(iss)/대상(aud)을 대조한다. 구글과 달리 email은
 * 필수가 아니다(비즈 앱이 아니면 카카오가 email을 주지 않음).
 */
@Component
@RequiredArgsConstructor
public class KakaoIdTokenVerifierAdapter implements SocialIdTokenVerifier {

    private final JwtDecoder kakaoJwtDecoder;
    private final KakaoProperties kakaoProperties;

    @Override
    public AuthProvider provider() {
        return AuthProvider.KAKAO;
    }

    @Override
    public SocialIdentity verify(String idToken) {
        Jwt jwt;
        try {
            jwt = kakaoJwtDecoder.decode(idToken);
        } catch (JwtException e) {
            throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }

        if (!kakaoProperties.issuer().equals(jwt.getClaimAsString("iss")) || !audienceMatches(jwt.getAudience())) {
            throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }

        String sub = jwt.getSubject();
        if (sub == null) {
            throw new AuthException(AuthErrorCode.INVALID_SOCIAL_TOKEN);
        }

        return new SocialIdentity(sub, jwt.getClaimAsString("email"), jwt.getClaimAsString("nickname"));
    }

    private boolean audienceMatches(List<String> audience) {
        return audience != null && audience.contains(kakaoProperties.nativeAppKey());
    }
}
