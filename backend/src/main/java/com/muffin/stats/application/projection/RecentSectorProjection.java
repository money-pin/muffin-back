package com.muffin.stats.application.projection;

import com.muffin.investment.domain.investment.enums.PriceDataSource;

/**
 * 최근 투자의 섹터별 상세 스냅샷.
 *
 * @param sectorCode 섹터 코드
 * @param sectorName 섹터 표시명
 * @param totalInvestment 섹터 매수금액
 * @param totalProfitLoss 섹터 손익금
 * @param priceDataSource 정산 시 가격 출처(FALLBACK_ZERO면 거래정지 등 0% 적용)
 */
public record RecentSectorProjection(
        String sectorCode,
        String sectorName,
        Long totalInvestment,
        Long totalProfitLoss,
        PriceDataSource priceDataSource) {}
