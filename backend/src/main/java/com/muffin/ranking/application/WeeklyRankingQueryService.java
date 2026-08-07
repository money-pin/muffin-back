package com.muffin.ranking.application;

import com.muffin.ranking.application.projection.Top10RankingProjection;
import com.muffin.ranking.application.projection.WeeklyInvestmentProjection;
import com.muffin.ranking.application.projection.WeeklyRankingProjection;
import com.muffin.ranking.application.projection.WeeklySectorProjection;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse.CharacterResponse;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse.MyRankResponse;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse.SectorResponse;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse.Top10DetailResponse;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse.Top10Response;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지난주 랭킹 스냅샷과 TOP 10의 투자 상세를 화면 응답으로 조립한다. */
@Service
@RequiredArgsConstructor
public class WeeklyRankingQueryService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final WeeklyRankingQueryRepository weeklyRankingQueryRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public WeeklyRankingResponse getWeeklyRanking(Long userId) {
        LocalDate weekStartDate = previousWeekStart(LocalDate.now(clock));
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        WeeklyRankingResponse.WeekInfo weekInfo = weekInfo(weekStartDate, weekEndDate);

        if (!weeklyRankingQueryRepository.existsSnapshot(weekStartDate)) {
            return weeklyRankingQueryRepository.hasCalculatingTarget(weekStartDate, weekEndDate)
                    ? WeeklyRankingResponse.calculating(weekInfo)
                    : WeeklyRankingResponse.empty(weekInfo);
        }

        return readyResponse(userId, weekStartDate, weekEndDate, weekInfo);
    }

    private WeeklyRankingResponse readyResponse(
            Long userId, LocalDate weekStartDate, LocalDate weekEndDate, WeeklyRankingResponse.WeekInfo weekInfo) {
        WeeklyRankingResponse.MyRankResponse myRank = weeklyRankingQueryRepository
                .findMyRank(userId, weekStartDate)
                .map(this::toMyRank)
                .orElseGet(WeeklyRankingResponse.MyRankResponse::notParticipated);

        List<Top10RankingProjection> top10 = weeklyRankingQueryRepository.findTop10(weekStartDate);
        if (top10.isEmpty()) {
            return WeeklyRankingResponse.ready(weekInfo, myRank, List.of());
        }

        List<Long> top10UserIds =
                top10.stream().map(Top10RankingProjection::userId).toList();
        Map<Long, Long> investmentByUserId =
                weeklyRankingQueryRepository.findWeeklyInvestments(top10UserIds, weekStartDate, weekEndDate).stream()
                        .collect(Collectors.toMap(
                                WeeklyInvestmentProjection::userId,
                                projection -> valueOrZero(projection.totalInvestment())));
        Map<Long, List<WeeklySectorProjection>> sectorsByUserId =
                weeklyRankingQueryRepository.findWeeklySectors(top10UserIds, weekStartDate, weekEndDate).stream()
                        .collect(Collectors.groupingBy(WeeklySectorProjection::userId));

        List<Top10Response> top10Responses = top10.stream()
                .map(ranking -> toTop10Response(
                        ranking,
                        investmentByUserId.getOrDefault(ranking.userId(), 0L),
                        sectorsByUserId.getOrDefault(ranking.userId(), List.of())))
                .toList();
        return WeeklyRankingResponse.ready(weekInfo, myRank, top10Responses);
    }

    private MyRankResponse toMyRank(WeeklyRankingProjection ranking) {
        return MyRankResponse.participated(ranking.rankingPosition(), ranking.nicknameSnapshot(), ranking.percentile());
    }

    private Top10Response toTop10Response(
            Top10RankingProjection ranking, long totalInvestment, List<WeeklySectorProjection> sectors) {
        List<SectorResponse> sectorResponses = sectors.stream()
                .map(this::toSectorResponse)
                .sorted(Comparator.comparing(SectorResponse::profitAmount, Comparator.reverseOrder())
                        .thenComparing(SectorResponse::totalInvestment, Comparator.reverseOrder())
                        .thenComparing(SectorResponse::sectorCode))
                .toList();
        long profitAmount = valueOrZero(ranking.weeklyProfit());
        return new Top10Response(
                ranking.rankingPosition(),
                ranking.nicknameSnapshot(),
                toCharacterResponse(ranking),
                profitAmount,
                rate(profitAmount, totalInvestment),
                new Top10DetailResponse(totalInvestment, sectorResponses));
    }

    private CharacterResponse toCharacterResponse(Top10RankingProjection ranking) {
        if (ranking.characterId() == null) {
            return null;
        }
        return new CharacterResponse(
                ranking.characterId(), ranking.characterType(), ranking.characterName(), ranking.characterImageUrl());
    }

    private SectorResponse toSectorResponse(WeeklySectorProjection sector) {
        long totalInvestment = valueOrZero(sector.totalInvestment());
        long profitAmount = valueOrZero(sector.profitAmount());
        return new SectorResponse(
                sector.sectorCode(),
                sector.sectorName(),
                profitAmount,
                rate(profitAmount, totalInvestment),
                totalInvestment);
    }

    private static WeeklyRankingResponse.WeekInfo weekInfo(LocalDate weekStartDate, LocalDate weekEndDate) {
        int weekOfYear = weekStartDate.get(WeekFields.ISO.weekOfWeekBasedYear());
        int weekOfMonth = ((weekStartDate.getDayOfMonth() - 1) / 7) + 1;
        String weekLabel = "%d월 %d주차 기준".formatted(weekStartDate.getMonthValue(), weekOfMonth);
        return new WeeklyRankingResponse.WeekInfo(weekStartDate, weekEndDate, weekOfYear, weekLabel);
    }

    private static LocalDate previousWeekStart(LocalDate referenceDate) {
        return referenceDate
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);
    }

    private static BigDecimal rate(long profitAmount, long totalInvestment) {
        if (totalInvestment <= 0L) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(profitAmount)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(totalInvestment), 1, RoundingMode.HALF_UP);
    }

    private static long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
