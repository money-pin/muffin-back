package com.muffin.auth.application.logout;

import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그아웃 유스케이스. 인증된 사용자의 refresh token과 푸시 기기 토큰을 지운다. 이미 삭제된 상태에서 다시 호출해도 에러 없이 끝난다(멱등). */
@Service
@RequiredArgsConstructor
public class LogoutCommandService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceTokenRepository deviceTokenRepository;

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
        deviceTokenRepository.deleteByUserId(userId);
    }
}
