package com.muffin.notification.application.send;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.notification.application.dispatch.NotificationDispatchSummary;
import com.muffin.notification.application.dispatch.NotificationDispatcher;
import com.muffin.notification.application.dispatch.NotificationRecipientQueryRepository;
import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.domain.delivery.NotificationType;
import com.muffin.ranking.domain.weeklyranking.WeeklyRanking;
import com.muffin.ranking.domain.weeklyranking.WeeklyRankingRepository;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RankingNotificationServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 2026-10-19는 월요일이다. 지난주는 10/12~10/18. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    private static final LocalDate LAST_WEEK = MONDAY.minusWeeks(1);

    private final WeeklyRankingRepository weeklyRankingRepository = mock(WeeklyRankingRepository.class);
    private final TradingCalendarService tradingCalendarService = mock(TradingCalendarService.class);
    private final NotificationRecipientQueryRepository recipientQueryRepository =
            mock(NotificationRecipientQueryRepository.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);

    @Test
    @DisplayName("첫 영업일 12:00에 지난주 랭킹을 ISO 주차 키로 보내고, 전전주 순위와 첫 랭킹 여부로 문구를 고른다")
    @SuppressWarnings("unchecked")
    void sendsOnFirstTradingDayAtNoon() {
        RankingNotificationService service = serviceAt(MONDAY.atTime(12, 0));
        givenTradingDay(MONDAY, MONDAY.minusDays(3));
        WeeklyRanking climber = ranking(1L, 20, 40);
        WeeklyRanking newcomer = ranking(2L, 30, 60);
        when(weeklyRankingRepository.findAllByWeekStartDate(LAST_WEEK)).thenReturn(List.of(climber, newcomer));
        WeeklyRanking climberBefore = ranking(1L, 25, 50);
        when(weeklyRankingRepository.findAllByWeekStartDate(LAST_WEEK.minusWeeks(1)))
                .thenReturn(List.of(climberBefore));
        when(weeklyRankingRepository.findUserIdsRankedBefore(eq(LAST_WEEK), any()))
                .thenReturn(List.of(1L));
        when(recipientQueryRepository.findRecipientUserIds(NotificationType.RANKING))
                .thenReturn(List.of(1L, 2L));
        when(dispatcher.dispatchAll(any(), anyString(), anyMap())).thenReturn(NotificationDispatchSummary.empty());

        service.notifyIfDue();

        ArgumentCaptor<Map<Long, PushMessage>> messages = ArgumentCaptor.forClass(Map.class);
        verify(dispatcher).dispatchAll(eq(NotificationType.RANKING), eq("2026-W42"), messages.capture());
        assertThat(messages.getValue().get(1L).title()).isEqualTo("지난주 20위, 5계단 올랐어요 ▲");
        assertThat(messages.getValue().get(2L).title()).isEqualTo("첫 주간 랭킹이 나왔어요 🧁");
    }

    @Test
    @DisplayName("12:00 전에 확정 이벤트가 오면 보내지 않고 12:00 스케줄러에 맡긴다")
    void skipsBeforeNoon() {
        NotificationDispatchSummary summary = serviceAt(MONDAY.atTime(9, 40)).notifyIfDue();

        assertThat(summary.skipReason()).isEqualTo("outside_window");
        verify(weeklyRankingRepository, never()).findAllByWeekStartDate(any());
    }

    @Test
    @DisplayName("20:00이 지나면 그 주는 보내지 않는다")
    void skipsAfterEight() {
        assertThat(serviceAt(MONDAY.atTime(20, 0)).notifyIfDue().skipReason()).isEqualTo("outside_window");
    }

    @Test
    @DisplayName("월요일이 휴장이면 화요일이 첫 영업일이다")
    void tuesdayAfterMondayHoliday() {
        LocalDate tuesday = MONDAY.plusDays(1);
        RankingNotificationService service = serviceAt(tuesday.atTime(12, 0));
        givenTradingDay(tuesday, MONDAY.minusDays(3));
        when(weeklyRankingRepository.findAllByWeekStartDate(LAST_WEEK)).thenReturn(List.of());

        // 첫 영업일 판정을 통과해 랭킹 조회까지 간다.
        assertThat(service.notifyIfDue().skipReason()).isEqualTo("ranking_not_ready");
    }

    @Test
    @DisplayName("첫 영업일이 지난 날에는 보내지 않는다")
    void skipsAfterFirstTradingDay() {
        LocalDate tuesday = MONDAY.plusDays(1);
        RankingNotificationService service = serviceAt(tuesday.atTime(12, 0));
        givenTradingDay(tuesday, MONDAY);

        assertThat(service.notifyIfDue().skipReason()).isEqualTo("not_first_trading_day");
        verify(weeklyRankingRepository, never()).findAllByWeekStartDate(any());
    }

    @Test
    @DisplayName("ISO 주차는 연도 경계에서도 주 기준 연도를 쓴다")
    void isoWeekAcrossYearBoundary() {
        assertThat(RankingNotificationService.isoWeek(LocalDate.of(2026, 12, 28)))
                .isEqualTo("2026-W53");
        assertThat(RankingNotificationService.isoWeek(LocalDate.of(2027, 1, 4))).isEqualTo("2027-W01");
    }

    private RankingNotificationService serviceAt(LocalDateTime now) {
        return new RankingNotificationService(
                weeklyRankingRepository,
                tradingCalendarService,
                recipientQueryRepository,
                dispatcher,
                Clock.fixed(now.atZone(KST).toInstant(), KST));
    }

    private void givenTradingDay(LocalDate date, LocalDate previousTradingDay) {
        when(tradingCalendarService.getCalendar(date))
                .thenReturn(new TradingCalendar(date, true, previousTradingDay, date.plusDays(1)));
    }

    private static WeeklyRanking ranking(Long userId, int position, int percentile) {
        WeeklyRanking ranking = mock(WeeklyRanking.class);
        when(ranking.getUserId()).thenReturn(userId);
        when(ranking.getRankingPosition()).thenReturn(position);
        when(ranking.getPercentile()).thenReturn(percentile);
        when(ranking.getWeeklyProfitRate()).thenReturn(new BigDecimal("1.50"));
        return ranking;
    }
}
