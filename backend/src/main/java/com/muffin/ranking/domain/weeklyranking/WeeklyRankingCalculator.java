package com.muffin.ranking.domain.weeklyranking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** 지난주 사용자별 투자 집계값을 주간 랭킹 스냅샷으로 변환한다. */
public final class WeeklyRankingCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int RATE_COMPARISON_SCALE = 8;
    private static final int RATE_STORAGE_SCALE = 2;

    private WeeklyRankingCalculator() {}

    public static List<WeeklyRanking> calculate(List<WeeklyRankingCandidate> candidates, LocalDate weekStartDate) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(weekStartDate, "weekStartDate must not be null");

        List<CalculatedCandidate> ranked = candidates.stream()
                .filter(candidate -> candidate.totalInvestment() != null && candidate.totalInvestment() > 0L)
                .map(WeeklyRankingCalculator::calculateRate)
                .sorted(Comparator.comparing(CalculatedCandidate::rateForComparison, Comparator.reverseOrder())
                        .thenComparing(CalculatedCandidate::weeklyProfit, Comparator.reverseOrder())
                        .thenComparing(CalculatedCandidate::userId))
                .toList();

        int participantCount = ranked.size();
        int weekOfYear = weekStartDate.get(WeekFields.ISO.weekOfWeekBasedYear());
        return java.util.stream.IntStream.range(0, participantCount)
                .mapToObj(index ->
                        toWeeklyRanking(ranked.get(index), index + 1, participantCount, weekStartDate, weekOfYear))
                .toList();
    }

    private static CalculatedCandidate calculateRate(WeeklyRankingCandidate candidate) {
        long weeklyProfit = Objects.requireNonNull(candidate.weeklyProfit(), "weeklyProfit must not be null");
        BigDecimal rate = BigDecimal.valueOf(weeklyProfit)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(candidate.totalInvestment()), RATE_COMPARISON_SCALE, RoundingMode.HALF_UP);
        return new CalculatedCandidate(candidate.userId(), snapshotNickname(candidate), weeklyProfit, rate);
    }

    private static WeeklyRanking toWeeklyRanking(
            CalculatedCandidate candidate,
            int rankingPosition,
            int participantCount,
            LocalDate weekStartDate,
            int weekOfYear) {
        return WeeklyRanking.create(
                candidate.userId(),
                candidate.nicknameSnapshot(),
                rankingPosition,
                candidate.weeklyProfit(),
                candidate.rateForComparison().setScale(RATE_STORAGE_SCALE, RoundingMode.HALF_UP),
                calculatePercentile(rankingPosition, participantCount),
                weekStartDate,
                weekOfYear);
    }

    private static int calculatePercentile(int rankingPosition, int participantCount) {
        return BigDecimal.valueOf(rankingPosition)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(participantCount), 0, RoundingMode.CEILING)
                .intValueExact();
    }

    private static String snapshotNickname(WeeklyRankingCandidate candidate) {
        if (candidate.nickname() != null && !candidate.nickname().isBlank()) {
            return candidate.nickname();
        }

        String userUuid = Objects.requireNonNull(candidate.userUuid(), "userUuid must not be null");
        if (userUuid.length() < 4) {
            throw new IllegalArgumentException("userUuid must contain at least four characters");
        }
        return "사용자#" + userUuid.substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private record CalculatedCandidate(
            Long userId, String nicknameSnapshot, long weeklyProfit, BigDecimal rateForComparison) {}
}
