package com.muffin.stats.application;

import com.muffin.stats.application.projection.DailyProfitProjection;
import com.muffin.stats.application.projection.PeriodProfitProjection;
import com.muffin.stats.application.projection.RecentInvestmentProjection;
import com.muffin.stats.application.projection.RecentSectorProjection;
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

    /** 가장 최근 정산 완료(SETTLED) 투자 1건의 헤더를 조회한다. 정산 이력이 없으면 null(최근 투자 성과 상세 소스). */
    RecentInvestmentProjection findLatestSettledInvestment(Long userId);

    /** 특정 투자의 섹터별 상세(코드/표시명/매수금/손익/가격 출처)를 매수금 내림차순으로 조회한다. */
    List<RecentSectorProjection> findSettledSectors(Long investmentId);
}
