package com.muffin.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.muffin.notification.domain.NotificationSettings;
import com.muffin.notification.presentation.dto.NotificationSettingsResponse;
import com.muffin.notification.presentation.dto.NotificationSettingsUpdateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsCommandServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private NotificationSettingsFinder notificationSettingsFinder;

    @InjectMocks
    private NotificationSettingsCommandService notificationSettingsCommandService;

    @Test
    @DisplayName("finder가 돌려준 설정을 4개 필드 모두 갱신한다")
    void updatesSettingsFromFinder() {
        NotificationSettings settings = NotificationSettings.createDefault(USER_ID);
        when(notificationSettingsFinder.findOrCreate(USER_ID)).thenReturn(settings);

        NotificationSettingsResponse response = notificationSettingsCommandService.updateSettings(
                USER_ID, new NotificationSettingsUpdateRequest(false, true, false, true));

        assertThat(response.notifications().newsUpdate()).isFalse();
        assertThat(response.notifications().dailyQuiz()).isTrue();
        assertThat(response.notifications().investResult()).isFalse();
        assertThat(response.notifications().rankingChange()).isTrue();
    }
}
