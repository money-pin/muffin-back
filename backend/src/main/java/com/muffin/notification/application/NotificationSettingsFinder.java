package com.muffin.notification.application;

import com.muffin.global.apiPayload.code.GeneralErrorCode;
import com.muffin.global.apiPayload.exception.GeneralException;
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
 * ({@link com.muffin.news.application.query.NewsQueryService}의 read_history upsert와 동일한 방어 패턴). 저장
 * 시도는 {@link NotificationSettingsCreator}의 별도 트랜잭션에서 이루어진다 — 호출자의 트랜잭션 안에서 바로 저장을
 * 시도하면 PK 충돌 시 호출자 트랜잭션 전체가 rollback-only로 오염되기 때문이다.
 *
 * <p>생성 시도가 성공하든 실패하든, 반환하는 행은 항상 호출자의 트랜잭션에서 다시 조회한다. {@code
 * NotificationSettingsCreator}가 반환하는 엔티티는 이미 커밋되어 닫힌 별도 트랜잭션에 속해 있어 호출자 입장에서는
 * detached 상태이므로, 그대로 반환하면 이후 호출자가 필드를 변경해도(예: 설정 변경 API) 더티 체킹이 되지 않아 저장이 유실된다.
 */
@Component
@RequiredArgsConstructor
class NotificationSettingsFinder {

    private final NotificationSettingsRepository notificationSettingsRepository;
    private final NotificationSettingsCreator notificationSettingsCreator;

    NotificationSettings findOrCreate(Long userId) {
        return notificationSettingsRepository.findById(userId).orElseGet(() -> createDefault(userId));
    }

    private NotificationSettings createDefault(Long userId) {
        try {
            notificationSettingsCreator.createNew(userId);
        } catch (DataIntegrityViolationException ignored) {
            // 동시 생성 레이스: 상대가 먼저 만들었다. 아래에서 그 행을 호출자 트랜잭션으로 다시 조회한다.
        }
        return notificationSettingsRepository
                .findById(userId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.INTERNAL_SERVER_ERROR, "알림 설정을 생성했지만 조회할 수 없습니다: userId=" + userId));
    }
}
