package com.muffin.user.domain;

import com.muffin.global.entity.BaseEntity;
import com.muffin.user.domain.enums.UserRole;
import com.muffin.user.domain.enums.UserStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User extends BaseEntity {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "user_uuid", nullable = false, length = 36, updatable = false, unique = true)
    private String userUuid;

    @Column(name = "name", length = 10)
    private String name;

    @Column(name = "nickname", unique = true, length = 20)
    private String nickname;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "phone_number", length = 15)
    private String phoneNumber;

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
    private static final int NICKNAME_MAX_LENGTH = 20;
    private static final int PHONE_NUMBER_MAX_LENGTH = 15;

    private User(
            Long characterId, String userUuid, String name, String nickname, LocalDate birthday, String phoneNumber) {
        if (characterId == null) {
            throw new NullPointerException("characterId는 필수입니다.");
        }
        this.characterId = characterId;
        if (userUuid == null) {
            throw new NullPointerException("userUuid는 필수입니다.");
        }
        this.userUuid = userUuid;
        validateOptionalLength(nickname, NICKNAME_MAX_LENGTH, "nickname");
        this.nickname = nickname;
        validateOptionalLength(name, NAME_MAX_LENGTH, "name");
        this.name = name;
        this.birthday = birthday;
        validateOptionalLength(phoneNumber, PHONE_NUMBER_MAX_LENGTH, "phoneNumber");
        this.phoneNumber = phoneNumber;
        this.onboardingCompleted = false;
        this.termAgreement = false;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
    }

    private static void validateNickname(String nickname) {
        if (nickname == null) {
            throw new NullPointerException("nickname은 필수입니다.");
        }
        validateOptionalLength(nickname, NICKNAME_MAX_LENGTH, "nickname");
    }

    private static void validateOptionalLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "은 " + maxLength + "자를 초과할 수 없습니다.");
        }
    }

    // 회원가입 공통 진입점
    public static User register(
            Long characterId, String userUuid, String name, String nickname, LocalDate birthday, String phoneNumber) {
        return new User(characterId, userUuid, name, nickname, birthday, phoneNumber);
    }

    public void agreeToTerms() {
        if (this.termAgreement) {
            throw new IllegalStateException("이미 약관에 동의한 사용자입니다.");
        }
        this.termAgreement = true;
        this.termAgreedAt = LocalDateTime.now(KST);
    }

    // 온보딩 완료
    public void completeOnboarding(int firstQuestion, int secondQuestion, int thirdQuestion) {
        if (this.onboardingCompleted) {
            throw new IllegalStateException("이미 온보딩을 완료한 사용자입니다.");
        }
        this.userOnboarding = UserOnboarding.of(this, firstQuestion, secondQuestion, thirdQuestion);
        this.onboardingCompleted = true;
    }

    // 탈퇴: 이름/전화번호/생년월일 즉시 삭제. 나머지 이력은 별도 배치가 비식별화 후 6개월 뒤 삭제.
    public void withdraw() {
        if (this.status == UserStatus.WITHDRAWN) {
            throw new IllegalStateException("이미 탈퇴한 사용자입니다.");
        }
        if (this.status == UserStatus.SUSPENDED) {
            throw new IllegalStateException("정지된 사용자는 탈퇴할 수 없습니다.");
        }
        this.name = null;
        this.phoneNumber = null;
        this.birthday = null;
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
        validateNickname(newNickname);
        this.nickname = newNickname;
    }
}
