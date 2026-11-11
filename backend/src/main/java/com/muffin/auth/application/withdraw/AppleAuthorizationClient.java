package com.muffin.auth.application.withdraw;

/** 애플 토큰 API(교환·철회) 호출 능력의 포트. 실제 API 호출과 client_secret 서명은 infrastructure가 구현. */
public interface AppleAuthorizationClient {

    /** 클라이언트가 받은 authorization code를 애플 토큰으로 교환한다. code는 1회용이며 5분간 유효하다. */
    AppleTokens exchange(String authorizationCode);

    void revoke(String refreshToken);

    /** idToken은 교환 결과가 어느 애플 계정의 것인지(sub) 확인하는 데 쓴다. */
    record AppleTokens(String refreshToken, String idToken) {}
}
