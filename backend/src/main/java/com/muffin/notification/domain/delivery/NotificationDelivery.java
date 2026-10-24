package com.muffin.notification.domain.delivery;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 한 명에게 보낸 알림 한 건의 이력.
 *
 * <p>{@code dedup_key}의 유니크 제약이 중복 발송을 막는 락이다. 발송 전에 PENDING으로 먼저 저장해 권한을 잡고, 저장에 실패하면 이미 다른 실행이 보낸
 * 것으로 본다. 그래서 배치 재실행이나 이벤트와 스케줄러가 겹쳐도 사용자는 알림을 한 번만 받는다.
 */
@Entity
@Getter
@Table(
        name = "notification_delivery",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_delivery_dedup_key", columnNames = "dedup_key"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_delivery_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "dedup_key", nullable = false, length = 100)
    private String dedupKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "body", nullable = false, length = 255)
    private String body;

    @Column(name = "deep_link", nullable = false, length = 255)
    private String deepLink;

    @Column(name = "failure_reason", length = 50)
    private String failureReason;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    private NotificationDelivery(
            Long userId, NotificationType type, String dedupKey, String title, String body, String deepLink) {
        this.userId = userId;
        this.type = type;
        this.dedupKey = dedupKey;
        this.title = title;
        this.body = body;
        this.deepLink = deepLink;
        this.status = DeliveryStatus.PENDING;
    }

    public static NotificationDelivery reserve(
            Long userId, NotificationType type, String dedupKey, String title, String body, String deepLink) {
        if (userId == null || type == null) {
            throw new IllegalArgumentException("userId와 type은 필수입니다.");
        }
        if (isBlank(dedupKey) || isBlank(title) || isBlank(body) || isBlank(deepLink)) {
            throw new IllegalArgumentException("dedupKey, title, body, deepLink는 필수입니다.");
        }
        return new NotificationDelivery(userId, type, dedupKey, title, body, deepLink);
    }

    public void sent(LocalDateTime sentAt) {
        requirePending();
        this.status = DeliveryStatus.SENT;
        this.sentAt = sentAt;
    }

    /** @param reason 실패 사유. 값의 종류가 유한한 짧은 토큰으로 적는다(no_device, send_failed 등). */
    public void failed(String reason) {
        requirePending();
        this.status = DeliveryStatus.FAILED;
        this.failureReason = reason;
    }

    private void requirePending() {
        if (status != DeliveryStatus.PENDING) {
            throw new IllegalStateException("발송 대기 중인 알림만 결과를 기록할 수 있습니다.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
