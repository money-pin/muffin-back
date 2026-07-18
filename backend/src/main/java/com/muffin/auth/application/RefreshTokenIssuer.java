package com.muffin.auth.application;

import com.muffin.auth.domain.RefreshToken;
import com.muffin.auth.domain.RefreshTokenRepository;
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

    @Transactional
    public String issue(Long userId) {
        String rawToken = generateRawToken();
        String tokenHash = hash(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now(KST).plusDays(properties.refreshTokenExpireDays());

        refreshTokenRepository
                .findByUserId(userId)
                .ifPresentOrElse(
                        existing -> existing.rotate(tokenHash, expiresAt),
                        () -> refreshTokenRepository.save(RefreshToken.issue(userId, tokenHash, expiresAt)));

        return rawToken;
    }

    /** 원문 refresh token으로 아직 만료되지 않은 레코드를 찾는다. 재발급/로그아웃에서 사용한다. */
    public Optional<RefreshToken> findValid(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken)).filter(token -> !token.isExpired());
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
