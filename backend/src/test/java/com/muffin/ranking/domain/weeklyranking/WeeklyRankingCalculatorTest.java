package com.muffin.ranking.domain.weeklyranking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeeklyRankingCalculatorTest {

    private static final LocalDate WEEK_START_DATE = LocalDate.of(2026, 7, 6);

    @Test
    @DisplayName("수익률, 수익금, 사용자 ID 순으로 정렬하고 백분위를 올림 계산한다")
    void calculate_ordersByRateThenProfitThenUserId() {
        List<WeeklyRanking> rankings = WeeklyRankingCalculator.calculate(
                List.of(
                        candidate(3L, "third", 100_000L, 10_000L),
                        candidate(2L, "second", 200_000L, 20_000L),
                        candidate(1L, "first", 100_000L, 10_000L)),
                WEEK_START_DATE);

        assertEquals(
                List.of(2L, 1L, 3L),
                rankings.stream().map(WeeklyRanking::getUserId).toList());
        assertEquals(
                List.of(1, 2, 3),
                rankings.stream().map(WeeklyRanking::getRankingPosition).toList());
        assertEquals(
                List.of(34, 67, 100),
                rankings.stream().map(WeeklyRanking::getPercentile).toList());
        assertEquals(0, BigDecimal.valueOf(10.00).compareTo(rankings.get(0).getWeeklyProfitRate()));
    }

    @Test
    @DisplayName("닉네임이 없으면 UUID 앞 네 글자로 스냅샷 닉네임을 만든다")
    void calculate_usesUuidPrefixWhenNicknameMissing() {
        List<WeeklyRanking> rankings = WeeklyRankingCalculator.calculate(
                List.of(new WeeklyRankingCandidate(1L, null, "abcd-1234-5678", 100_000L, -1_250L)), WEEK_START_DATE);

        WeeklyRanking ranking = rankings.getFirst();
        assertEquals("사용자#ABCD", ranking.getNicknameSnapshot());
        assertEquals(0, BigDecimal.valueOf(-1.25).compareTo(ranking.getWeeklyProfitRate()));
        assertEquals(100, ranking.getPercentile());
    }

    @Test
    @DisplayName("총 투자금이 0인 후보는 랭킹에서 제외한다")
    void calculate_excludesZeroInvestmentCandidate() {
        List<WeeklyRanking> rankings = WeeklyRankingCalculator.calculate(
                List.of(candidate(1L, "eligible", 100_000L, 5_000L), candidate(2L, "zero", 0L, 0L)), WEEK_START_DATE);

        assertEquals(1, rankings.size());
        assertEquals(1L, rankings.getFirst().getUserId());
    }

    private static WeeklyRankingCandidate candidate(
            Long userId, String nickname, Long totalInvestment, Long weeklyProfit) {
        return new WeeklyRankingCandidate(userId, nickname, "1234-5678-9012", totalInvestment, weeklyProfit);
    }
}
