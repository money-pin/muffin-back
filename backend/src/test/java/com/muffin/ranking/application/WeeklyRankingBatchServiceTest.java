package com.muffin.ranking.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.muffin.ranking.domain.weeklyranking.WeeklyRanking;
import com.muffin.ranking.domain.weeklyranking.WeeklyRankingCandidate;
import com.muffin.ranking.domain.weeklyranking.WeeklyRankingRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyRankingBatchServiceTest {

    @Mock
    private WeeklyRankingAggregationRepository aggregationRepository;

    @Mock
    private WeeklyRankingRepository weeklyRankingRepository;

    @Captor
    private ArgumentCaptor<List<WeeklyRanking>> rankingsCaptor;

    @Test
    @DisplayName("화요일 재시도도 직전 월요일부터 일요일까지를 같은 지난주로 집계한다")
    void createPreviousWeekRanking_createsPreviousCalendarWeek() {
        WeeklyRankingBatchService service =
                new WeeklyRankingBatchService(aggregationRepository, weeklyRankingRepository);
        LocalDate weekStartDate = LocalDate.of(2026, 7, 6);
        LocalDate weekEndDate = LocalDate.of(2026, 7, 12);
        when(aggregationRepository.existsByWeekStartDate(weekStartDate)).thenReturn(false);
        when(aggregationRepository.hasUnsettledConfirmedInvestment(weekStartDate, weekEndDate))
                .thenReturn(false);
        when(aggregationRepository.findSettledCandidates(weekStartDate, weekEndDate))
                .thenReturn(List.of(new WeeklyRankingCandidate(1L, "muffin", "abcd-1234", 100_000L, 5_000L)));

        service.createPreviousWeekRanking(LocalDate.of(2026, 7, 14));

        verify(weeklyRankingRepository).saveAll(rankingsCaptor.capture());
        WeeklyRanking ranking = rankingsCaptor.getValue().getFirst();
        org.junit.jupiter.api.Assertions.assertEquals(weekStartDate, ranking.getWeekStartDate());
        org.junit.jupiter.api.Assertions.assertEquals(1, ranking.getRankingPosition());
    }

    @Test
    @DisplayName("이미 생성된 주간 스냅샷은 다시 집계하지 않는다")
    void createPreviousWeekRanking_skipsWhenSnapshotExists() {
        WeeklyRankingBatchService service =
                new WeeklyRankingBatchService(aggregationRepository, weeklyRankingRepository);
        LocalDate weekStartDate = LocalDate.of(2026, 7, 6);
        when(aggregationRepository.existsByWeekStartDate(weekStartDate)).thenReturn(true);

        service.createPreviousWeekRanking(LocalDate.of(2026, 7, 13));

        verify(aggregationRepository).existsByWeekStartDate(weekStartDate);
        verifyNoMoreInteractions(aggregationRepository, weeklyRankingRepository);
    }

    @Test
    @DisplayName("지난주에 미정산 확정 투자가 있으면 스냅샷 생성을 다음 실행으로 보류한다")
    void createPreviousWeekRanking_defersWhenSettlementIsPending() {
        WeeklyRankingBatchService service =
                new WeeklyRankingBatchService(aggregationRepository, weeklyRankingRepository);
        LocalDate weekStartDate = LocalDate.of(2026, 7, 6);
        LocalDate weekEndDate = LocalDate.of(2026, 7, 12);
        when(aggregationRepository.existsByWeekStartDate(weekStartDate)).thenReturn(false);
        when(aggregationRepository.hasUnsettledConfirmedInvestment(weekStartDate, weekEndDate))
                .thenReturn(true);

        service.createPreviousWeekRanking(LocalDate.of(2026, 7, 13));

        verify(aggregationRepository).hasUnsettledConfirmedInvestment(weekStartDate, weekEndDate);
        verify(weeklyRankingRepository, never()).saveAll(any());
    }
}
