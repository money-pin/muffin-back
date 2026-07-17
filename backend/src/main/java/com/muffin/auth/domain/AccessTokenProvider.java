package com.muffin.auth.domain;

/** 도메인이 필요로 하는 Access Token 발급/검증 능력의 포트. 실제 구현(JWT)은 infrastructure가 담당. */
public interface AccessTokenProvider {

    String issue(Long userId, String role);

    Long parseUserId(String accessToken);
}
