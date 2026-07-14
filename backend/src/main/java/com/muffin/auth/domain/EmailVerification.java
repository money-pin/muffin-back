package com.muffin.auth.domain;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "email_verification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class EmailVerification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_verification_id")
    private Long emailVerificationId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    private EmailVerification(String email, String codeHash, LocalDateTime expiresAt) {
        this.email = Objects.requireNonNull(email, "email은 필수입니다.");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash는 필수입니다.");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt은 필수입니다.");
        this.verified = false;
        this.attemptCount = 0;
    }

    public static EmailVerification create(String email, String codeHash, long expireMinutes) {
        return new EmailVerification(email, codeHash, LocalDateTime.now().plusMinutes(expireMinutes));
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isLocked(int maxAttempts) {
        return attemptCount >= maxAttempts;
    }

    public boolean isWithinCooldown(long cooldownSeconds) {
        return getCreatedAt().plusSeconds(cooldownSeconds).isAfter(LocalDateTime.now());
    }

    public void increaseAttemptCount() {
        this.attemptCount++;
    }

    public void markVerified() {
        this.verified = true;
    }
}
