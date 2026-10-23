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

    // 소셜 로그인 가입
    public static Auth createSocial(Long userId, AuthProvider provider, String email, String providerUserId) {
        if (provider == null) {
            throw new NullPointerException("provider는 필수입니다.");
        }
        Auth credential = new Auth(userId, provider, email);
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

    /**
     * 탈퇴 처리: 소셜 계정과의 연결(sub)과 이메일을 지운다. sub를 남겨두면 같은 소셜 계정으로 다시 로그인했을 때 이 탈퇴한
     * Auth가 조회돼 영구히 재가입할 수 없다. provider_user_id는 비울 수 없고(NOT NULL) 같은 provider 안에서 겹칠 수도
     * 없어서(uk_provider_user), 탈퇴 계정마다 다른 값이 되도록 auth_id를 붙인다.
     */
    public void anonymize() {
        this.providerUserId = "withdrawn-" + authId;
        this.email = null;
    }
}
