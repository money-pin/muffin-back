package com.muffin.notification.infrastructure;

import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.application.dispatch.PushSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 기기 토큰 연동 전까지 쓰는 발송기. 실제로 보내지 않고 로그만 남긴다.
 *
 * <p>전달 성공으로 보고하는 이유는 발송 이력과 중복 방지 흐름을 실제 발송 없이도 그대로 확인하기 위해서다.
 */
@Slf4j
@Component
public class LoggingPushSender implements PushSender {

    @Override
    public boolean send(Long userId, PushMessage message) {
        log.info(
                "Push send skipped (device token not integrated): userId={} title={} deepLink={}",
                userId,
                message.title(),
                message.deepLink());
        return true;
    }
}
