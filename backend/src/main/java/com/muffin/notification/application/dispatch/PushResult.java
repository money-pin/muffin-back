package com.muffin.notification.application.dispatch;

/** 발송기({@link PushSender})가 사용자 한 명에게 보낸 결과. */
public enum PushResult {
    /** 기기 하나 이상에 전달했다. */
    SENT,
    /** 등록된 기기가 없다. 알림 권한 거부·로그아웃·앱 삭제 등으로 늘 생기므로 발송 경로의 실패와 구분한다. */
    NO_DEVICE,
    FAILED
}
