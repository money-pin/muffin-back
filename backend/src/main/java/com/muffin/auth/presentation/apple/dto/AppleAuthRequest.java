package com.muffin.auth.presentation.apple.dto;

import com.muffin.user.domain.User;
import jakarta.validation.constraints.NotBlank;

/**
 * 애플 로그인 통합(가입/로그인) 요청. 기존 계정이면 로그인, 없으면 자동 가입한다. 애플 로그인 자체가 동의 절차를 거치므로
 * 서비스 약관도 자동 동의로 간주한다.
 *
 * @param idToken 애플 SDK가 발급한 identity token
 * @param givenName 이름. 애플이 최초 로그인 때만 주므로 이후 요청에서는 비어 있다.
 * @param familyName 성. givenName과 같다.
 */
public record AppleAuthRequest(@NotBlank String idToken, String givenName, String familyName) {

    /**
     * 성+이름 순서로 공백 없이 합친다. 합친 이름이 이름 길이 제한을 넘으면(주로 영문 이름) 이름(givenName)만 쓴다. 둘 다 비어
     * 있으면 null.
     */
    public String fullName() {
        String given = nullToEmpty(givenName);
        String full = nullToEmpty(familyName) + given;
        if (full.isEmpty()) {
            return null;
        }
        return full.length() > User.nameMaxLength() && !given.isEmpty() ? given : full;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.strip();
    }
}
