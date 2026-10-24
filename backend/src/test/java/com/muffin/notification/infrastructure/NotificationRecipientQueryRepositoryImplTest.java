package com.muffin.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.muffin.notification.application.dispatch.NotificationRecipientQueryRepository;
import com.muffin.notification.domain.NotificationSettings;
import com.muffin.notification.domain.NotificationSettingsRepository;
import com.muffin.notification.domain.delivery.NotificationType;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 다른 테스트가 남긴 사용자가 있어도 깨지지 않도록, 이 테스트가 만든 사용자의 포함 여부만 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class NotificationRecipientQueryRepositoryImplTest {

    @Autowired
    private NotificationRecipientQueryRepository recipientQueryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationSettingsRepository notificationSettingsRepository;

    @AfterEach
    void cleanUp() {
        notificationSettingsRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("활성 사용자 중 해당 알림을 켠 사용자만 고른다")
    void selectsUsersWithToggleOn() {
        Long on = saveUser();
        Long off = saveUser();
        NotificationSettings offSettings = NotificationSettings.createDefault(off);
        offSettings.updateQuizPush(false);
        notificationSettingsRepository.save(offSettings);
        notificationSettingsRepository.save(NotificationSettings.createDefault(on));

        assertThat(recipientQueryRepository.findRecipientUserIds(NotificationType.QUIZ))
                .contains(on)
                .doesNotContain(off);
        assertThat(recipientQueryRepository.findRecipientUserIds(NotificationType.NEWS))
                .contains(on, off);
    }

    @Test
    @DisplayName("브리핑 알림은 뉴스 알림 토글을 따른다")
    void briefingFollowsNewsToggle() {
        Long userId = saveUser();
        NotificationSettings settings = NotificationSettings.createDefault(userId);
        settings.updateNewsUpdatePush(false);
        notificationSettingsRepository.save(settings);

        assertThat(recipientQueryRepository.findRecipientUserIds(NotificationType.BRIEFING))
                .doesNotContain(userId);
    }

    @Test
    @DisplayName("알림 설정 행이 없는 사용자는 기본값(ON)으로 본다")
    void treatsMissingSettingsAsOn() {
        Long userId = saveUser();

        assertThat(recipientQueryRepository.findRecipientUserIds(NotificationType.RANKING))
                .contains(userId);
    }

    @Test
    @DisplayName("탈퇴한 사용자는 빠진다")
    void excludesWithdrawn() {
        Long active = saveUser();
        User withdrawn = userRepository.save(newUser());
        withdrawn.withdraw();
        userRepository.save(withdrawn);

        assertThat(recipientQueryRepository.findRecipientUserIds(NotificationType.NEWS))
                .contains(active)
                .doesNotContain(withdrawn.getUserId());
    }

    private Long saveUser() {
        return userRepository.save(newUser()).getUserId();
    }

    private static User newUser() {
        return User.register(null, UUID.randomUUID().toString(), "홍길동", null);
    }
}
