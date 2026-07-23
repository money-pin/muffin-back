package com.muffin.notification.application;

import com.muffin.notification.domain.NotificationSettings;
import com.muffin.notification.domain.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 알림 설정을 조회하고, 없으면 기본값을 생성해서 돌려준다(get-or-create).
 *
 * <p>같은 유저에 대한 최초 조회/변경 요청이 동시에 들어오면 둘 다 findById에서 빈 값을 보고 둘 다 생성을 시도할 수 있다. 이때
 * 뒤늦게 저장을 시도한 쪽은 {@code user_id} PK 충돌로 실패하므로, 그 경우 상대가 먼저 만든 행을 다시 조회해서 반환한다
 * ({@link com.muffin.news.application.query.NewsQueryService}의 read_history upsert와 동일한 방어 패턴).
 */
@Component
@RequiredArgsConstructor
class NotificationSettingsFinder {

    private final NotificationSettingsRepository notificationSettingsRepository;

    NotificationSettings findOrCreate(Long userId) {
        return notificationSettingsRepository.findById(userId).orElseGet(() -> createDefault(userId));
    }

    private NotificationSettings createDefault(Long userId) {
        try {
            return notificationSettingsRepository.saveAndFlush(NotificationSettings.createDefault(userId));
        } catch (DataIntegrityViolationException e) {
            return notificationSettingsRepository.findById(userId).orElseThrow(() -> e);
        }
    }
}
