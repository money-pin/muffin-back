package com.muffin.notification.application.devicetoken;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.refreshtoken.RefreshToken;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.notification.domain.devicetoken.*;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class DeviceTokenRegistrationRetryTest {
    @Test
    void retriesOnlyTokenUniqueConflictInNewTransaction() {
        var devices = mock(DeviceTokenRepository.class);
        var sessions = mock(RefreshTokenRepository.class);
        var access = mock(AccessTokenProvider.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        var service = new DeviceTokenCommandService(devices, sessions, access, manager);
        var session = RefreshToken.issue(1L, "hash", LocalDateTime.now().plusDays(1));
        when(sessions.findByUserIdForUpdate(1L)).thenReturn(Optional.of(session));
        when(access.parseSessionId("access")).thenReturn(session.getSessionId());
        var existing = DeviceToken.register(2L, "shared", ApnsEnvironment.PRODUCTION);
        when(devices.findIdByToken("shared")).thenReturn(Optional.empty(), Optional.of(7L));
        when(devices.findByIdForUpdate(7L)).thenReturn(Optional.of(existing));
        when(devices.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_device_token_token"));
        service.register(1L, "access", "shared", ApnsEnvironment.SANDBOX);
        assertThat(existing.getUserId()).isEqualTo(1L);
        assertThat(existing.getEnvironment()).isEqualTo(ApnsEnvironment.SANDBOX);
        verify(manager).rollback(any());
        verify(manager).commit(any());
    }

    @Test
    void unrelatedIntegrityFailureIsNotRetried() {
        var devices = mock(DeviceTokenRepository.class);
        var sessions = mock(RefreshTokenRepository.class);
        var access = mock(AccessTokenProvider.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        var service = new DeviceTokenCommandService(devices, sessions, access, manager);
        var session = RefreshToken.issue(1L, "hash", LocalDateTime.now().plusDays(1));
        when(sessions.findByUserIdForUpdate(1L)).thenReturn(Optional.of(session));
        when(access.parseSessionId("access")).thenReturn(session.getSessionId());
        when(devices.findIdByToken("shared")).thenReturn(Optional.empty());
        when(devices.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("other constraint"));
        assertThatThrownBy(() -> service.register(1L, "access", "shared", ApnsEnvironment.PRODUCTION))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("other constraint");
        verify(manager, times(1)).getTransaction(any());
    }
}
