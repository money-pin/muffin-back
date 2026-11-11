package com.muffin.notification.domain.devicetoken;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자의 푸시 기기 토큰. 현재 로그인한 기기의 토큰({@code expiresAt == null})과, 다른 기기 로그인으로 곧 로그아웃될 옛 기기의 토큰을 둔다.
 * 옛 기기는 마지막으로 받은 access token이 만료되어 실제로 로그아웃될 때까지만 알림을 받는다.
 */
@Entity
@Getter
@Table(
        name = "device_token",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_token_token", columnNames = "token"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_token_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token", nullable = false, length = 255)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "environment", nullable = false, length = 20)
    private ApnsEnvironment environment;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    private DeviceToken(Long userId, String token, ApnsEnvironment environment) {
        this.userId = userId;
        this.token = token;
        this.environment = environment;
    }

    public static DeviceToken register(Long userId, String token, ApnsEnvironment environment) {
        if (userId == null || environment == null) {
            throw new IllegalArgumentException("userId와 environment는 필수입니다.");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token은 필수입니다.");
        }
        return new DeviceToken(userId, token, environment);
    }

    public void makeCurrentDeviceOf(Long userId, ApnsEnvironment environment) {
        if (userId == null || environment == null) {
            throw new IllegalArgumentException("userId와 environment는 필수입니다.");
        }
        this.userId = userId;
        this.environment = environment;
        this.expiresAt = null;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
