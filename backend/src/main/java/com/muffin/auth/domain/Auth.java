package com.muffin.auth.domain;

import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "auth",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_provider_user",
                    columnNames = {"provider", "provider_user_id"}),
            @UniqueConstraint(
                    name = "uk_provider_email",
                    columnNames = {"provider", "email"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Auth extends BaseEntity {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // 비밀번호 형식: 영문, 숫자를 포함한 8~16자리 조합
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,16}$");

    // 이메일 형식: local@domain.tld (서브도메인 다중 허용, 예: dgu.ac.kr)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)*\\.[a-zA-Z]{2,}$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_id")
    private Long authId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_user_id", length = 255)
    private String providerUserId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "password", length = 255)
    private String passwordHash;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    private Auth(Long userId, AuthProvider provider, String email) {
        if (userId == null) {
            throw new NullPointerException("userId는 필수입니다.");
        }
        this.userId = userId;
        this.provider = provider;
        validateEmailFormat(email);
        this.email = email;
        this.emailVerified = false;
    }

    // 로컬 회원가입
    public static Auth createLocal(Long userId, String email, String rawPassword, String encodedPassword) {
        validatePasswordFormat(rawPassword);
        Auth credential = new Auth(userId, AuthProvider.LOCAL, email);
        if (encodedPassword == null) {
            throw new NullPointerException("encodedPassword는 필수입니다.");
        }
        credential.passwordHash = encodedPassword;
        return credential;
    }

    // 구글 OAuth2 가입
    public static Auth createGoogle(Long userId, String email, String providerUserId) {
        Auth credential = new Auth(userId, AuthProvider.GOOGLE, email);
        if (providerUserId == null) {
            throw new NullPointerException("providerUserId는 필수입니다.");
        }
        credential.providerUserId = providerUserId;
        credential.emailVerified = true;
        return credential;
    }

    private static void validatePasswordFormat(String rawPassword) {
        if (rawPassword == null || !PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            throw new IllegalArgumentException("비밀번호는 영문, 숫자를 포함한 8~16자여야 합니다.");
        }
    }

    private static void validateEmailFormat(String email) {
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public boolean isLoginLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now(KST));
    }

    /** 비밀번호 불일치 시 호출한다. 누적 실패 횟수가 maxAttempts에 도달하면 lockMinutes 동안 로그인을 잠근다. */
    public void recordFailedLogin(int maxAttempts, long lockMinutes) {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= maxAttempts) {
            this.lockedUntil = LocalDateTime.now(KST).plusMinutes(lockMinutes);
        }
    }

    /** 로그인 성공 시 호출해 실패 이력을 초기화한다. */
    public void resetLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void changePassword(String rawPassword, String encodedPassword) {
        if (this.provider != AuthProvider.LOCAL) {
            throw new IllegalStateException("소셜 로그인 계정은 비밀번호를 변경할 수 없습니다.");
        }
        validatePasswordFormat(rawPassword);
        if (encodedPassword == null) {
            throw new NullPointerException("encodedPassword는 필수입니다.");
        }
        this.passwordHash = encodedPassword;
    }
}
