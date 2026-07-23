package com.muffin.mypage.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 일일 퀴즈 완료일 집합으로부터 연속 참여(스트릭)를 계산하는 순수 로직.
 *
 * <p>currentStreak은 "오늘"이 아직 완료되지 않았어도 끊기지 않는다(오늘 하루가 아직 끝나지 않았으므로). 오늘도, 어제도 완료하지
 * 않았을 때만 0이 된다. maxStreak은 전체 완료일 집합에서 가장 긴 연속 구간이다.
 */
public final class StreakCalculator {

    private StreakCalculator() {}

    public record DayResult(LocalDate date, boolean participated) {}

    public record Result(int currentStreak, int maxStreak, List<DayResult> weeklyActivity) {}

    public static Result calculate(Set<LocalDate> finishedDates, LocalDate today) {
        int currentStreak = calculateCurrentStreak(finishedDates, today);
        int maxStreak = calculateMaxStreak(finishedDates);
        List<DayResult> weeklyActivity = calculateWeeklyActivity(finishedDates, today);
        return new Result(currentStreak, maxStreak, weeklyActivity);
    }

    private static int calculateCurrentStreak(Set<LocalDate> finishedDates, LocalDate today) {
        LocalDate cursor = finishedDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (finishedDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private static int calculateMaxStreak(Set<LocalDate> finishedDates) {
        int max = 0;
        for (LocalDate date : finishedDates) {
            if (finishedDates.contains(date.minusDays(1))) {
                continue;
            }
            int length = 1;
            LocalDate cursor = date;
            while (finishedDates.contains(cursor.plusDays(1))) {
                length++;
                cursor = cursor.plusDays(1);
            }
            max = Math.max(max, length);
        }
        return max;
    }

    private static List<DayResult> calculateWeeklyActivity(Set<LocalDate> finishedDates, LocalDate today) {
        LocalDate sunday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        List<DayResult> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = sunday.plusDays(i);
            days.add(new DayResult(date, finishedDates.contains(date)));
        }
        return days;
    }
}
