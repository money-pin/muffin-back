package com.muffin.user.domain;

import com.muffin.user.domain.enums.UserRole;
import com.muffin.user.domain.enums.UserStatus;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "user_uuid", nullable = false, length = 36, updatable = false)
    private String userUuid;

    @Column(name = "name", length = 10)
    private String name;

    @Column(name = "nickname", nullable = false, length = 20)
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private UserOnboarding userOnboarding;

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
        if (nickname == null) {
            throw new NullPointerException("nickname은 필수입니다.");
        }
        if (nickname.length() > 20) {
            throw new IllegalArgumentException("nickname은 20자를 초과할 수 없습니다.");
        }
        this.nickname = nickname;
        this.name = name;
        this.birthday = birthday;
        this.phoneNumber = phoneNumber;
        this.onboardingCompleted = false;
        this.termAgreement = false;
        this.role = UserRole.USER;
        this.status = UserStatus.ACTIVE;
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
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
        this.termAgreedAt = LocalDateTime.now();
        touch();
    }

    // 온보딩 완료
    public void completeOnboarding(int firstQuestion, int secondQuestion, int thirdQuestion) {
        if (this.onboardingCompleted) {
            throw new IllegalStateException("이미 온보딩을 완료한 사용자입니다.");
        }
        this.userOnboarding = UserOnboarding.of(this, firstQuestion, secondQuestion, thirdQuestion);
        this.onboardingCompleted = true;
        touch();
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
        this.deletedAt = LocalDateTime.now();
        touch();
    }

    public void suspend() {
        if (this.status == UserStatus.WITHDRAWN) {
            throw new IllegalStateException("탈퇴한 사용자는 정지할 수 없습니다.");
        }
        if (this.status == UserStatus.SUSPENDED) {
            throw new IllegalStateException("이미 정지한 사용자입니다.");
        }
        this.status = UserStatus.SUSPENDED;
        touch();
    }

    public void reactivate() {
        if (this.status != UserStatus.SUSPENDED) {
            throw new IllegalStateException("정지 상태인 사용자만 복구할 수 있습니다.");
        }
        this.status = UserStatus.ACTIVE;
        touch();
    }

    public void changeNickname(String newNickname) {
        if (newNickname == null) {
            throw new NullPointerException("nickname은 필수입니다.");
        }
        if (newNickname.length() > 20) {
            throw new IllegalArgumentException("nickname은 20자를 초과할 수 없습니다.");
        }
        this.nickname = newNickname;
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
