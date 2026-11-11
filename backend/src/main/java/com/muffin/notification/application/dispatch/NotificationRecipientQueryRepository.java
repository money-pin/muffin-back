package com.muffin.notification.application.dispatch;

import com.muffin.notification.domain.delivery.NotificationType;
import java.util.List;

/** 알림을 받을 사용자를 고른다. */
public interface NotificationRecipientQueryRepository {

    /**
     * 활성 상태이며 해당 알림을 켜 둔 사용자 ID를 오름차순으로 조회한다. 기기가 있는지는 발송기({@link PushSender})가 판단한다.
     *
     * <p>알림 설정 행이 아직 없는 사용자는 기본값(ON)으로 본다. 설정은 가입 시 또는 처음 조회할 때 만들어지므로 행이 없을 수 있다.
     */
    List<Long> findRecipientUserIds(NotificationType type);
}
