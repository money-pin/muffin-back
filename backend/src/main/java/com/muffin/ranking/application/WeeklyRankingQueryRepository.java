package com.muffin.ranking.application;

import com.muffin.ranking.application.projection.WeeklyInvestmentProjection;
import com.muffin.ranking.application.projection.WeeklyRankingProjection;
import com.muffin.ranking.application.projection.WeeklySectorProjection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** 주간 랭킹 화면을 한 번에 조립하기 위한 읽기 전용 조회 포트다. */
public interface WeeklyRankingQueryRepository {

    boolean existsSnapshot(LocalDate weekStartDate);

    boolean hasCalculatingTarget(LocalDate weekStartDate, LocalDate weekEndDate);

    Optional<WeeklyRankingProjection> findMyRank(Long userId, LocalDate weekStartDate);

    List<WeeklyRankingProjection> findTop10(LocalDate weekStartDate);

    List<WeeklyInvestmentProjection> findWeeklyInvestments(
            List<Long> userIds, LocalDate weekStartDate, LocalDate weekEndDate);

    List<WeeklySectorProjection> findWeeklySectors(List<Long> userIds, LocalDate weekStartDate, LocalDate weekEndDate);
}
