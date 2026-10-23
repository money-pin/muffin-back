package com.muffin.auth.application.withdraw;

/** 카카오 계정과 우리 앱의 연결을 끊는 능력의 포트. 실제 API 호출은 infrastructure가 구현. */
public interface KakaoAccountUnlinker {

    void unlink(String kakaoUserId);
}
