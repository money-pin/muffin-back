package com.muffin.ranking.application;

import com.muffin.ranking.domain.weeklyranking.WeeklyRankingCandidate;
import java.time.LocalDate;
import java.util.List;

/** 주간 랭킹 배치에 필요한 투자 집계 전용 조회 포트다. */
public interface WeeklyRankingAggregationRepository {

    boolean existsByWeekStartDate(LocalDate weekStartDate);

    boolean hasUnsettledConfirmedInvestment(LocalDate weekStartDate, LocalDate weekEndDate);

    List<WeeklyRankingCandidate> findSettledCandidates(LocalDate weekStartDate, LocalDate weekEndDate);
}
