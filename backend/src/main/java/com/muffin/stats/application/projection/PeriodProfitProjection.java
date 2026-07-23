package com.muffin.stats.application.projection;

/**
 * 특정 기간 윈도우의 정산 완료 투자 합계. 데이터가 없으면 SUM 결과가 null이라 서비스에서 0으로 보정한다.
 *
 * @param totalInvestment 기간 내 누적 매수금액 합
 * @param totalProfitLoss 기간 내 누적 손익금 합
 */
public record PeriodProfitProjection(Long totalInvestment, Long totalProfitLoss) {}
