package com.muffin.notification.application;

import com.muffin.notification.domain.NotificationSettings;
import com.muffin.notification.domain.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 설정 기본값 생성을 별도의 물리 트랜잭션({@code REQUIRES_NEW})으로 격리한다.
 *
 * <p>{@link NotificationSettingsFinder#findOrCreate}는 항상 호출자({@code
 * NotificationSettingsQueryService}/{@code NotificationSettingsCommandService})의 기존 트랜잭션에 참여한
 * 채로 실행된다. 만약 저장 시도가 이 참여 트랜잭션 안에서 이루어지면, PK 충돌로 실패했을 때 Spring이 그 예외를 잡기도 전에 물리
 * 트랜잭션 전체를 rollback-only로 표시해버려서(참여 트랜잭션 실패 시 기본 동작), 이후 호출자 트랜잭션이 정상 커밋을 시도해도
 * {@code UnexpectedRollbackException}이 발생한다. {@code REQUIRES_NEW}로 새 물리 트랜잭션을 열어 저장을 시도하면 실패
 * 시 이 트랜잭션만 롤백되고 호출자의 트랜잭션은 오염되지 않는다.
 */
@Component
@RequiredArgsConstructor
class NotificationSettingsCreator {

    private final NotificationSettingsRepository notificationSettingsRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    NotificationSettings createNew(Long userId) {
        return notificationSettingsRepository.saveAndFlush(NotificationSettings.createDefault(userId));
    }
}
