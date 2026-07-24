package com.muffin.mypage.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StreakCalculatorTest {

    private static final LocalDate WED = LocalDate.of(2026, 7, 22); // 2026-07-22는 수요일

    @Test
    @DisplayName("완료일이 없으면 currentStreak/maxStreak 모두 0, weeklyActivity 전부 false")
    void empty_allZero() {
        StreakCalculator.Result result = StreakCalculator.calculate(Set.of(), WED);

        assertThat(result.currentStreak()).isZero();
        assertThat(result.maxStreak()).isZero();
        assertThat(result.weeklyActivity()).hasSize(7);
        assertThat(result.weeklyActivity()).allMatch(day -> !day.participated());
    }

    @Test
    @DisplayName("오늘 포함 연속 3일 완료 → currentStreak=3")
    void currentStreak_includesToday() {
        Set<LocalDate> finished = Set.of(WED, WED.minusDays(1), WED.minusDays(2));

        StreakCalculator.Result result = StreakCalculator.calculate(finished, WED);

        assertThat(result.currentStreak()).isEqualTo(3);
    }

    @Test
    @DisplayName("오늘은 미완료지만 어제까지 연속 3일 → currentStreak 유지(오늘 미완료가 끊지 않음)")
    void currentStreak_todayNotDoneYet_doesNotBreakStreak() {
        Set<LocalDate> finished = Set.of(WED.minusDays(1), WED.minusDays(2), WED.minusDays(3));

        StreakCalculator.Result result = StreakCalculator.calculate(finished, WED);

        assertThat(result.currentStreak()).isEqualTo(3);
    }

    @Test
    @DisplayName("오늘도 어제도 미완료 → currentStreak=0")
    void currentStreak_todayAndYesterdayMissing_zero() {
        Set<LocalDate> finished = Set.of(WED.minusDays(3), WED.minusDays(4));

        StreakCalculator.Result result = StreakCalculator.calculate(finished, WED);

        assertThat(result.currentStreak()).isZero();
    }

    @Test
    @DisplayName("과거의 더 긴 연속 구간이 현재 스트릭보다 길면 maxStreak은 과거 기록을 반영한다")
    void maxStreak_reflectsPastLongestRun() {
        // 과거: 5일 연속(D-10~D-6), 현재: 오늘 포함 2일 연속
        Set<LocalDate> finished = Set.of(
                WED.minusDays(10),
                WED.minusDays(9),
                WED.minusDays(8),
                WED.minusDays(7),
                WED.minusDays(6),
                WED,
                WED.minusDays(1));

        StreakCalculator.Result result = StreakCalculator.calculate(finished, WED);

        assertThat(result.currentStreak()).isEqualTo(2);
        assertThat(result.maxStreak()).isEqualTo(5);
    }

    @Test
    @DisplayName("weeklyActivity는 일요일부터 토요일까지 7일이며 참여 여부를 정확히 반영한다")
    void weeklyActivity_sundayToSaturday() {
        // WED(2026-07-22, 수)가 속한 주의 일요일은 2026-07-19
        LocalDate sunday = LocalDate.of(2026, 7, 19);
        Set<LocalDate> finished = Set.of(sunday.plusDays(1), sunday.plusDays(3)); // 월, 수

        StreakCalculator.Result result = StreakCalculator.calculate(finished, WED);

        assertThat(result.weeklyActivity()).hasSize(7);
        assertThat(result.weeklyActivity().get(0).date()).isEqualTo(sunday);
        assertThat(result.weeklyActivity().get(6).date()).isEqualTo(sunday.plusDays(6));
        assertThat(result.weeklyActivity().get(1).participated()).isTrue(); // 월
        assertThat(result.weeklyActivity().get(3).participated()).isTrue(); // 수
        assertThat(result.weeklyActivity().get(0).participated()).isFalse(); // 일
        assertThat(result.weeklyActivity().get(2).participated()).isFalse(); // 화
    }

    @Test
    @DisplayName("today가 일요일이면 weeklyActivity의 첫날은 today 자신이다")
    void weeklyActivity_todayIsSunday() {
        LocalDate sunday = LocalDate.of(2026, 7, 19);

        StreakCalculator.Result result = StreakCalculator.calculate(Set.of(), sunday);

        assertThat(result.weeklyActivity().get(0).date()).isEqualTo(sunday);
    }
}
