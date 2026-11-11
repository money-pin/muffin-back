package com.muffin.auth.domain.deletedaccount;

import com.muffin.auth.domain.enums.AuthProvider;

/**
 * 소셜 계정 식별자(provider+sub)를 안전하게 해시하는 능력의 포트. 실제 알고리즘(HMAC-SHA256)은 infrastructure가 구현.
 * 탈퇴 시 저장과 가입 시 조회가 같은 값을 만들어야 하므로, 해시 입력 형식은 구현체 한 곳에서만 정한다.
 */
public interface IdentityHasher {

    String hash(AuthProvider provider, String providerUserId);
}
