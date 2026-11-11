package com.muffin.notification.application.devicetoken;

import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeviceTokenCleanupService {
    private final DeviceTokenRepository repository;
    private final Clock clock;

    @Transactional
    public int deleteExpired() {
        return repository.deleteExpired(LocalDateTime.now(clock));
    }
}
