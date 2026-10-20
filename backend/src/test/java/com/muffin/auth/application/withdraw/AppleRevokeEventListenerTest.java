package com.muffin.auth.application.withdraw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.muffin.auth.domain.enums.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppleRevokeEventListenerTest {

    @Mock
    private AppleAuthorizationClient appleAuthorizationClient;

    private AppleRevokeEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AppleRevokeEventListener(appleAuthorizationClient);
    }

    @Test
    @DisplayName("애플 계정이 탈퇴하면 refresh token을 철회한다")
    void handle_apple_revokes() {
        listener.handle(new AccountWithdrawnEvent(AuthProvider.APPLE, "apple-sub", "apple-refresh"));

        verify(appleAuthorizationClient).revoke("apple-refresh");
    }

    @Test
    @DisplayName("애플이 아닌 계정이면 철회하지 않는다")
    void handle_otherProvider_skips() {
        listener.handle(new AccountWithdrawnEvent(AuthProvider.KAKAO, "3812345678", null));

        verify(appleAuthorizationClient, never()).revoke(any());
    }

    @Test
    @DisplayName("철회가 실패해도 예외를 밖으로 던지지 않는다")
    void handle_revokeFails_swallows() {
        doThrow(new RuntimeException("apple down"))
                .when(appleAuthorizationClient)
                .revoke("apple-refresh");

        assertThatNoException()
                .isThrownBy(() ->
                        listener.handle(new AccountWithdrawnEvent(AuthProvider.APPLE, "apple-sub", "apple-refresh")));
    }

    @Test
    @DisplayName("이벤트를 문자열로 찍어도 refresh token 값은 나오지 않는다")
    void event_toString_masksRefreshToken() {
        String printed = new AccountWithdrawnEvent(AuthProvider.APPLE, "apple-sub", "apple-refresh").toString();

        assertThat(printed).doesNotContain("apple-refresh").contains("***");
    }
}
