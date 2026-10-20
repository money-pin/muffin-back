package com.muffin.auth.application.withdraw;

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
class KakaoUnlinkEventListenerTest {

    @Mock
    private KakaoAccountUnlinker kakaoAccountUnlinker;

    private KakaoUnlinkEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new KakaoUnlinkEventListener(kakaoAccountUnlinker);
    }

    @Test
    @DisplayName("카카오 계정이 탈퇴하면 원본 회원번호로 연결을 끊는다")
    void handle_kakao_unlinks() {
        listener.handle(new AccountWithdrawnEvent(AuthProvider.KAKAO, "3812345678"));

        verify(kakaoAccountUnlinker).unlink("3812345678");
    }

    @Test
    @DisplayName("카카오가 아닌 계정이면 호출하지 않는다")
    void handle_otherProvider_skips() {
        listener.handle(new AccountWithdrawnEvent(AuthProvider.GOOGLE, "google-sub"));

        verify(kakaoAccountUnlinker, never()).unlink(any());
    }

    @Test
    @DisplayName("연결 끊기가 실패해도 예외를 밖으로 던지지 않는다")
    void handle_unlinkFails_swallows() {
        doThrow(new IllegalStateException("kakao down"))
                .when(kakaoAccountUnlinker)
                .unlink("3812345678");

        assertThatNoException()
                .isThrownBy(() -> listener.handle(new AccountWithdrawnEvent(AuthProvider.KAKAO, "3812345678")));
    }
}
