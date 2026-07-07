package com.muffin.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_onboarding")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class UserOnboarding {

    private static final int MIN_OPTION = 1;
    private static final int MAX_OPTION = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "onboarding_id")
    private Long onboardingId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "first_question", nullable = false)
    private int firstQuestion;

    @Column(name = "second_question", nullable = false)
    private int secondQuestion;

    @Column(name = "third_question", nullable = false)
    private int thirdQuestion;

    private UserOnboarding(User user, int firstQuestion, int secondQuestion, int thirdQuestion) {
        this.user = user;
        this.firstQuestion = validateOption(firstQuestion);
        this.secondQuestion = validateOption(secondQuestion);
        this.thirdQuestion = validateOption(thirdQuestion);
    }

    // 사용자 최초 가입 시 온보딩 결과 생성, User 애그리거트 내부에서만 호출 가능
    static UserOnboarding of(User user, int firstQuestion, int secondQuestion, int thirdQuestion) {
        return new UserOnboarding(user, firstQuestion, secondQuestion, thirdQuestion);
    }

    // 온보딩 문항 번호 제약 검사 메소드
    private static int validateOption(int value) {
        if (value < MIN_OPTION || value > MAX_OPTION) {
            throw new IllegalArgumentException("응답 옵션은 1~3 중 하나여야 합니다.");
        }
        return value;
    }
}
