package com.muffin.notification.application;

import com.muffin.notification.domain.NotificationSettings;
import com.muffin.notification.presentation.dto.MyPageSettingsResponse;
import com.muffin.notification.presentation.dto.NotificationSettingsItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 마이페이지 설정(현재는 알림 설정만) 조회 유스케이스. */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationSettingsQueryService {

    private final NotificationSettingsFinder notificationSettingsFinder;

    public MyPageSettingsResponse getSettings(Long userId) {
        NotificationSettings settings = notificationSettingsFinder.findOrCreate(userId);

        return new MyPageSettingsResponse(new NotificationSettingsItem(
                settings.isNewsUpdatePushEnabled(),
                settings.isQuizPushEnabled(),
                settings.isInvestmentResultPushEnabled(),
                settings.isRankingChangedPushEnabled()));
    }
}
