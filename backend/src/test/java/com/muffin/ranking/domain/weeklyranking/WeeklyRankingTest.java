package com.muffin.ranking.domain.weeklyranking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeeklyRankingTest {

    @Test
    @DisplayName("생성하면 전달한 랭킹 필드가 그대로 채워진다")
    void create_fillsRankingFields() {
        WeeklyRanking ranking = WeeklyRanking.create(1L, "muffin", 3, 120_000L, BigDecimal.valueOf(4.5), 10, 27);

        assertEquals(1L, ranking.getUserId());
        assertEquals("muffin", ranking.getNicknameSnapshot());
        assertEquals(3, ranking.getRank());
        assertEquals(120_000L, ranking.getWeeklyProfit());
        assertEquals(0, BigDecimal.valueOf(4.5).compareTo(ranking.getWeeklyProfitRate()));
        assertEquals(10, ranking.getPercentile());
        assertEquals(27, ranking.getWeekOfYear());
    }
}
