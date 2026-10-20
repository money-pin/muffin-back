package com.muffin.notification.application.dispatch;

/**
 * 사용자에게 푸시를 보내는 포트. 기기 토큰 조회와 발송 채널(APNs 등)은 인프라 어댑터가 맡는다.
 *
 * <p>기기 토큰 연동 전까지는 로그만 남기는 구현을 쓴다. 토큰 연동이 붙으면 이 구현만 바뀌고 알림 정책·중복 방지·이력 기록은 그대로다.
 */
public interface PushSender {

    /**
     * 사용자의 기기로 보낸다. 기기 하나의 실패가 나머지 기기 발송을 막지 않아야 한다.
     *
     * @return 기기 하나 이상에 전달했으면 {@code true}
     */
    boolean send(Long userId, PushMessage message);
}
