package com.muffin.stats.presentation.dto;

import java.math.BigDecimal;

/**
 * 누적 수익률 상위 섹터 한 건. 수익 통계 조회(STATS-02-1)와 수익 TOP3 섹터 조회(STATS-02-2)가 같은 형태로 내려주기 위해 공유한다.
 *
 * @param rank 1부터 시작하는 순위
 * @param profitAmount 그 섹터의 누적 손익금(손실 시 음수)
 * @param profitRate 그 섹터의 누적 매수금 대비 손익률(%), 소수 첫째자리
 */
public record TopSectorResponse(
        int rank, String sectorCode, String sectorName, long profitAmount, BigDecimal profitRate) {}
