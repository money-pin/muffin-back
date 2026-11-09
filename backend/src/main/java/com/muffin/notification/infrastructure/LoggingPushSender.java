package com.muffin.notification.infrastructure;

import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.application.dispatch.PushResult;
import com.muffin.notification.application.dispatch.PushSender;
import lombok.extern.slf4j.Slf4j;

/**
 * APNs 키가 없는 환경(로컬, 키 등록 전 배포)에서 쓰는 발송기. 실제로 보내지 않고 로그만 남긴다. 빈 등록은 {@code ApnsConfig}가 키 유무로 정한다.
 *
 * <p>전달 성공으로 보고하는 이유는 발송 이력과 중복 방지 흐름을 실제 발송 없이도 그대로 확인하기 위해서다.
 */
@Slf4j
public class LoggingPushSender implements PushSender {

    @Override
    public PushResult send(Long userId, PushMessage message) {
        log.info(
                "Push send skipped (APNs key not configured): userId={} title={} deepLink={}",
                userId,
                message.title(),
                message.deepLink());
        return PushResult.SENT;
    }
}
