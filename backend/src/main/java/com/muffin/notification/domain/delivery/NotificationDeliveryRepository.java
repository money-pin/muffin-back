package com.muffin.notification.domain.delivery;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    boolean existsByDedupKey(String dedupKey);
}
