package com.muffin.notification.application.dispatch;

/** 사용자에게 푸시를 보내는 포트. 기기 토큰 조회와 발송(APNs)은 인프라 어댑터가 맡고, APNs 키가 없는 환경에서는 로그만 남기는 구현을 쓴다. */
public interface PushSender {

    /**
     * 사용자의 기기(현재 기기와, 다른 기기 로그인으로 곧 로그아웃될 기기)로 보낸다. 기기 하나의 실패가 나머지 기기 발송을 막지 않아야 한다.
     *
     * @return 한 기기라도 전달했으면 SENT, 보낼 기기가 없거나 무효해진 기기를 지웠으면 NO_DEVICE, 그 외 실패는 FAILED. NO_DEVICE는 배치 실패
     *     판단에서 빠지므로 발송 경로의 문제(인증·설정 오류 등)를 NO_DEVICE로 돌려주면 안 된다.
     */
    PushResult send(Long userId, PushMessage message);
}
