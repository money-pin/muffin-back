package com.muffin.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.application.dispatch.PushResult;
import org.junit.jupiter.api.Test;

class LoggingPushSenderTest {
    @Test
    void unconfiguredApnsIsNotReportedAsSent() {
        assertThat(new LoggingPushSender().send(1L, new PushMessage("title", "body", "muffin://home")))
                .isEqualTo(PushResult.FAILED);
    }
}
