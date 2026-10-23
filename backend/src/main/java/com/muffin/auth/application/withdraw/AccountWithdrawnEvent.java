package com.muffin.auth.application.withdraw;

import com.muffin.auth.domain.enums.AuthProvider;

/**
 * 탈퇴 커밋 후 provider 쪽 연결 해제에 쓴다. Auth의 sub는 탈퇴 시 익명화되므로 원본 sub를 담아 보낸다. appleRefreshToken은
 * 애플 계정일 때만 있다.
 */
public record AccountWithdrawnEvent(AuthProvider provider, String providerUserId, String appleRefreshToken) {

    /** 이벤트가 로그에 찍혀도 철회 전까지 유효한 애플 refresh token이 새지 않게 가린다. */
    @Override
    public String toString() {
        return "AccountWithdrawnEvent[provider=" + provider + ", providerUserId=" + providerUserId
                + ", appleRefreshToken=" + (appleRefreshToken == null ? "null" : "***") + "]";
    }
}
