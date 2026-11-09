package com.muffin.notification.application.dispatch;

/** 사용자 한 명에게 보낸 결과. */
public enum DispatchOutcome {
    SENT,
    /** 같은 중복 방지 키로 이미 보냈다. */
    DUPLICATE,
    FAILED
}
