package com.muffin.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.muffin.notification.domain.NotificationSettings;
import com.muffin.notification.presentation.dto.NotificationSettingsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsQueryServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private NotificationSettingsFinder notificationSettingsFinder;

    @InjectMocks
    private NotificationSettingsQueryService notificationSettingsQueryService;

    @Test
    @DisplayName("finder가 돌려준 설정을 그대로 응답에 반영한다")
    void returnsSettingsFromFinder() {
        NotificationSettings settings = NotificationSettings.createDefault(USER_ID);
        settings.updateRankingChangedPush(false);
        when(notificationSettingsFinder.findOrCreate(USER_ID)).thenReturn(settings);

        NotificationSettingsResponse response = notificationSettingsQueryService.getSettings(USER_ID);

        assertThat(response.notifications().newsUpdate()).isTrue();
        assertThat(response.notifications().dailyQuiz()).isTrue();
        assertThat(response.notifications().investResult()).isTrue();
        assertThat(response.notifications().rankingChange()).isFalse();
    }
}
