package com.muffin.ranking.domain.weeklyranking;

import org.springframework.data.jpa.repository.JpaRepository;

/** WeeklyRanking 애그리거트 리포지토리. */
public interface WeeklyRankingRepository extends JpaRepository<WeeklyRanking, Long> {}
