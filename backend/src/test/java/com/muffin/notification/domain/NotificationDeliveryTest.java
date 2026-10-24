package com.muffin.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muffin.notification.domain.delivery.DeliveryStatus;
import com.muffin.notification.domain.delivery.NotificationDelivery;
import com.muffin.notification.domain.delivery.NotificationType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationDeliveryTest {

    @Test
    @DisplayName("중복 방지 키는 기획서의 PREFIX:user_id:scope 형식이다")
    void dedupKeyFormat() {
        assertThat(NotificationType.INVESTMENT_RESULT.dedupKey(7L, "2026-10-16"))
                .isEqualTo("RESULT:7:2026-10-16");
        assertThat(NotificationType.RANKING.dedupKey(7L, "2026-W42")).isEqualTo("RANK:7:2026-W42");
    }

    @Test
    @DisplayName("발송 대기 상태에서만 결과를 한 번 기록할 수 있다")
    void recordsResultOnce() {
        NotificationDelivery delivery = NotificationDelivery.reserve(
                1L, NotificationType.NEWS, "NEWS:1:2026-10-20", "제목", "본문", "muffin://home");

        delivery.sent(LocalDateTime.of(2026, 10, 20, 9, 0));

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.SENT);
        assertThatThrownBy(() -> delivery.failed("send_failed")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("문구나 이동 화면이 비어 있으면 만들 수 없다")
    void rejectsBlankContent() {
        assertThatThrownBy(() -> NotificationDelivery.reserve(1L, NotificationType.NEWS, "NEWS:1:d", "제목", " ", "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
