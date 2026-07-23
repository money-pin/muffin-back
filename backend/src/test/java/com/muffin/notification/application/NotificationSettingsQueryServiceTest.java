package com.muffin.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.notification.domain.NotificationSettings;
import com.muffin.notification.domain.NotificationSettingsRepository;
import com.muffin.notification.presentation.dto.MyPageSettingsResponse;
import java.util.Optional;
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
    private NotificationSettingsRepository notificationSettingsRepository;

    @InjectMocks
    private NotificationSettingsQueryService notificationSettingsQueryService;

    @Test
    @DisplayName("기존 설정이 있으면 그대로 반환한다")
    void returnsExistingSettings() {
        NotificationSettings settings = NotificationSettings.createDefault(USER_ID);
        settings.updateRankingChangedPush(false);
        when(notificationSettingsRepository.findById(USER_ID)).thenReturn(Optional.of(settings));

        MyPageSettingsResponse response = notificationSettingsQueryService.getSettings(USER_ID);

        assertThat(response.notifications().newsUpdate()).isTrue();
        assertThat(response.notifications().dailyQuiz()).isTrue();
        assertThat(response.notifications().investResult()).isTrue();
        assertThat(response.notifications().rankingChange()).isFalse();
    }

    @Test
    @DisplayName("설정이 없으면 기본값(모두 true)을 생성해서 반환한다")
    void createsDefaultWhenMissing() {
        when(notificationSettingsRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(notificationSettingsRepository.save(any(NotificationSettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MyPageSettingsResponse response = notificationSettingsQueryService.getSettings(USER_ID);

        assertThat(response.notifications().newsUpdate()).isTrue();
        assertThat(response.notifications().dailyQuiz()).isTrue();
        assertThat(response.notifications().investResult()).isTrue();
        assertThat(response.notifications().rankingChange()).isTrue();
        verify(notificationSettingsRepository).save(any(NotificationSettings.class));
    }
}
