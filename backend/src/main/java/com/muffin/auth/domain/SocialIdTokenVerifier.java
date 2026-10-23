package com.muffin.auth.domain;

import com.muffin.auth.domain.enums.AuthProvider;

/**
 * 도메인이 필요로 하는 소셜 로그인 ID Token 검증 능력의 포트. provider마다 다른 것은 이 검증(JWKS 주소, 발급자, 대상)뿐이라
 * provider별 구현체를 infrastructure에 두고, 가입/로그인 유스케이스는 하나를 공유한다.
 */
public interface SocialIdTokenVerifier {

    /** 이 구현체가 검증하는 provider. */
    AuthProvider provider();

    /** 서명, 발급자(iss), 대상(aud), 만료를 검증하고 클레임을 반환한다. 검증 실패 시 GeneralException(AUTH_401_004)을 던진다. */
    SocialIdentity verify(String idToken);
}
