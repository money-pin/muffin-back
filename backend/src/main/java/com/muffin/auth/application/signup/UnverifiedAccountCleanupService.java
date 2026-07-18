package com.muffin.auth.application.signup;

import com.muffin.auth.domain.Auth;
import com.muffin.auth.domain.AuthRepository;
import com.muffin.auth.domain.RefreshTokenRepository;
import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.user.domain.UserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 인증을 끝내지 않은 채 TTL이 지난 로컬 계정을 정리하는 배치. User/Auth/RefreshToken은 FK로 묶여있지 않은
 * 느슨한 결합이라 userId 목록을 기준으로 세 테이블을 각각 정리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnverifiedAccountCleanupService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UnverifiedAccountCleanupProperties properties;

    @Transactional
    public void cleanupUnverified() {
        LocalDateTime cutoff = LocalDateTime.now(KST).minusMinutes(properties.ttlMinutes());
        List<Auth> candidates =
                authRepository.findAllByProviderAndEmailVerifiedFalseAndCreatedAtBefore(AuthProvider.LOCAL, cutoff);
        if (candidates.isEmpty()) {
            log.info("[unverified-account-cleanup] deleted=0");
            return;
        }

        List<Long> userIds = candidates.stream().map(Auth::getUserId).toList();

        refreshTokenRepository.deleteAllByUserIdIn(userIds);
        authRepository.deleteAll(candidates);
        userRepository.deleteAllById(userIds);

        log.info("[unverified-account-cleanup] deleted={}", candidates.size());
    }
}
