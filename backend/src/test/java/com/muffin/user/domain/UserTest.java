package com.muffin.user.domain;

import static org.assertj.core.api.Assertions.*;

import com.muffin.user.domain.enums.UserRole;
import com.muffin.user.domain.enums.UserStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UserTest {

    private User defaultUser() {
        return User.register(1L, "uuid-1234", "홍길동", "길동이", LocalDate.of(1995, 1, 1), "010-1234-5678");
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("정상 생성 시 기본값 확인")
        void success() {
            User user = defaultUser();

            assertThat(user.getRole()).isEqualTo(UserRole.USER);
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.isOnboardingCompleted()).isFalse();
            assertThat(user.isTermAgreement()).isFalse();
            assertThat(user.getCreatedAt()).isNotNull();
            assertThat(user.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("null characterId → NullPointerException")
        void nullCharacterId() {
            assertThatThrownBy(() ->
                            User.register(null, "uuid-1234", "홍길동", "길동이", LocalDate.of(1995, 1, 1), "010-1234-5678"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null userUuid → NullPointerException")
        void nullUserUuid() {
            assertThatThrownBy(() -> User.register(1L, null, "홍길동", "길동이", LocalDate.of(1995, 1, 1), "010-1234-5678"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null nickname → NullPointerException")
        void nullNickname() {
            assertThatThrownBy(() ->
                            User.register(1L, "uuid-1234", "홍길동", null, LocalDate.of(1995, 1, 1), "010-1234-5678"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("name, birthday, phoneNumber는 null 허용")
        void optionalFieldsNullable() {
            assertThatNoException().isThrownBy(() -> User.register(1L, "uuid-1234", null, "길동이", null, null));
        }
    }

    @Nested
    @DisplayName("agreeToTerms")
    class AgreeToTerms {

        @Test
        @DisplayName("정상 동의 → termAgreement=true, termAgreedAt 설정")
        void success() {
            User user = defaultUser();

            user.agreeToTerms();

            assertThat(user.isTermAgreement()).isTrue();
            assertThat(user.getTermAgreedAt()).isNotNull();
        }

        @Test
        @DisplayName("두 번 호출 → IllegalStateException")
        void alreadyAgreed() {
            User user = defaultUser();
            user.agreeToTerms();

            assertThatThrownBy(user::agreeToTerms).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("completeOnboarding")
    class CompleteOnboarding {

        @Test
        @DisplayName("정상 완료 → onboardingCompleted=true, userOnboarding 생성")
        void success() {
            User user = defaultUser();

            user.completeOnboarding(1, 2, 3);

            assertThat(user.isOnboardingCompleted()).isTrue();
            assertThat(user.getUserOnboarding()).isNotNull();
        }

        @Test
        @DisplayName("두 번 호출 → IllegalStateException")
        void alreadyCompleted() {
            User user = defaultUser();
            user.completeOnboarding(1, 2, 3);

            assertThatThrownBy(() -> user.completeOnboarding(1, 2, 3)).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("옵션 0 → IllegalArgumentException")
        void optionBelowMin() {
            User user = defaultUser();

            assertThatThrownBy(() -> user.completeOnboarding(0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("옵션 1(최솟값) → 성공")
        void optionMin() {
            assertThatNoException().isThrownBy(() -> defaultUser().completeOnboarding(1, 1, 1));
        }

        @Test
        @DisplayName("옵션 3(최댓값) → 성공")
        void optionMax() {
            assertThatNoException().isThrownBy(() -> defaultUser().completeOnboarding(3, 3, 3));
        }

        @Test
        @DisplayName("옵션 4 → IllegalArgumentException")
        void optionAboveMax() {
            User user = defaultUser();

            assertThatThrownBy(() -> user.completeOnboarding(4, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("withdraw")
    class Withdraw {

        @Test
        @DisplayName("정상 탈퇴 → PII 삭제, status=WITHDRAWN, deletedAt 설정")
        void success() {
            User user = defaultUser();

            user.withdraw();

            assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
            assertThat(user.getName()).isNull();
            assertThat(user.getPhoneNumber()).isNull();
            assertThat(user.getBirthday()).isNull();
            assertThat(user.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("이미 탈퇴한 사용자 → IllegalStateException")
        void alreadyWithdrawn() {
            User user = defaultUser();
            user.withdraw();

            assertThatThrownBy(user::withdraw).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("SUSPENDED 상태에서 탈퇴 시도 → IllegalStateException")
        void cannotWithdrawWhenSuspended() {
            User user = defaultUser();
            user.suspend();

            assertThatThrownBy(user::withdraw).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("suspend")
    class Suspend {

        @Test
        @DisplayName("ACTIVE → SUSPENDED 정상 처리")
        void success() {
            User user = defaultUser();

            user.suspend();

            assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        }

        @Test
        @DisplayName("WITHDRAWN 상태에서 정지 → IllegalStateException")
        void cannotSuspendWithdrawn() {
            User user = defaultUser();
            user.withdraw();

            assertThatThrownBy(user::suspend).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("이미 SUSPENDED 상태에서 정지 → IllegalStateException")
        void alreadySuspended() {
            User user = defaultUser();
            user.suspend();

            assertThatThrownBy(user::suspend).isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("reactivate")
    class Reactivate {

        @Test
        @DisplayName("SUSPENDED → ACTIVE 정상 복구")
        void success() {
            User user = defaultUser();
            user.suspend();

            user.reactivate();

            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("ACTIVE 상태에서 복구 시도 → IllegalStateException")
        void cannotReactivateActive() {
            User user = defaultUser();

            assertThatThrownBy(user::reactivate).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("WITHDRAWN 상태에서 복구 시도 → IllegalStateException")
        void cannotReactivateWithdrawn() {
            User user = defaultUser();
            user.withdraw();

            assertThatThrownBy(user::reactivate).isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("ACTIVE → SUSPENDED → ACTIVE → SUSPENDED 반복 가능")
        void suspendReactivateCycle() {
            User user = defaultUser();

            user.suspend();
            user.reactivate();
            user.suspend();

            assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        }
    }

    @Nested
    @DisplayName("changeNickname")
    class ChangeNickname {

        @Test
        @DisplayName("정상 변경")
        void success() {
            User user = defaultUser();

            user.changeNickname("새닉네임");

            assertThat(user.getNickname()).isEqualTo("새닉네임");
        }

        @Test
        @DisplayName("null → NullPointerException")
        void nullNickname() {
            User user = defaultUser();

            assertThatThrownBy(() -> user.changeNickname(null)).isInstanceOf(NullPointerException.class);
        }
    }
}
