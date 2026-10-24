package com.muffin.notification.application.send;

import com.muffin.briefing.domain.Briefing;
import com.muffin.briefing.domain.BriefingRepository;
import com.muffin.briefing.domain.enums.BriefingStatus;
import com.muffin.notification.application.dispatch.NotificationDispatchSummary;
import com.muffin.notification.application.dispatch.NotificationDispatcher;
import com.muffin.notification.application.dispatch.NotificationRecipientQueryRepository;
import com.muffin.notification.application.message.NotificationMessages;
import com.muffin.notification.domain.delivery.NotificationType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * NOTI-01 모닝 브리핑 알림.
 *
 * <p>오늘 날짜의 브리핑이 09:00 전에 공개됐을 때만 보낸다. 지난 날짜 브리핑은 누락 복구로 오늘 함께 공개될 수 있는데, 그걸 알리면 어제 소식을 오늘 아침
 * 알림으로 받게 된다. 09:00 이후에 늦게 공개된 브리핑은 같은 시각의 뉴스 알림(NOTI-05)과 겹치므로 보내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BriefingNotificationService {

    static final LocalTime NOTIFICATION_DEADLINE = LocalTime.of(9, 0);

    private final BriefingRepository briefingRepository;
    private final NotificationRecipientQueryRepository recipientQueryRepository;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    public NotificationDispatchSummary notifyPublished(LocalDate briefingDate, LocalDateTime publishedAt) {
        if (!briefingDate.equals(LocalDate.now(clock))) {
            return NotificationDispatchSummary.skipped("past_briefing");
        }
        if (!publishedAt.toLocalTime().isBefore(NOTIFICATION_DEADLINE)) {
            return NotificationDispatchSummary.skipped("late_publication");
        }
        Optional<Briefing> briefing =
                briefingRepository.findByBriefingDateAndStatus(briefingDate, BriefingStatus.PUBLISHED);
        if (briefing.isEmpty()) {
            return NotificationDispatchSummary.skipped("briefing_not_published");
        }

        return dispatcher.dispatchAll(
                NotificationType.BRIEFING,
                briefingDate.toString(),
                recipientQueryRepository.findRecipientUserIds(NotificationType.BRIEFING),
                NotificationMessages.briefing(briefingDate, briefing.get().getHeadline()));
    }
}
