package com.muffin.notification.infrastructure.apns;

import com.muffin.notification.application.dispatch.PushResult;
import com.muffin.notification.domain.devicetoken.DeviceToken;
import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Serializes delivery with ownership changes to the same device token. */
@Component
public class DeviceTokenDeliveryGuard {
    private final DeviceTokenRepository repository;
    private final TransactionTemplate transactionTemplate;

    public DeviceTokenDeliveryGuard(DeviceTokenRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public PushResult withOwnedDevice(Long userId, DeviceToken candidate, Function<DeviceToken, PushResult> operation) {
        return transactionTemplate.execute(status -> repository
                .findByIdForUpdate(candidate.getId())
                .filter(device -> userId.equals(device.getUserId()))
                .map(operation)
                .orElse(PushResult.NO_DEVICE));
    }
}
