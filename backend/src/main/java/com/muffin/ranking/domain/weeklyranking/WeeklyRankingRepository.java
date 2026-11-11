package com.muffin.ranking.domain.weeklyranking;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** WeeklyRanking 애그리거트 리포지토리. */
public interface WeeklyRankingRepository extends JpaRepository<WeeklyRanking, Long> {

    List<WeeklyRanking> findAllByWeekStartDate(LocalDate weekStartDate);

    /** 랭킹 알림의 "첫 랭킹" 판정용: 지정 주보다 앞선 랭킹 기록이 있는 사용자 ID. */
    @Query(
            "select distinct r.userId from WeeklyRanking r where r.weekStartDate < :weekStartDate and r.userId in :userIds")
    List<Long> findUserIdsRankedBefore(
            @Param("weekStartDate") LocalDate weekStartDate, @Param("userIds") Collection<Long> userIds);
}
