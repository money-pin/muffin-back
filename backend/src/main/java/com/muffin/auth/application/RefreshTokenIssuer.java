package com.muffin.auth.application;

import com.muffin.auth.domain.refreshtoken.RefreshToken;
import com.muffin.auth.domain.refreshtoken.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token 발급의 트랜잭션 경계. 원문 토큰은 여기서만 잠깐 존재하고(리턴값으로만 나감), DB에는 해시값만 남는다. 같은
 * 유저의 기존 세션이 있으면 대체(rotate)하고, 없으면 새로 만든다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenIssuer {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties properties;

    /** 재발급용. 기존 세션을 이어 가므로 로그인 시각을 바꾸지 않는다. */
    @Transactional
    public String issue(Long userId) {
        return issue(userId, false);
    }

    /** 로그인용. 다른 기기의 세션을 대체하고 로그인 시각을 새로 찍는다. */
    @Transactional
    public String startSession(Long userId) {
        return issue(userId, true);
    }

    private String issue(Long userId, boolean newSession) {
        String rawToken = generateRawToken();
        String tokenHash = hash(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now(KST).plusDays(properties.refreshTokenExpireDays());

        refreshTokenRepository
                .findByUserId(userId)
                .ifPresentOrElse(
                        existing -> {
                            if (newSession) {
                                existing.startNewSession(tokenHash, expiresAt);
                            } else {
                                existing.rotate(tokenHash, expiresAt);
                            }
                        },
                        () -> refreshTokenRepository.save(RefreshToken.issue(userId, tokenHash, expiresAt)));

        return rawToken;
    }

    /**
     * 지금 세션에 마지막으로 발급한 access token의 만료 시각. 그 기기는 이때 재발급이 막혀 실제로 로그아웃된다. refresh token 행은 발급(로그인·재발급) 때만
     * 바뀌므로 updatedAt이 마지막 발급 시각이다.
     */
    public Optional<LocalDateTime> lastAccessTokenExpiry(Long userId) {
        return refreshTokenRepository
                .findByUserIdForUpdate(userId)
                .map(token -> token.getUpdatedAt() != null ? token.getUpdatedAt() : token.getCreatedAt())
                .map(lastIssuedAt -> lastIssuedAt.plusMinutes(properties.accessTokenExpireMinutes()));
    }

    /** 원문 refresh token으로 아직 만료되지 않은 레코드를 찾는다. 재발급/로그아웃에서 사용한다. */
    public Optional<RefreshToken> findValid(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken)).filter(token -> !token.isExpired());
    }

    public Optional<RefreshToken> findValidForUpdate(String rawToken) {
        return refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken)).filter(token -> !token.isExpired());
    }

    public String sessionId(Long userId) {
        return refreshTokenRepository.findByUserId(userId).orElseThrow().getSessionId();
    }

    private static String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
