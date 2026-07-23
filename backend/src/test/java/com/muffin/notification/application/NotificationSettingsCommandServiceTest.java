package com.muffin.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.muffin.notification.domain.NotificationSettings;
import com.muffin.notification.domain.NotificationSettingsRepository;
import com.muffin.notification.presentation.dto.MyPageSettingsResponse;
import com.muffin.notification.presentation.dto.NotificationSettingsUpdateRequest;
import java.util.Optional;
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
    private NotificationSettingsRepository notificationSettingsRepository;

    @InjectMocks
    private NotificationSettingsCommandService notificationSettingsCommandService;

    @Test
    @DisplayName("기존 설정을 4개 필드 모두 갱신한다")
    void updatesExistingSettings() {
        NotificationSettings settings = NotificationSettings.createDefault(USER_ID);
        when(notificationSettingsRepository.findById(USER_ID)).thenReturn(Optional.of(settings));

        MyPageSettingsResponse response = notificationSettingsCommandService.updateSettings(
                USER_ID, new NotificationSettingsUpdateRequest(false, true, false, true));

        assertThat(response.notifications().newsUpdate()).isFalse();
        assertThat(response.notifications().dailyQuiz()).isTrue();
        assertThat(response.notifications().investResult()).isFalse();
        assertThat(response.notifications().rankingChange()).isTrue();
    }

    @Test
    @DisplayName("설정이 없던 유저는 기본값 생성 후 바로 갱신된 값이 반영된다")
    void createsDefaultThenUpdates() {
        when(notificationSettingsRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(notificationSettingsRepository.save(any(NotificationSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MyPageSettingsResponse response = notificationSettingsCommandService.updateSettings(
                USER_ID, new NotificationSettingsUpdateRequest(false, false, false, false));

        assertThat(response.notifications().newsUpdate()).isFalse();
        assertThat(response.notifications().dailyQuiz()).isFalse();
        assertThat(response.notifications().investResult()).isFalse();
        assertThat(response.notifications().rankingChange()).isFalse();
    }
}
