package com.muffin.notification.application.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.notification.domain.delivery.DeliveryStatus;
import com.muffin.notification.domain.delivery.NotificationDelivery;
import com.muffin.notification.domain.delivery.NotificationDeliveryRepository;
import com.muffin.notification.domain.delivery.NotificationType;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 중복 방지 키와 발송 결과 기록을 실제 저장소로 검증한다. 외부 발송만 mock으로 둔다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class NotificationDispatcherTest {

    private static final PushMessage MESSAGE = new PushMessage("제목", "본문", "muffin://home");

    @Autowired
    private NotificationDispatcher dispatcher;

    @Autowired
    private NotificationDeliveryRepository deliveryRepository;

    @MockitoBean
    private PushSender pushSender;

    @AfterEach
    void cleanUp() {
        deliveryRepository.deleteAll();
    }

    @Test
    @DisplayName("보내고 나면 중복 방지 키와 함께 SENT로 기록한다")
    void recordsSent() {
        when(pushSender.send(1L, MESSAGE)).thenReturn(true);

        DispatchOutcome outcome = dispatcher.dispatch(NotificationType.NEWS, 1L, "2026-10-20", MESSAGE);

        assertThat(outcome).isEqualTo(DispatchOutcome.SENT);
        NotificationDelivery delivery = deliveryRepository.findAll().getFirst();
        assertThat(delivery.getDedupKey()).isEqualTo("NEWS:1:2026-10-20");
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThat(delivery.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 중복 방지 키로 다시 부르면 보내지 않는다")
    void skipsDuplicate() {
        when(pushSender.send(1L, MESSAGE)).thenReturn(true);

        dispatcher.dispatch(NotificationType.NEWS, 1L, "2026-10-20", MESSAGE);
        DispatchOutcome second = dispatcher.dispatch(NotificationType.NEWS, 1L, "2026-10-20", MESSAGE);

        assertThat(second).isEqualTo(DispatchOutcome.DUPLICATE);
        verify(pushSender, times(1)).send(anyLong(), any());
        assertThat(deliveryRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("전달하지 못하면 FAILED로 기록하고 다시 보내지 않는다")
    void recordsFailureOnce() {
        when(pushSender.send(1L, MESSAGE)).thenReturn(false);

        DispatchOutcome outcome = dispatcher.dispatch(NotificationType.QUIZ, 1L, "2026-10-20", MESSAGE);
        DispatchOutcome retry = dispatcher.dispatch(NotificationType.QUIZ, 1L, "2026-10-20", MESSAGE);

        assertThat(outcome).isEqualTo(DispatchOutcome.FAILED);
        assertThat(retry).isEqualTo(DispatchOutcome.DUPLICATE);
        NotificationDelivery delivery = deliveryRepository.findAll().getFirst();
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getFailureReason()).isEqualTo("send_failed");
    }

    @Test
    @DisplayName("발송기가 예외를 던져도 다른 사용자 발송은 이어지고 결과를 집계한다")
    void continuesAfterSenderException() {
        when(pushSender.send(1L, MESSAGE)).thenThrow(new IllegalStateException("boom"));
        when(pushSender.send(2L, MESSAGE)).thenReturn(true);

        NotificationDispatchSummary summary =
                dispatcher.dispatchAll(NotificationType.RANKING, "2026-W42", Map.of(1L, MESSAGE, 2L, MESSAGE));

        assertThat(summary.sent()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
    }
}
