package com.muffin.stats.application;

import com.muffin.stats.application.projection.DailyProfitProjection;
import com.muffin.stats.application.projection.SectorStatProjection;
import java.util.List;

/**
 * 수익 통계 조회 읽기 전용 인터페이스. 정산 완료(SETTLED)된 투자만 대상으로 집계한다.
 */
public interface StatsSummaryQueryRepository {

    /** 정산 완료된 일자별 손익을 투자일자 오름차순으로 조회한다(누적 수익/그래프 소스). */
    List<DailyProfitProjection> findSettledDailyProfits(Long userId);

    /** 정산 완료된 투자의 섹터별 누적 매수금/손익을 조회한다(TOP3/성향 소스). */
    List<SectorStatProjection> findSettledSectorStats(Long userId);
}
