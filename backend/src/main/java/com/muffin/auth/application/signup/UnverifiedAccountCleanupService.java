package com.muffin.auth.application.signup;

import com.muffin.auth.domain.auth.Auth;
import com.muffin.auth.domain.auth.AuthRepository;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 인증을 끝내지 않은 채 TTL이 지난 로컬 계정을 정리하는 배치. User/Auth/RefreshToken은 FK로 묶여있지 않은
 * 느슨한 결합이라 userId 목록을 기준으로 세 테이블을 각각 정리한다.
 */
@Service
@RequiredArgsConstructor
public class UnverifiedAccountCleanupService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UnverifiedAccountCleanupProperties properties;

    /**
     * @return 삭제한 계정 수. 호출자가 배치 실행 로그에 남긴다.
     */
    @Transactional
    public int cleanupUnverified() {
        LocalDateTime cutoff = LocalDateTime.now(KST).minusMinutes(properties.ttlMinutes());
        List<Auth> candidates =
                authRepository.findAllByProviderAndEmailVerifiedFalseAndCreatedAtBefore(AuthProvider.LOCAL, cutoff);
        if (candidates.isEmpty()) {
            return 0;
        }

        List<Long> userIds = candidates.stream().map(Auth::getUserId).toList();

        refreshTokenRepository.deleteAllByUserIdIn(userIds);
        authRepository.deleteAll(candidates);
        userRepository.deleteAllById(userIds);

        return candidates.size();
    }
}
