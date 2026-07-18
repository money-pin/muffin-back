package com.muffin.auth.domain;

/** 도메인이 필요로 하는 구글 ID Token 검증 능력의 포트. 실제 서명/클레임 검증(JWKS)은 infrastructure가 구현. */
public interface GoogleIdTokenVerifier {

    /** 서명, 발급자(iss), 대상(aud), 만료를 검증하고 클레임을 반환한다. 검증 실패 시 GeneralException(AUTH_401_004)을 던진다. */
    GoogleIdTokenPayload verify(String idToken);
}
