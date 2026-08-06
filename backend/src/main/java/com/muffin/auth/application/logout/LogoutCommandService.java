package com.muffin.auth.application.logout;

import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그아웃 유스케이스. 인증된 사용자의 refresh token을 무효화한다. 이미 삭제된 상태에서 다시 호출해도 에러 없이 끝난다(멱등). */
@Service
@RequiredArgsConstructor
public class LogoutCommandService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
