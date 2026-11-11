package com.muffin.notification.presentation;

import static org.assertj.core.api.Assertions.*;

import com.muffin.auth.application.RefreshTokenIssuer;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.notification.application.devicetoken.DeviceTokenCleanupService;
import com.muffin.notification.application.devicetoken.DeviceTokenCommandService;
import com.muffin.notification.application.dispatch.PushResult;
import com.muffin.notification.domain.devicetoken.*;
import com.muffin.notification.infrastructure.apns.DeviceTokenDeliveryGuard;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
class DeviceTokenOwnershipConcurrencyTest {
    @Autowired
    DeviceTokenRepository devices;

    @Autowired
    DeviceTokenDeliveryGuard guard;

    @Autowired
    DeviceTokenCommandService registration;

    @Autowired
    DeviceTokenCleanupService cleanup;

    @Autowired
    RefreshTokenIssuer issuer;

    @Autowired
    RefreshTokenRepository sessions;

    @Autowired
    AccessTokenProvider accessTokens;

    @AfterEach
    void clean() {
        devices.deleteAll();
        sessions.deleteAll();
    }

    @Test
    void transferWaitsForInFlightDeliveryAndStaleCandidateIsRejected() throws Exception {
        long oldOwner = 881L, newOwner = 882L;
        DeviceToken candidate =
                devices.saveAndFlush(DeviceToken.register(oldOwner, "shared", ApnsEnvironment.PRODUCTION));
        issuer.startSession(newOwner);
        String access = accessTokens.issue(
                newOwner, "USER", sessions.findByUserId(newOwner).orElseThrow().getSessionId());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var send = pool.submit(() -> guard.withOwnedDevice(oldOwner, candidate, owned -> {
                entered.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return PushResult.SENT;
            }));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var transfer =
                    pool.submit(() -> registration.register(newOwner, access, "shared", ApnsEnvironment.PRODUCTION));
            assertThatThrownBy(() -> transfer.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            assertThat(send.get(10, TimeUnit.SECONDS)).isEqualTo(PushResult.SENT);
            transfer.get(10, TimeUnit.SECONDS);
            assertThat(devices.findByToken("shared").orElseThrow().getUserId()).isEqualTo(newOwner);
            assertThat(guard.withOwnedDevice(oldOwner, candidate, owned -> {
                        throw new AssertionError("sent to new owner");
                    }))
                    .isEqualTo(PushResult.NO_DEVICE);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void cleanupDeletesOnlyExpiredTokens() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        DeviceToken expired = DeviceToken.register(1L, "expired", ApnsEnvironment.PRODUCTION);
        DeviceToken future = DeviceToken.register(1L, "future", ApnsEnvironment.PRODUCTION);
        DeviceToken current = DeviceToken.register(1L, "current", ApnsEnvironment.PRODUCTION);
        ReflectionTestUtils.setField(expired, "expiresAt", now.minusSeconds(5));
        ReflectionTestUtils.setField(future, "expiresAt", now.plusHours(1));
        devices.saveAllAndFlush(java.util.List.of(expired, future, current));
        assertThat(cleanup.deleteExpired()).isEqualTo(1);
        assertThat(devices.findByToken("expired")).isEmpty();
        assertThat(devices.findByToken("future")).isPresent();
        assertThat(devices.findByToken("current")).isPresent();
    }
}
