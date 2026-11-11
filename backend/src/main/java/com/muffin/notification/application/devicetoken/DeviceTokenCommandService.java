package com.muffin.notification.application.devicetoken;

import com.muffin.auth.application.ConstraintViolations;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.auth.domain.exception.AuthException;
import com.muffin.auth.domain.exception.code.AuthErrorCode;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import com.muffin.notification.domain.devicetoken.ApnsEnvironment;
import com.muffin.notification.domain.devicetoken.DeviceToken;
import com.muffin.notification.domain.devicetoken.DeviceTokenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 기기 토큰 등록 유스케이스. 같은 요청을 다시 보내도 결과가 같다(멱등). */
@Service
public class DeviceTokenCommandService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenProvider accessTokenProvider;

    private final TransactionTemplate transactionTemplate;

    public DeviceTokenCommandService(
            DeviceTokenRepository deviceTokenRepository,
            RefreshTokenRepository refreshTokenRepository,
            AccessTokenProvider accessTokenProvider,
            PlatformTransactionManager transactionManager) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenProvider = accessTokenProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void register(Long userId, String accessToken, String token, ApnsEnvironment environment) {
        try {
            registerInTransaction(userId, accessToken, token, environment);
        } catch (DataIntegrityViolationException e) {
            if (!ConstraintViolations.isConstraint(e, "uk_device_token_token")) throw e;
            registerInTransaction(userId, accessToken, token, environment);
        }
    }

    private void registerInTransaction(Long userId, String accessToken, String token, ApnsEnvironment environment) {
        transactionTemplate.executeWithoutResult(status -> registerLocked(userId, accessToken, token, environment));
    }

    private void registerLocked(Long userId, String accessToken, String token, ApnsEnvironment environment) {
        requireCurrentSession(userId, accessToken);
        deviceTokenRepository.deleteOtherTokensOf(userId, token);
        // 이미 있는 토큰이면 이 계정의 현재 기기로 되돌린다. 다른 계정의 토큰이었거나(같은 기기에서 계정 전환),
        // 로그인 때 종료 시각이 적혔지만 실제로는 같은 기기에서 다시 로그인한 경우다.
        deviceTokenRepository
                .findIdByToken(token)
                .flatMap(deviceTokenRepository::findByIdForUpdate)
                .ifPresentOrElse(
                        existing -> existing.makeCurrentDeviceOf(userId, environment),
                        () -> deviceTokenRepository.saveAndFlush(DeviceToken.register(userId, token, environment)));
    }

    // access token은 만료 전까지 DB와 무관하게 통과한다. 다른 기기 로그인·로그아웃 뒤에도 옛 기기가 그 토큰으로
    // 자기 기기를 등록하면 현재 기기의 토큰을 덮어쓰므로, 등록만큼은 지금 세션에서 발급된 토큰인지 확인한다.
    private void requireCurrentSession(Long userId, String accessToken) {
        boolean current = refreshTokenRepository
                .findByUserIdForUpdate(userId)
                .map(session -> session.isCurrentSession(accessTokenProvider.parseSessionId(accessToken)))
                .orElse(false);
        if (!current) {
            throw new AuthException(AuthErrorCode.STALE_SESSION);
        }
    }
}
