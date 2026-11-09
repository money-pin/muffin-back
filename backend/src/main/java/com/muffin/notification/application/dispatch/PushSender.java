package com.muffin.notification.application.dispatch;

/** 사용자에게 푸시를 보내는 포트. 기기 토큰 조회와 발송(APNs)은 인프라 어댑터가 맡고, APNs 키가 없는 환경에서는 로그만 남기는 구현을 쓴다. */
public interface PushSender {

    /**
     * 사용자의 기기(사용자당 1개)로 보낸다.
     *
     * @return 전달했으면 SENT, 등록된 기기가 없거나 무효해진 기기를 지웠으면 NO_DEVICE, 그 외 실패는 FAILED. NO_DEVICE는 배치 실패 판단에서
     *     빠지므로 발송 경로의 문제(인증·설정 오류 등)를 NO_DEVICE로 돌려주면 안 된다.
     */
    PushResult send(Long userId, PushMessage message);
}
