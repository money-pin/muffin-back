package com.muffin.investment.domain.profitsummary;

import org.springframework.data.jpa.repository.JpaRepository;

/** ProfitSummary 애그리거트 리포지토리. */
public interface ProfitSummaryRepository extends JpaRepository<ProfitSummary, Long> {}
