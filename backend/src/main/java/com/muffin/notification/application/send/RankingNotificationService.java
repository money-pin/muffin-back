package com.muffin.notification.application.send;

import com.muffin.notification.application.dispatch.NotificationDispatchSummary;
import com.muffin.notification.application.dispatch.NotificationDispatcher;
import com.muffin.notification.application.dispatch.NotificationRecipientQueryRepository;
import com.muffin.notification.application.dispatch.PushMessage;
import com.muffin.notification.application.message.NotificationMessages;
import com.muffin.notification.domain.delivery.NotificationType;
import com.muffin.ranking.domain.weeklyranking.WeeklyRanking;
import com.muffin.ranking.domain.weeklyranking.WeeklyRankingRepository;
import com.muffin.sector.application.TradingCalendarService;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * NOTI-04 랭킹 변동 알림.
 *
 * <p>이번 주 첫 영업일 12:00~20:00 사이에만 보낸다. 오전에 몰리는 알림(브리핑·뉴스·투자 결과)과 시간을 나누기 위해 12:00 전에 랭킹이 확정되면 12:00
 * 스케줄러가 보내고, 12:00 이후에 늦게 확정되면 확정 이벤트에서 바로 보낸다. 20:00까지 확정되지 않으면 그 주는 보내지 않는다. 첫 영업일이 지난 뒤 늦게 확정된
 * 랭킹도 보내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RankingNotificationService {

    static final LocalTime WINDOW_START = LocalTime.of(12, 0);
    static final LocalTime WINDOW_END = LocalTime.of(20, 0);

    private final WeeklyRankingRepository weeklyRankingRepository;
    private final TradingCalendarService tradingCalendarService;
    private final NotificationRecipientQueryRepository recipientQueryRepository;
    private final NotificationDispatcher dispatcher;
    private final Clock clock;

    /** 지금이 보낼 때면 지난주 랭킹 알림을 보낸다. 12:00 스케줄러와 랭킹 확정 이벤트가 함께 부른다. */
    public NotificationDispatchSummary notifyIfDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalTime time = now.toLocalTime();
        if (time.isBefore(WINDOW_START) || !time.isBefore(WINDOW_END)) {
            return NotificationDispatchSummary.skipped("outside_window");
        }
        LocalDate today = now.toLocalDate();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (!isFirstTradingDayOfWeek(today, thisMonday)) {
            return NotificationDispatchSummary.skipped("not_first_trading_day");
        }

        LocalDate weekStartDate = thisMonday.minusWeeks(1);
        List<WeeklyRanking> rankings = weeklyRankingRepository.findAllByWeekStartDate(weekStartDate);
        if (rankings.isEmpty()) {
            return NotificationDispatchSummary.skipped("ranking_not_ready");
        }

        Set<Long> recipients = new HashSet<>(recipientQueryRepository.findRecipientUserIds(NotificationType.RANKING));
        // 상위 %는 랭킹 배치가 항상 채운다. 컬럼이 NULL을 허용해 생길 수 있는 예전 행만 거른다.
        List<WeeklyRanking> targets = rankings.stream()
                .filter(ranking -> recipients.contains(ranking.getUserId()))
                .filter(ranking -> ranking.getPercentile() != null)
                .toList();
        if (targets.isEmpty()) {
            return NotificationDispatchSummary.empty();
        }

        List<Long> targetUserIds =
                targets.stream().map(WeeklyRanking::getUserId).toList();
        Map<Long, Integer> previousPositions =
                weeklyRankingRepository.findAllByWeekStartDate(weekStartDate.minusWeeks(1)).stream()
                        .collect(Collectors.toMap(WeeklyRanking::getUserId, WeeklyRanking::getRankingPosition));
        Set<Long> rankedBefore =
                new HashSet<>(weeklyRankingRepository.findUserIdsRankedBefore(weekStartDate, targetUserIds));

        Map<Long, PushMessage> messages = new LinkedHashMap<>();
        for (WeeklyRanking ranking : targets) {
            messages.put(
                    ranking.getUserId(),
                    NotificationMessages.ranking(
                            ranking.getRankingPosition(),
                            ranking.getPercentile(),
                            ranking.getWeeklyProfitRate(),
                            previousPositions.get(ranking.getUserId()),
                            !rankedBefore.contains(ranking.getUserId())));
        }
        return dispatcher.dispatchAll(NotificationType.RANKING, isoWeek(weekStartDate), messages);
    }

    /** 오늘이 거래일이고 직전 거래일이 지난주라면 이번 주 첫 영업일이다. 월요일이 휴장이면 화요일이 된다. */
    private boolean isFirstTradingDayOfWeek(LocalDate today, LocalDate thisMonday) {
        TradingCalendar calendar = tradingCalendarService.getCalendar(today);
        return calendar.tradingDay() && calendar.previousTradingDay().isBefore(thisMonday);
    }

    /** 중복 방지 키의 주 단위. ISO 주차(예: 2026-W42)다. */
    static String isoWeek(LocalDate date) {
        return String.format(
                "%d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }
}
