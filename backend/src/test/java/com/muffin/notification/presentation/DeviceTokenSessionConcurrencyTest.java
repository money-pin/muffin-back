package com.muffin.notification.presentation;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.notification.application.devicetoken.DeviceTokenCommandService;
import com.muffin.notification.domain.devicetoken.ApnsEnvironment;
import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class DeviceTokenSessionConcurrencyTest {
    @Autowired
    RefreshTokenIssuer issuer;

    @Autowired
    RefreshTokenRepository sessions;

    @Autowired
    AccessTokenProvider accessTokens;

    @Autowired
    DeviceTokenCommandService devices;

    @Autowired
    DeviceTokenRepository deviceTokens;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void registrationWaitsForSessionReplacementAndRejectsOldToken() throws Exception {
        long userId = 919191L;
        issuer.startSession(userId);
        String oldToken = accessTokens.issue(
                userId, "USER", sessions.findByUserId(userId).orElseThrow().getSessionId());
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var login = pool.submit(() -> transactions.executeWithoutResult(status -> {
                var session = sessions.findByUserIdForUpdate(userId).orElseThrow();
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                session.startNewSession("replacement", session.getExpiresAt());
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var registration =
                    pool.submit(() -> devices.register(userId, oldToken, "old-device", ApnsEnvironment.PRODUCTION));
            assertThatThrownBy(() -> registration.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            release.countDown();
            login.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> registration.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(AuthException.class);
            assertThat(deviceTokens.findByToken("old-device")).isEmpty();
        } finally {
            release.countDown();
            pool.shutdownNow();
            transactions.executeWithoutResult(status -> {
                deviceTokens.deleteByUserId(userId);
                sessions.deleteByUserId(userId);
            });
        }
    }
}
