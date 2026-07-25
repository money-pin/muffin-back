package com.muffin.stats.application.projection;

import java.time.LocalDate;

/**
 * 가장 최근 정산 완료(SETTLED) 투자 1건의 헤더 정보.
 *
 * @param investmentId 투자 식별자(섹터 상세 조회 키)
 * @param investDate 투자 일자
 * @param totalInvestment 총 매수금액
 * @param totalProfitLoss 총 손익금
 */
public record RecentInvestmentProjection(
        Long investmentId, LocalDate investDate, Long totalInvestment, Long totalProfitLoss) {}
