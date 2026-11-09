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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자의 푸시 기기 토큰. 세션(RefreshToken)이 사용자당 1개라 토큰도 마지막 로그인 기기 1개만 둔다. 로그아웃된 기기로 알림이 가지 않게
 * 하기 위해서다.
 */
@Entity
@Getter
@Table(
        name = "device_token",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_device_token_user_id", columnNames = "user_id"),
            @UniqueConstraint(name = "uk_device_token_token", columnNames = "token")
        })
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
}
