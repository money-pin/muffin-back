package com.muffin.ranking.domain.weeklyranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeeklyRankingTest {

    private static final LocalDate WEEK_START_DATE = LocalDate.of(2026, 6, 29);

    @Test
    @DisplayName("생성하면 전달한 랭킹 필드가 그대로 채워진다")
    void create_fillsRankingFields() {
        WeeklyRanking ranking = createRanking(3, 10, BigDecimal.valueOf(4.5));

        assertEquals(1L, ranking.getUserId());
        assertEquals("muffin", ranking.getNicknameSnapshot());
        assertEquals(3, ranking.getRankingPosition());
        assertEquals(120_000L, ranking.getWeeklyProfit());
        assertEquals(0, BigDecimal.valueOf(4.5).compareTo(ranking.getWeeklyProfitRate()));
        assertEquals(10, ranking.getPercentile());
        assertEquals(WEEK_START_DATE, ranking.getWeekStartDate());
        assertEquals(27, ranking.getWeekOfYear());
    }

    @Test
    @DisplayName("주간 손익이 음수여도 생성할 수 있다")
    void create_allowsNegativeWeeklyProfit() {
        WeeklyRanking ranking =
                WeeklyRanking.create(1L, "muffin", 50, -30_000L, BigDecimal.valueOf(-2.5), 90, WEEK_START_DATE, 27);

        assertEquals(-30_000L, ranking.getWeeklyProfit());
    }

    @Test
    @DisplayName("순위가 0 이하이면 예외가 발생한다")
    void create_throwsWhenRankingPositionNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> createRanking(0, 10, BigDecimal.valueOf(4.5)));
    }

    @Test
    @DisplayName("상위 백분율이 0~100 범위를 벗어나면 예외가 발생한다")
    void create_throwsWhenPercentileOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> createRanking(3, 101, BigDecimal.valueOf(4.5)));
        assertThrows(IllegalArgumentException.class, () -> createRanking(3, -1, BigDecimal.valueOf(4.5)));
    }

    @Test
    @DisplayName("닉네임 스냅샷이 빈 값이면 예외가 발생한다")
    void create_throwsWhenNicknameBlank() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WeeklyRanking.create(1L, "  ", 3, 120_000L, BigDecimal.valueOf(4.5), 10, WEEK_START_DATE, 27));
    }

    @Test
    @DisplayName("ISO 주차 범위(1~53)를 벗어나면 예외가 발생한다")
    void create_throwsWhenWeekOfYearOutOfRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WeeklyRanking.create(1L, "muffin", 3, 120_000L, BigDecimal.valueOf(4.5), 10, WEEK_START_DATE, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> WeeklyRanking.create(
                        1L, "muffin", 3, 120_000L, BigDecimal.valueOf(4.5), 10, WEEK_START_DATE, 54));
    }

    private WeeklyRanking createRanking(int rankingPosition, int percentile, BigDecimal weeklyProfitRate) {
        return WeeklyRanking.create(
                1L, "muffin", rankingPosition, 120_000L, weeklyProfitRate, percentile, WEEK_START_DATE, 27);
    }
}
