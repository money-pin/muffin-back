package com.muffin.stats.application;

import com.muffin.stats.application.projection.DailyProfitProjection;
import com.muffin.stats.application.projection.PeriodProfitProjection;
import com.muffin.stats.application.projection.SectorHistoryProjection;
import com.muffin.stats.application.projection.SectorStatProjection;
import java.time.LocalDate;
import java.util.List;

/**
 * 통계 조회 읽기 전용 인터페이스.
 */
public interface StatsQueryRepository {

    /** 정산 완료된 일자별 손익을 투자일자 오름차순으로 조회한다(누적 수익/그래프 소스). */
    List<DailyProfitProjection> findSettledDailyProfits(Long userId);

    /** 정산 완료된 투자의 섹터별 누적 매수금/손익을 조회한다(TOP3/성향 소스). */
    List<SectorStatProjection> findSettledSectorStats(Long userId);

    /**
     * 기간 윈도우 안의 정산 완료 투자 합계(누적 수익 내역 요약). {@code start}/{@code end}가 null이면 그 방향 경계를 두지 않는다(ALL).
     */
    PeriodProfitProjection findPeriodSummary(Long userId, LocalDate start, LocalDate end);

    /** 기간 윈도우 안의 섹터별 정산 완료 집계(누적 수익 내역 종목 목록). 경계 null 규칙은 위와 같다. */
    List<SectorHistoryProjection> findPeriodSectorStats(Long userId, LocalDate start, LocalDate end);

    /** {@code date} 이전(미포함)에 정산 완료 투자가 있는지. hasPrev 판정용. */
    boolean existsSettledBefore(Long userId, LocalDate date);

    /** {@code date} 이후(미포함)에 정산 완료 투자가 있는지. hasNext 판정용. */
    boolean existsSettledAfter(Long userId, LocalDate date);
}
