package com.muffin.notification.application.dispatch;

import com.muffin.notification.domain.delivery.NotificationDelivery;
import com.muffin.notification.domain.delivery.NotificationDeliveryRepository;
import com.muffin.notification.domain.delivery.NotificationType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사용자별로 중복 방지 키를 잡고 보낸 뒤 결과를 기록한다.
 *
 * <p>외부 발송은 트랜잭션 밖에서 한다. 느린 네트워크 호출을 기다리는 동안 DB 커넥션을 붙잡지 않기 위해서다. 기록은 사용자마다 독립 트랜잭션이라 한 명의 실패가
 * 다른 사용자의 기록을 되돌리지 않는다. {@code REQUIRES_NEW}인 이유는 커밋 이후 이벤트 리스너처럼 이미 끝난 트랜잭션이 스레드에 남아 있는 곳에서 불려도
 * 기록이 그 트랜잭션에 합류해 사라지지 않게 하기 위해서다.
 */
@Slf4j
@Service
public class NotificationDispatcher {

    private static final String SEND_FAILED = "send_failed";

    private final NotificationDeliveryRepository deliveryRepository;
    private final PushSender pushSender;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public NotificationDispatcher(
            NotificationDeliveryRepository deliveryRepository,
            PushSender pushSender,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.deliveryRepository = deliveryRepository;
        this.pushSender = pushSender;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /**
     * 사용자마다 다른 내용을 보낸다. 한 명에게서 난 예외는 기록만 하고 다음 사용자로 넘어간다.
     *
     * @param scope 중복 방지 키의 발송 단위 ({@link NotificationType#dedupKey})
     */
    public NotificationDispatchSummary dispatchAll(
            NotificationType type, String scope, Map<Long, PushMessage> messagesByUserId) {
        NotificationDispatchSummary summary = NotificationDispatchSummary.empty();
        for (Map.Entry<Long, PushMessage> entry : messagesByUserId.entrySet()) {
            summary = summary.add(dispatchSafely(type, entry.getKey(), scope, entry.getValue()));
        }
        return summary;
    }

    /** 같은 내용을 여러 사용자에게 보낸다. */
    public NotificationDispatchSummary dispatchAll(
            NotificationType type, String scope, List<Long> userIds, PushMessage message) {
        NotificationDispatchSummary summary = NotificationDispatchSummary.empty();
        for (Long userId : userIds) {
            summary = summary.add(dispatchSafely(type, userId, scope, message));
        }
        return summary;
    }

    /** 사용자 한 명에게 보낸다. 같은 키로 이미 보냈으면 아무것도 하지 않는다. */
    public DispatchOutcome dispatch(NotificationType type, Long userId, String scope, PushMessage message) {
        String dedupKey = type.dedupKey(userId, scope);
        if (deliveryRepository.existsByDedupKey(dedupKey)) {
            return DispatchOutcome.DUPLICATE;
        }
        Long deliveryId = reserve(type, userId, dedupKey, message);
        if (deliveryId == null) {
            return DispatchOutcome.DUPLICATE;
        }

        boolean delivered = sendSafely(userId, message, dedupKey);
        transactionTemplate.executeWithoutResult(status -> {
            NotificationDelivery delivery = deliveryRepository.getReferenceById(deliveryId);
            if (delivered) {
                delivery.sent(LocalDateTime.now(clock));
            } else {
                delivery.failed(SEND_FAILED);
            }
        });
        return delivered ? DispatchOutcome.SENT : DispatchOutcome.FAILED;
    }

    private DispatchOutcome dispatchSafely(NotificationType type, Long userId, String scope, PushMessage message) {
        try {
            return dispatch(type, userId, scope, message);
        } catch (RuntimeException exception) {
            log.error("Notification dispatch failed: type={} userId={} scope={}", type, userId, scope, exception);
            return DispatchOutcome.FAILED;
        }
    }

    /** 중복 방지 키로 발송 권한을 잡는다. 다른 실행이 먼저 잡았으면 {@code null}. */
    private Long reserve(NotificationType type, Long userId, String dedupKey, PushMessage message) {
        try {
            return transactionTemplate.execute(status -> deliveryRepository
                    .save(NotificationDelivery.reserve(
                            userId, type, dedupKey, message.title(), message.body(), message.deepLink()))
                    .getId());
        } catch (DataIntegrityViolationException exception) {
            return null;
        }
    }

    private boolean sendSafely(Long userId, PushMessage message, String dedupKey) {
        try {
            return pushSender.send(userId, message);
        } catch (RuntimeException exception) {
            log.error("Push send failed: dedupKey={}", dedupKey, exception);
            return false;
        }
    }
}
