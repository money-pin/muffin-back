package com.muffin.ranking.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.muffin.character.domain.enums.MuffinType;
import com.muffin.ranking.application.projection.Top10RankingProjection;
import com.muffin.ranking.application.projection.WeeklyInvestmentProjection;
import com.muffin.ranking.application.projection.WeeklyRankingProjection;
import com.muffin.ranking.application.projection.WeeklySectorProjection;
import com.muffin.ranking.presentation.dto.WeeklyRankingResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyRankingQueryServiceTest {

    private static final LocalDate WEEK_START_DATE = LocalDate.of(2026, 7, 20);
    private static final LocalDate WEEK_END_DATE = LocalDate.of(2026, 7, 26);

    @Mock
    private WeeklyRankingQueryRepository weeklyRankingQueryRepository;

    @Test
    @DisplayName("스냅샷이 있으면 내 순위와 TOP 10 상세를 한 응답으로 조립한다")
    void getWeeklyRanking_returnsReadyResponse() {
        WeeklyRankingQueryService service = serviceAt("2026-07-27T10:00:00+09:00");
        when(weeklyRankingQueryRepository.existsSnapshot(WEEK_START_DATE)).thenReturn(true);
        when(weeklyRankingQueryRepository.findMyRank(99L, WEEK_START_DATE))
                .thenReturn(Optional.of(ranking(99L, "나", 11, 12_345L, 5, 12)));
        when(weeklyRankingQueryRepository.findTop10(WEEK_START_DATE))
                .thenReturn(List.of(
                        top10Ranking(1L, "1등", 1, 10_000L, 1L, MuffinType.PLAIN, "플레인 머핀", "plain.png"),
                        top10Ranking(2L, "2등", 2, 3_000L, null, null, null, null)));
        when(weeklyRankingQueryRepository.findWeeklyInvestments(List.of(1L, 2L), WEEK_START_DATE, WEEK_END_DATE))
                .thenReturn(List.of(
                        new WeeklyInvestmentProjection(1L, 100_000L), new WeeklyInvestmentProjection(2L, 60_000L)));
        when(weeklyRankingQueryRepository.findWeeklySectors(List.of(1L, 2L), WEEK_START_DATE, WEEK_END_DATE))
                .thenReturn(List.of(
                        new WeeklySectorProjection(1L, "GOLD", "금", 40_000L, 2_000L),
                        new WeeklySectorProjection(1L, "TECH", "테크", 60_000L, 8_000L),
                        new WeeklySectorProjection(2L, "BOND", "채권", 60_000L, 3_000L)));

        WeeklyRankingResponse response = service.getWeeklyRanking(99L);

        assertEquals("READY", response.rankingStatus().name());
        assertEquals("7월 3주차 기준", response.weekLabel());
        assertTrue(response.myRank().participated());
        assertEquals(11, response.myRank().rank());
        assertEquals(12, response.myRank().topPercent());
        assertEquals(2, response.top10().size());
        assertEquals(1, response.top10().getFirst().rank());
        assertEquals(MuffinType.PLAIN, response.top10().getFirst().character().characterType());
        assertEquals("plain.png", response.top10().getFirst().character().characterImageUrl());
        assertNull(response.top10().get(1).character());
        assertEquals(
                0, new BigDecimal("10.0").compareTo(response.top10().getFirst().profitRate()));
        assertEquals(
                List.of("TECH", "GOLD"),
                response.top10().getFirst().detail().sectors().stream()
                        .map(WeeklyRankingResponse.SectorResponse::sectorCode)
                        .toList());
        assertEquals(0, new BigDecimal("5.0").compareTo(response.top10().get(1).profitRate()));
    }

    @Test
    @DisplayName("스냅샷이 없고 정산 대상이 남아 있으면 집계 중 상태를 반환한다")
    void getWeeklyRanking_returnsCalculatingWhenTargetExists() {
        WeeklyRankingQueryService service = serviceAt("2026-07-27T09:40:00+09:00");
        when(weeklyRankingQueryRepository.existsSnapshot(WEEK_START_DATE)).thenReturn(false);
        when(weeklyRankingQueryRepository.hasCalculatingTarget(WEEK_START_DATE, WEEK_END_DATE))
                .thenReturn(true);

        WeeklyRankingResponse response = service.getWeeklyRanking(1L);

        assertEquals("CALCULATING", response.rankingStatus().name());
        assertNull(response.myRank());
        assertTrue(response.top10().isEmpty());
        verifyNoMoreInteractionsAfterCalculationCheck();
    }

    @Test
    @DisplayName("스냅샷과 참여 가능한 투자 모두 없으면 미참여 빈 상태를 반환한다")
    void getWeeklyRanking_returnsEmptyWhenNoTargetExists() {
        WeeklyRankingQueryService service = serviceAt("2026-07-27T09:40:00+09:00");
        when(weeklyRankingQueryRepository.existsSnapshot(WEEK_START_DATE)).thenReturn(false);
        when(weeklyRankingQueryRepository.hasCalculatingTarget(WEEK_START_DATE, WEEK_END_DATE))
                .thenReturn(false);

        WeeklyRankingResponse response = service.getWeeklyRanking(1L);

        assertEquals("EMPTY", response.rankingStatus().name());
        assertFalse(response.myRank().participated());
        assertTrue(response.top10().isEmpty());
        verifyNoMoreInteractionsAfterCalculationCheck();
    }

    private WeeklyRankingQueryService serviceAt(String timestamp) {
        Clock clock = Clock.fixed(OffsetDateTime.parse(timestamp).toInstant(), ZoneId.of("Asia/Seoul"));
        return new WeeklyRankingQueryService(weeklyRankingQueryRepository, clock);
    }

    private WeeklyRankingProjection ranking(
            Long userId, String nickname, int rank, Long profit, int ignoredRate, Integer percentile) {
        return new WeeklyRankingProjection(userId, nickname, rank, profit, BigDecimal.valueOf(ignoredRate), percentile);
    }

    private Top10RankingProjection top10Ranking(
            Long userId,
            String nickname,
            int rank,
            Long profit,
            Long characterId,
            MuffinType characterType,
            String characterName,
            String characterImageUrl) {
        return new Top10RankingProjection(
                userId,
                nickname,
                rank,
                profit,
                BigDecimal.ZERO,
                rank,
                characterId,
                characterType,
                characterName,
                characterImageUrl);
    }

    private void verifyNoMoreInteractionsAfterCalculationCheck() {
        verify(weeklyRankingQueryRepository).existsSnapshot(WEEK_START_DATE);
        verify(weeklyRankingQueryRepository).hasCalculatingTarget(WEEK_START_DATE, WEEK_END_DATE);
        verifyNoMoreInteractions(weeklyRankingQueryRepository);
    }
}
