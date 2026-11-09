package com.muffin.notification.application.devicetoken;

import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.notification.domain.devicetoken.ApnsEnvironment;
import com.muffin.notification.domain.devicetoken.DeviceToken;
import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기기 토큰 등록 유스케이스. 같은 요청을 다시 보내도 결과가 같다(멱등). */
@Service
@RequiredArgsConstructor
public class DeviceTokenCommandService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenProvider accessTokenProvider;

    // ponytail: 같은 토큰을 동시에 처음 등록하면 유니크 충돌로 한쪽이 500. 앱이 실행·로그인 때만 보내 드물어서 재시도를 두지 않았다.
    @Transactional
    public void register(Long userId, String accessToken, String token, ApnsEnvironment environment) {
        requireCurrentSession(userId, accessToken);
        deviceTokenRepository.deleteOtherTokensOf(userId, token);
        deviceTokenRepository
                .findByToken(token)
                .ifPresentOrElse(
                        existing -> existing.assignTo(userId, environment),
                        () -> deviceTokenRepository.save(DeviceToken.register(userId, token, environment)));
    }

    // access token은 만료 전까지 DB와 무관하게 통과한다. 다른 기기 로그인·로그아웃 뒤에도 옛 기기가 그 토큰으로
    // 자기 기기를 등록하면 현재 기기의 토큰을 덮어쓰므로, 등록만큼은 지금 세션에서 발급된 토큰인지 확인한다.
    private void requireCurrentSession(Long userId, String accessToken) {
        boolean current = refreshTokenRepository
                .findByUserId(userId)
                .map(session -> session.isIssuedInCurrentSession(accessTokenProvider.parseIssuedAt(accessToken)))
                .orElse(false);
        if (!current) {
            throw new AuthException(AuthErrorCode.STALE_SESSION);
        }
    }
}
