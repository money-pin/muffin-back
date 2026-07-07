package notification.domain;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationSettingsTest {

    @Nested
    @DisplayName("createDefault")
    class CreateDefault {

        @Test
        @DisplayName("정상 생성 시 모든 알림 기본값 true, createdAt 설정, updatedAt은 null")
        void success() {
            NotificationSettings settings = NotificationSettings.createDefault(1L);

            assertThat(settings.getUserId()).isEqualTo(1L);
            assertThat(settings.isQuizPushEnabled()).isTrue();
            assertThat(settings.isInvestmentResultPushEnabled()).isTrue();
            assertThat(settings.isRankingChangedPushEnabled()).isTrue();
            assertThat(settings.isNewsUpdatePushEnabled()).isTrue();
            assertThat(settings.getCreatedAt()).isNotNull();
            assertThat(settings.getUpdatedAt()).isNull();
        }

        @Test
        @DisplayName("null userId → NullPointerException")
        void nullUserId() {
            assertThatThrownBy(() -> NotificationSettings.createDefault(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("updateQuizPush")
    class UpdateQuizPush {

        @Test
        @DisplayName("true → false 변경 후 updatedAt 설정")
        void disablePush() {
            NotificationSettings settings = NotificationSettings.createDefault(1L);

            settings.updateQuizPush(false);

            assertThat(settings.isQuizPushEnabled()).isFalse();
            assertThat(settings.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("false → true 재활성화")
        void reenablePush() {
            NotificationSettings settings = NotificationSettings.createDefault(1L);
            settings.updateQuizPush(false);

            settings.updateQuizPush(true);

            assertThat(settings.isQuizPushEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateInvestmentResultPush")
    class UpdateInvestmentResultPush {

        @Test
        @DisplayName("true → false 변경 후 updatedAt 설정")
        void disablePush() {
            NotificationSettings settings = NotificationSettings.createDefault(1L);

            settings.updateInvestmentResultPush(false);

            assertThat(settings.isInvestmentResultPushEnabled()).isFalse();
            assertThat(settings.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("false → true 재활성화")
        void reenablePush() {
            NotificationSettings settings = NotificationSettings.createDefault(1L);
            settings.updateInvestmentResultPush(false);

            settings.updateInvestmentResultPush(true);

            assertThat(settings.isInvestmentResultPushEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateRankingChangedPush")
    class UpdateRankingChangedPush {

        @Test
        @DisplayName("true → false 변경 후 updatedAt 설정")
        void disablePush() {
            NotificationSettings settings = NotificationSettings.createDefault(1L);

            settings.updateRankingChangedPush(false);

            assertThat(settings.isRankingChangedPushEnabled()).isFalse();
            assertThat(settings.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("false → true 재활성화")
        void reenablePush() {
            NotificationSettings settings = NotificationSettings.createDefault(1L);
            settings.updateRankingChangedPush(false);

            settings.updateRankingChangedPush(true);

            assertThat(settings.isRankingChangedPushEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateNewsUpdatePush")
    class UpdateNewsUpdatePush {

        @Test
        @DisplayName("true → false 변경 후 updatedAt 설정")
        void disablePush() {
            NotificationSettings settings = NotificationSettings.createDefault(1L);

            settings.updateNewsUpdatePush(false);

            assertThat(settings.isNewsUpdatePushEnabled()).isFalse();
            assertThat(settings.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("false → true 재활성화")
        void reenablePush() {
            NotificationSettings settings = NotificationSettings.createDefault(1L);
            settings.updateNewsUpdatePush(false);

            settings.updateNewsUpdatePush(true);

            assertThat(settings.isNewsUpdatePushEnabled()).isTrue();
        }
    }
}
