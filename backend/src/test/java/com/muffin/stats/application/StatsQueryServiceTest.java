package com.muffin.stats.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.muffin.stats.application.projection.DailyProfitProjection;
import com.muffin.stats.application.projection.SectorStatProjection;
import com.muffin.stats.presentation.dto.StatsSummaryResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse.TopSectorResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 조회 결과를 통계 응답으로 조립하는 로직(누적/그래프/TOP3/성향)을 검증한다. 저장소는 정적 stub으로 대체한다. */
class StatsQueryServiceTest {

    private static final LocalDate DAY_5 = LocalDate.of(2026, 5, 5);
    private static final LocalDate DAY_8 = LocalDate.of(2026, 5, 8);

    @Test
    @DisplayName("정산 이력이 없으면 investDate/성향은 null, 누적 0, 그래프/TOP3는 빈 배열이다")
    void getSummary_emptyState() {
        StatsSummaryResponse response = serviceWith(List.of(), List.of()).getSummary(1L);

        assertNull(response.investDate());
        assertEquals(0L, response.cumulativeProfitAmount());
        assertEquals(new BigDecimal("0.0"), response.cumulativeProfitRate());
        assertTrue(response.graph().isEmpty());
        assertTrue(response.topSectors().isEmpty());
        assertNull(response.investmentType());
    }

    @Test
    @DisplayName("누적 수익률은 초기자본 100만 기준이고, 헤드라인은 최근 정산일과 누적 손익이다")
    void getSummary_cumulativeHeadline() {
        StatsSummaryResponse response = serviceWith(
                        List.of(new DailyProfitProjection(DAY_5, 45_000L), new DailyProfitProjection(DAY_8, 83_000L)),
                        List.of())
                .getSummary(1L);

        assertEquals(DAY_8, response.investDate());
        assertEquals(128_000L, response.cumulativeProfitAmount());
        assertEquals(new BigDecimal("12.8"), response.cumulativeProfitRate());
    }

    @Test
    @DisplayName("그래프는 최근 정산일 포함 7일이고, 최초 투자 이전은 0, 미투자일은 직전 누적을 유지한다")
    void getSummary_graphCarryForward() {
        StatsSummaryResponse response = serviceWith(
                        List.of(new DailyProfitProjection(DAY_5, 45_000L), new DailyProfitProjection(DAY_8, 83_000L)),
                        List.of())
                .getSummary(1L);

        assertEquals(7, response.graph().size());
        // 5/2 ~ 5/8. 최초 정산(5/5) 이전은 0, 5/5~5/7은 4.5 유지, 5/8은 12.8(=헤드라인)
        assertEquals(LocalDate.of(2026, 5, 2), response.graph().get(0).date());
        assertEquals(new BigDecimal("0.0"), response.graph().get(0).cumulativeProfitRate());
        assertEquals(new BigDecimal("0.0"), response.graph().get(2).cumulativeProfitRate()); // 5/4
        assertEquals(new BigDecimal("4.5"), response.graph().get(3).cumulativeProfitRate()); // 5/5
        assertEquals(new BigDecimal("4.5"), response.graph().get(5).cumulativeProfitRate()); // 5/7 carry-forward
        assertEquals(DAY_8, response.graph().get(6).date());
        assertEquals(new BigDecimal("12.8"), response.graph().get(6).cumulativeProfitRate());
    }

    @Test
    @DisplayName("TOP3는 섹터 누적 수익률(그 섹터 매수금 기준) 내림차순 3개이고 rank가 매겨진다")
    void getSummary_topSectorsOrdering() {
        List<SectorStatProjection> sectors = List.of(
                new SectorStatProjection("SEMICONDUCTOR", "반도체", "FUTURE_TECH", 642_000L, 52_000L), // 8.1%
                new SectorStatProjection("GOLD", "금", "BASE_ASSET", 600_000L, 31_000L), // 5.2%
                new SectorStatProjection("TECH", "테크", "FUTURE_TECH", 615_000L, 24_000L), // 3.9%
                new SectorStatProjection("BOND", "채권", "BASE_ASSET", 400_000L, 4_000L)); // 1.0% (제외)

        StatsSummaryResponse response = serviceWith(List.of(new DailyProfitProjection(DAY_8, 111_000L)), sectors)
                .getSummary(1L);

        List<TopSectorResponse> top = response.topSectors();
        assertEquals(3, top.size());
        assertEquals(1, top.getFirst().rank());
        assertEquals("SEMICONDUCTOR", top.getFirst().sectorCode());
        assertEquals(new BigDecimal("8.1"), top.get(0).profitRate());
        assertEquals(52_000L, top.get(0).profitAmount());
        assertEquals("GOLD", top.get(1).sectorCode());
        assertEquals(new BigDecimal("5.2"), top.get(1).profitRate());
        assertEquals("TECH", top.get(2).sectorCode());
    }

    @Test
    @DisplayName("성향은 자산군 비중으로 판정하고 불릿1에 비중을 조립한다(기술주 56% → 성장추구형)")
    void getSummary_investmentTypeGrowth() {
        List<SectorStatProjection> sectors = List.of(
                new SectorStatProjection("SEMICONDUCTOR", "반도체", "FUTURE_TECH", 642_000L, 0L),
                new SectorStatProjection("TECH", "테크", "FUTURE_TECH", 615_000L, 0L),
                new SectorStatProjection("GOLD", "금", "BASE_ASSET", 600_000L, 0L),
                new SectorStatProjection("BOND", "채권", "BASE_ASSET", 400_000L, 0L));

        StatsSummaryResponse response = serviceWith(List.of(new DailyProfitProjection(DAY_8, 0L)), sectors)
                .getSummary(1L);

        assertEquals("GROWTH", response.investmentType().type());
        assertEquals("성장추구형 투자자", response.investmentType().label());
        assertEquals(
                "기초 자산 44%, 기술주 56%, 실물 경제 0%",
                response.investmentType().bullets().getFirst());
    }

    @Test
    @DisplayName("자산군 비중 균형이면 균형형이고 비중 합은 100이다(40/35/25)")
    void getSummary_investmentTypeBalanced() {
        List<SectorStatProjection> sectors = List.of(
                new SectorStatProjection("GOLD", "금", "BASE_ASSET", 400_000L, 0L),
                new SectorStatProjection("TECH", "테크", "FUTURE_TECH", 350_000L, 0L),
                new SectorStatProjection("ENERGY", "에너지", "REAL_ECONOMY", 250_000L, 0L));

        StatsSummaryResponse response = serviceWith(List.of(new DailyProfitProjection(DAY_8, 0L)), sectors)
                .getSummary(1L);

        assertEquals("BALANCED", response.investmentType().type());
        assertEquals(
                "기초 자산 40%, 기술주 35%, 실물 경제 25%",
                response.investmentType().bullets().getFirst());
    }

    private StatsQueryService serviceWith(
            List<DailyProfitProjection> dailyProfits, List<SectorStatProjection> sectorStats) {
        return new StatsQueryService(new StatsQueryRepository() {
            @Override
            public List<DailyProfitProjection> findSettledDailyProfits(Long userId) {
                return dailyProfits;
            }

            @Override
            public List<SectorStatProjection> findSettledSectorStats(Long userId) {
                return sectorStats;
            }
        });
    }
}
