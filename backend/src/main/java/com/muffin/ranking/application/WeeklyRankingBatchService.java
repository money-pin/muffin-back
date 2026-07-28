package com.muffin.ranking.application;

import com.muffin.ranking.domain.weeklyranking.WeeklyRanking;
import com.muffin.ranking.domain.weeklyranking.WeeklyRankingCalculator;
import com.muffin.ranking.domain.weeklyranking.WeeklyRankingCandidate;
import com.muffin.ranking.domain.weeklyranking.WeeklyRankingRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지난달이 아닌, 직전 월요일~일요일의 확정 수익률을 주간 스냅샷으로 저장한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyRankingBatchService {

    private final WeeklyRankingAggregationRepository aggregationRepository;
    private final WeeklyRankingRepository weeklyRankingRepository;

    /**
     * 기준일이 속한 주의 직전 주를 집계한다. 월요일 휴장 시에도 화요일 실행이 같은 대상 주를 계산한다.
     */
    @Transactional
    public void createPreviousWeekRanking(LocalDate referenceDate) {
        LocalDate weekStartDate = previousWeekStart(referenceDate);
        LocalDate weekEndDate = weekStartDate.plusDays(6);

        if (aggregationRepository.existsByWeekStartDate(weekStartDate)) {
            log.info("[weekly-ranking] already created weekStartDate={}", weekStartDate);
            return;
        }
        if (aggregationRepository.hasUnsettledConfirmedInvestment(weekStartDate, weekEndDate)) {
            log.info("[weekly-ranking] settlement pending, defer weekStartDate={}", weekStartDate);
            return;
        }

        List<WeeklyRankingCandidate> candidates =
                aggregationRepository.findSettledCandidates(weekStartDate, weekEndDate);
        List<WeeklyRanking> rankings = WeeklyRankingCalculator.calculate(candidates, weekStartDate);
        if (rankings.isEmpty()) {
            log.info("[weekly-ranking] no participants weekStartDate={}", weekStartDate);
            return;
        }

        weeklyRankingRepository.saveAll(rankings);
        log.info("[weekly-ranking] created weekStartDate={} participantCount={}", weekStartDate, rankings.size());
    }

    private static LocalDate previousWeekStart(LocalDate referenceDate) {
        return referenceDate
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(1);
    }
}
