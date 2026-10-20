package com.muffin.auth.domain.auth;

import com.muffin.auth.domain.enums.AuthProvider;
import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.*;
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
            // 계정 연동이 없어 유저 한 명은 로그인 수단(Auth)을 하나만 가진다.
            @UniqueConstraint(
                    name = "uk_auth_user_id",
                    columnNames = {"user_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Auth extends BaseEntity {

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

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    // 계정 식별에는 쓰지 않는 참고 정보다. 카카오처럼 사용자가 이메일 제공을 거부할 수 있는 provider가 있어 null을 허용한다.
    @Column(name = "email", length = 255)
    private String email;

    private Auth(Long userId, AuthProvider provider, String email) {
        if (userId == null) {
            throw new NullPointerException("userId는 필수입니다.");
        }
        this.userId = userId;
        this.provider = provider;
        validateEmailFormat(email);
        this.email = email;
    }

    // 구글 OAuth2 가입
    public static Auth createGoogle(Long userId, String email, String providerUserId) {
        Auth credential = new Auth(userId, AuthProvider.GOOGLE, email);
        if (providerUserId == null) {
            throw new NullPointerException("providerUserId는 필수입니다.");
        }
        credential.providerUserId = providerUserId;
        return credential;
    }

    private static void validateEmailFormat(String email) {
        if (email != null && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("이메일 형식이 올바르지 않습니다.");
        }
    }

    /** 탈퇴 처리: 원본 이메일을 복구 불가능한 값으로 대체한다. */
    public void anonymizeEmail() {
        this.email = "withdrawn-" + authId + "@deleted.local";
    }
}
