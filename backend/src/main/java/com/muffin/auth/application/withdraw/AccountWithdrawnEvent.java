package com.muffin.auth.application.withdraw;

import com.muffin.auth.domain.enums.AuthProvider;

/** 탈퇴 커밋 후 provider 쪽 연결 해제에 쓴다. Auth의 sub는 탈퇴 시 익명화되므로 원본 sub를 담아 보낸다. */
public record AccountWithdrawnEvent(AuthProvider provider, String providerUserId) {}
