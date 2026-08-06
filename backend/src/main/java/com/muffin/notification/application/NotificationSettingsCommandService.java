package com.muffin.notification.application;

import com.muffin.notification.domain.NotificationSettings;
import com.muffin.notification.presentation.dto.MypageSettingsResponse;
import com.muffin.notification.presentation.dto.NotificationSettingsItem;
import com.muffin.notification.presentation.dto.NotificationSettingsUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 설정 변경 유스케이스. */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationSettingsCommandService {

    private final NotificationSettingsFinder notificationSettingsFinder;

    public MypageSettingsResponse updateSettings(Long userId, NotificationSettingsUpdateRequest request) {
        NotificationSettings settings = notificationSettingsFinder.findOrCreate(userId);

        settings.updateNewsUpdatePush(request.newsUpdate());
        settings.updateQuizPush(request.dailyQuiz());
        settings.updateInvestmentResultPush(request.investResult());
        settings.updateRankingChangedPush(request.rankingChange());

        return new MypageSettingsResponse(new NotificationSettingsItem(
                settings.isNewsUpdatePushEnabled(),
                settings.isQuizPushEnabled(),
                settings.isInvestmentResultPushEnabled(),
                settings.isRankingChangedPushEnabled()));
    }
}
