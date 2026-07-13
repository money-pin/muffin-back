package com.muffin.investment.domain.profitsummary;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** ProfitSummary 애그리거트 리포지토리. */
public interface ProfitSummaryRepository extends JpaRepository<ProfitSummary, Long> {

    /** 재실행 upsert 판단용: 해당 유저-요약일자 요약 조회. */
    Optional<ProfitSummary> findByUserIdAndSummaryDate(Long userId, LocalDate summaryDate);

    /** 누적 손익 계산용: 요약일자 이전의 가장 최근 요약(직전 누적). */
    Optional<ProfitSummary> findTopByUserIdAndSummaryDateLessThanOrderBySummaryDateDesc(
            Long userId, LocalDate summaryDate);
}
