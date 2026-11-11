package com.muffin.notification.infrastructure;

import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.application.dispatch.PushResult;
import com.muffin.notification.application.dispatch.PushSender;
import lombok.extern.slf4j.Slf4j;

/**
 * APNs 키가 없는 환경(로컬, 키 등록 전 배포)에서 쓰는 발송기. 실제로 보내지 않고 로그만 남긴다. 빈 등록은 {@code ApnsConfig}가 키 유무로 정한다.
 *
 * <p>실제로 발송하지 않았으므로 실패로 기록한다.
 */
@Slf4j
public class LoggingPushSender implements PushSender {

    @Override
    public PushResult send(Long userId, PushMessage message) {
        log.warn(
                "Push send skipped (APNs key not configured): userId={} title={} deepLink={}",
                userId,
                message.sensitive() ? "[private]" : message.title(),
                message.deepLink());
        return PushResult.FAILED;
    }
}
