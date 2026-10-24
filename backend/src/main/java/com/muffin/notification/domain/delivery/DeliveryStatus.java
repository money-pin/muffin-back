package com.muffin.notification.domain.delivery;

public enum DeliveryStatus {
    /** 발송 권한을 잡았고 아직 결과를 모른다. */
    PENDING,
    /** 기기 하나 이상에 전달됐다. */
    SENT,
    /** 전달된 기기가 없다. 재발송하지 않는다. */
    FAILED
}
