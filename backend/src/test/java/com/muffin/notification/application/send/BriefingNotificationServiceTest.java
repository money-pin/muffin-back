package com.muffin.notification.application.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.briefing.domain.Briefing;
import com.muffin.briefing.domain.BriefingRepository;
import com.muffin.briefing.domain.enums.BriefingStatus;
import com.muffin.notification.application.dispatch.NotificationDispatchSummary;
import com.muffin.notification.application.dispatch.NotificationDispatcher;
import com.muffin.notification.application.dispatch.NotificationRecipientQueryRepository;
import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.domain.delivery.NotificationType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BriefingNotificationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);

    private final BriefingRepository briefingRepository = mock(BriefingRepository.class);
    private final NotificationRecipientQueryRepository recipientQueryRepository =
            mock(NotificationRecipientQueryRepository.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final BriefingNotificationService service = new BriefingNotificationService(
            briefingRepository,
            recipientQueryRepository,
            dispatcher,
            Clock.fixed(TODAY.atTime(7, 30).atZone(KST).toInstant(), KST));

    @Test
    @DisplayName("오늘 브리핑이 09:00 전에 공개되면 한 줄 요약을 담아 보낸다")
    void sendsTodayBriefing() {
        Briefing briefing = mock(Briefing.class);
        when(briefing.getHeadline()).thenReturn("코스피가 숨 고르기에 들어갔어요.");
        when(briefingRepository.findByBriefingDateAndStatus(TODAY, BriefingStatus.PUBLISHED))
                .thenReturn(Optional.of(briefing));
        when(recipientQueryRepository.findRecipientUserIds(NotificationType.BRIEFING))
                .thenReturn(List.of(1L, 2L));
        when(dispatcher.dispatchAll(any(), anyString(), anyList(), any(PushMessage.class)))
                .thenReturn(NotificationDispatchSummary.empty());

        service.notifyPublished(TODAY, TODAY.atTime(7, 30));

        verify(dispatcher)
                .dispatchAll(
                        eq(NotificationType.BRIEFING),
                        eq("2026-10-20"),
                        eq(List.of(1L, 2L)),
                        eq(new PushMessage(
                                "모닝 머핀 브리핑이 구워졌어요 🧁", "코스피가 숨 고르기에 들어갔어요.", "muffin://briefing/2026-10-20")));
    }

    @Test
    @DisplayName("누락 복구로 함께 공개된 지난 날짜 브리핑은 보내지 않는다")
    void skipsPastBriefing() {
        NotificationDispatchSummary summary = service.notifyPublished(TODAY.minusDays(3), TODAY.atTime(7, 30));

        assertThat(summary.skipReason()).isEqualTo("past_briefing");
        verify(dispatcher, never()).dispatchAll(any(), anyString(), anyList(), any(PushMessage.class));
    }

    @Test
    @DisplayName("09:00 이후에 늦게 공개된 브리핑은 뉴스 알림과 겹치므로 보내지 않는다")
    void skipsLatePublication() {
        NotificationDispatchSummary summary = service.notifyPublished(TODAY, TODAY.atTime(9, 0));

        assertThat(summary.skipReason()).isEqualTo("late_publication");
        verify(dispatcher, never()).dispatchAll(any(), anyString(), anyList(), any(PushMessage.class));
    }
}
