package com.muffin.auth.domain.refreshtoken;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 유저 1명당 활성 세션 1개를 나타내는 애그리거트({@code user_id} unique). 새로 로그인하면 기존 세션을 대체하고(다른 기기 세션은
 * 무효화), 탈취 시를 대비하여 원문 토큰은 저장하지 않고 해시값만 저장한다.
 */
@Getter
@Entity
@Table(
        name = "refresh_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_user_id", columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refresh_token_id")
    private Long refreshTokenId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 로그인 시각. 재발급(rotate)으로는 바뀌지 않는다. */
    @Column(name = "session_started_at", nullable = false)
    private LocalDateTime sessionStartedAt;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    private RefreshToken(Long userId, String tokenHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.sessionStartedAt = LocalDateTime.now(KST);
        this.sessionId = UUID.randomUUID().toString();
    }

    public static RefreshToken issue(Long userId, String tokenHash, LocalDateTime expiresAt) {
        return new RefreshToken(userId, tokenHash, expiresAt);
    }

    public boolean isExpired() {
        return isExpired(LocalDateTime.now(KST));
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    /** 재발급(refresh) 시 호출한다. 같은 세션이 이어지므로 로그인 시각은 그대로 둔다. */
    public void rotate(String newTokenHash, LocalDateTime newExpiresAt) {
        this.tokenHash = newTokenHash;
        this.expiresAt = newExpiresAt;
    }

    /** 같은 유저가 새로 로그인해 기존 세션을 대체할 때 호출한다. */
    public void startNewSession(String newTokenHash, LocalDateTime newExpiresAt) {
        rotate(newTokenHash, newExpiresAt);
        this.sessionStartedAt = LocalDateTime.now(KST);
        this.sessionId = UUID.randomUUID().toString();
    }

    public boolean isCurrentSession(String accessSessionId) {
        return sessionId.equals(accessSessionId);
    }
}
