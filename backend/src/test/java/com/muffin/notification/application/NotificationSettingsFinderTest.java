package com.muffin.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.notification.domain.NotificationSettings;
import com.muffin.notification.domain.NotificationSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsFinderTest {

    private static final Long USER_ID = 1L;

    @Mock
    private NotificationSettingsRepository notificationSettingsRepository;

    @Mock
    private NotificationSettingsCreator notificationSettingsCreator;

    @InjectMocks
    private NotificationSettingsFinder notificationSettingsFinder;

    @Test
    @DisplayName("기존 설정이 있으면 그대로 반환한다")
    void returnsExistingSettings() {
        NotificationSettings settings = NotificationSettings.createDefault(USER_ID);
        when(notificationSettingsRepository.findById(USER_ID)).thenReturn(Optional.of(settings));

        NotificationSettings result = notificationSettingsFinder.findOrCreate(USER_ID);

        assertThat(result).isSameAs(settings);
    }

    @Test
    @DisplayName("설정이 없으면 기본값을 생성해서 저장하고, 호출자 트랜잭션에서 다시 조회해서 반환한다")
    void createsDefaultWhenMissing() {
        NotificationSettings created = NotificationSettings.createDefault(USER_ID);
        when(notificationSettingsRepository.findById(USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(created));

        NotificationSettings result = notificationSettingsFinder.findOrCreate(USER_ID);

        verify(notificationSettingsCreator).createNew(USER_ID);
        assertThat(result).isSameAs(created);
    }

    @Test
    @DisplayName("동시 생성 레이스: 저장이 PK 충돌로 실패하면 상대가 먼저 만든 행을 다시 조회해서 반환한다")
    void raceOnCreate_fallsBackToExistingRow() {
        NotificationSettings createdByOtherRequest = NotificationSettings.createDefault(USER_ID);
        when(notificationSettingsRepository.findById(USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(createdByOtherRequest));
        when(notificationSettingsCreator.createNew(eq(USER_ID))).thenThrow(mock(DataIntegrityViolationException.class));

        NotificationSettings result = notificationSettingsFinder.findOrCreate(USER_ID);

        assertThat(result).isSameAs(createdByOtherRequest);
    }
}
