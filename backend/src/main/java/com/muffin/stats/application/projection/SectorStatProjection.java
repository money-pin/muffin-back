package com.muffin.stats.application.projection;

/**
 * 정산 완료된 투자의 섹터별 누적 집계 결과. TOP3 섹터와 투자 성향 계산에 함께 쓰인다.
 *
 * @param sectorCode 섹터 코드
 * @param sectorName 섹터 표시명
 * @param groupCode sector_group.group_code(성향 그룹 매핑용)
 * @param totalAmount 섹터 누적 매수금액
 * @param totalProfitLoss 섹터 누적 손익금
 */
public record SectorStatProjection(
        String sectorCode, String sectorName, String groupCode, Long totalAmount, Long totalProfitLoss) {}
