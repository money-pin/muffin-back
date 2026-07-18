package com.muffin.auth.infrastructure.jwt;

import com.muffin.auth.application.JwtProperties;
import com.muffin.auth.domain.AccessTokenProvider;
import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** {@link AccessTokenProvider}의 JWT(HS256) 구현체. */
@Component
public class JwtAccessTokenProvider implements AccessTokenProvider {

    private static final String ROLE_CLAIM = "role";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtAccessTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String issue(Long userId, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenExpireMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    @Override
    public Long parseUserId(String accessToken) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(accessToken)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new GeneralException(GeneralErrorCode.UNAUTHORIZED);
        }
    }
}
