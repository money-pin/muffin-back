package com.muffin.stats.application.projection;

/**
 * 특정 기간 윈도우의 섹터별 정산 완료 집계.
 *
 * @param sectorCode 섹터 코드
 * @param sectorName 섹터 표시명
 * @param totalInvestment 섹터 누적 매수금액
 * @param totalProfitLoss 섹터 누적 손익금
 */
public record SectorHistoryProjection(
        String sectorCode, String sectorName, Long totalInvestment, Long totalProfitLoss) {}
