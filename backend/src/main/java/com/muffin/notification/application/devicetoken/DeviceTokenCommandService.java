package com.muffin.notification.application.devicetoken;

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

    // ponytail: 같은 토큰을 동시에 처음 등록하면 유니크 충돌로 한쪽이 500. 앱이 실행·로그인 때만 보내 드물어서 재시도를 두지 않았다.
    @Transactional
    public void register(Long userId, String token, ApnsEnvironment environment) {
        deviceTokenRepository.deleteOtherTokensOf(userId, token);
        deviceTokenRepository
                .findByToken(token)
                .ifPresentOrElse(
                        existing -> existing.assignTo(userId, environment),
                        () -> deviceTokenRepository.save(DeviceToken.register(userId, token, environment)));
    }
}
