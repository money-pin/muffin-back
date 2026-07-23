package com.muffin.user.domain;

import com.muffin.global.entity.BaseEntity;
import com.muffin.user.domain.enums.UserRole;
import com.muffin.user.domain.enums.UserStatus;
import jakarta.persistence.*;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member", uniqueConstraints = @UniqueConstraint(name = "uk_member_nickname", columnNames = "nickname"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User extends BaseEntity {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "character_id")
    private Long characterId;

    @Column(name = "user_uuid", nullable = false, length = 36, updatable = false, unique = true)
    private String userUuid;

    @Column(name = "name", length = 10)
    private String name;

    @Column(name = "nickname", length = 10)
    private String nickname;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "term_agreement", nullable = false)
    private boolean termAgreement;

    @Column(name = "term_agreed_at")
    private LocalDateTime termAgreedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserOnboarding userOnboarding;

    private static final int NAME_MAX_LENGTH = 10;
    private static final int NICKNAME_MIN_LENGTH = 2;
    private static final int NICKNAME_MAX_LENGTH = 10;
    // 한글/영문/숫자/공백만 허용, 2~10자. 정규화(NFC) 후의 문자열에 대해서만 검증한다.
    private static final Pattern NICKNAME_PATTERN =
            Pattern.compile("^[가-힣a-zA-Z0-9 ]{" + NICKNAME_MIN_LENGTH + "," + NICKNAME_MAX_LENGTH + "}$");

    private User(Long characterId, String userUuid, String name, String nickname) {
        this.characterId = characterId;
        if (userUuid == null) {
            throw new NullPointerException("userUuid는 필수입니다.");
        }
        this.userUuid = userUuid;
        String normalizedNickname = normalizeNickname(nickname);
        validateNicknameFormat(normalizedNickname);
        this.nickname = normalizedNickname;
        validateOptionalLength(name, NAME_MAX_LENGTH, "name");
        this.name = name;
        this.onboardingCompleted = false;
        this.termAgreement = false;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * 클라이언트/OS에 따라 한글이 분해형(NFD, 자모 분리)으로 넘어올 수 있어(특히 macOS), 완성형(NFC)으로 정규화한 뒤
     * 검증·저장한다. 정규화하지 않으면 String.length()가 실제 글자 수보다 많이 잡히고, 정규식도 완성형 음절 범위만 매칭하므로
     * 정상적인 한글 닉네임이 거부될 수 있다.
     */
    private static String normalizeNickname(String nickname) {
        return nickname == null ? null : Normalizer.normalize(nickname, Normalizer.Form.NFC);
    }

    private static void validateNickname(String nickname) {
        if (nickname == null) {
            throw new NullPointerException("nickname은 필수입니다.");
        }
        validateNicknameFormat(nickname);
    }

    private static void validateNicknameFormat(String nickname) {
        if (nickname != null && !NICKNAME_PATTERN.matcher(nickname).matches()) {
            throw new IllegalArgumentException(
                    "닉네임은 한글/영문/숫자/공백만 사용해 " + NICKNAME_MIN_LENGTH + "~" + NICKNAME_MAX_LENGTH + "자로 입력해야 합니다.");
        }
    }

    private static void validateOptionalLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "은 " + maxLength + "자를 초과할 수 없습니다.");
        }
    }

    // 회원가입 공통 진입점
    public static User register(Long characterId, String userUuid, String name, String nickname) {
        return new User(characterId, userUuid, name, nickname);
    }

    public void agreeToTerms() {
        if (this.termAgreement) {
            throw new IllegalStateException("이미 약관에 동의한 사용자입니다.");
        }
        this.termAgreement = true;
        this.termAgreedAt = LocalDateTime.now(KST);
    }

    // 온보딩 중 캐릭터 결과 확정. 온보딩 완료 후에는 재할당할 수 없다.
    public void assignCharacter(Long characterId) {
        if (this.onboardingCompleted) {
            throw new IllegalStateException("이미 온보딩을 완료한 사용자입니다.");
        }
        if (characterId == null) {
            throw new NullPointerException("characterId는 필수입니다.");
        }
        this.characterId = characterId;
    }

    // 온보딩 완료
    public void completeOnboarding(int firstQuestion, int secondQuestion, int thirdQuestion) {
        if (this.onboardingCompleted) {
            throw new IllegalStateException("이미 온보딩을 완료한 사용자입니다.");
        }
        this.userOnboarding = UserOnboarding.of(this, firstQuestion, secondQuestion, thirdQuestion);
        this.onboardingCompleted = true;
    }

    // 탈퇴: 이름 즉시 삭제. 나머지 이력은 별도 배치가 비식별화 후 6개월 뒤 삭제.
    public void withdraw() {
        if (this.status == UserStatus.WITHDRAWN) {
            throw new IllegalStateException("이미 탈퇴한 사용자입니다.");
        }
        if (this.status == UserStatus.SUSPENDED) {
            throw new IllegalStateException("정지된 사용자는 탈퇴할 수 없습니다.");
        }
        this.name = null;
        this.status = UserStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now(KST);
    }

    public void suspend() {
        if (this.status == UserStatus.WITHDRAWN) {
            throw new IllegalStateException("탈퇴한 사용자는 정지할 수 없습니다.");
        }
        if (this.status == UserStatus.SUSPENDED) {
            throw new IllegalStateException("이미 정지한 사용자입니다.");
        }
        this.status = UserStatus.SUSPENDED;
    }

    public void reactivate() {
        if (this.status != UserStatus.SUSPENDED) {
            throw new IllegalStateException("정지 상태인 사용자만 복구할 수 있습니다.");
        }
        this.status = UserStatus.ACTIVE;
    }

    public void changeNickname(String newNickname) {
        this.nickname = normalizeAndValidateNickname(newNickname);
    }

    /**
     * 닉네임 조회/변경 API가 User 인스턴스 없이도 같은 정규화·형식 검증 규칙을 재사용할 수 있도록 공개한 정적 메서드.
     * null이면 NullPointerException, 형식 위반이면 IllegalArgumentException을 던진다.
     */
    public static String normalizeAndValidateNickname(String nickname) {
        String normalized = normalizeNickname(nickname);
        validateNickname(normalized);
        return normalized;
    }
}
