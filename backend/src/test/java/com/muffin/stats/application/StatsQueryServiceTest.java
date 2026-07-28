package com.muffin.stats.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.muffin.investment.domain.investment.enums.PriceDataSource;
import com.muffin.stats.application.projection.DailyProfitProjection;
import com.muffin.stats.application.projection.PeriodProfitProjection;
import com.muffin.stats.application.projection.RecentInvestmentProjection;
import com.muffin.stats.application.projection.RecentSectorProjection;
import com.muffin.stats.application.projection.SectorHistoryProjection;
import com.muffin.stats.application.projection.SectorStatProjection;
import com.muffin.stats.domain.HistorySort;
import com.muffin.stats.domain.StatsPeriod;
import com.muffin.stats.domain.exception.StatsException;
import com.muffin.stats.domain.exception.code.StatsErrorCode;
import com.muffin.stats.presentation.dto.ProfitHistoryResponse;
import com.muffin.stats.presentation.dto.ProfitHistoryResponse.SectorHistoryResponse;
import com.muffin.stats.presentation.dto.RecentDetailResponse;
import com.muffin.stats.presentation.dto.RecentDetailResponse.SectorDetailResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse;
import com.muffin.stats.presentation.dto.StatsSummaryResponse.TopSectorResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 조회 결과를 통계 응답으로 조립하는 로직(누적/그래프/TOP3/성향, 누적 수익 내역)을 검증한다. 저장소는 정적 stub으로 대체한다. */
class StatsQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 19);
    private static final Clock FIXED_CLOCK = Clock.fixed(TODAY.atStartOfDay(KST).toInstant(), KST);

    private static final LocalDate DAY_5 = LocalDate.of(2026, 5, 5);
    private static final LocalDate DAY_8 = LocalDate.of(2026, 5, 8);

    @Test
    @DisplayName("정산 이력이 없으면 investDate/성향은 null, 누적 0, 그래프는 오늘 포함 7일치 0%, TOP3는 빈 배열이다")
    void getSummary_emptyState() {
        StatsSummaryResponse response = serviceWith(List.of(), List.of()).getSummary(1L);

        assertNull(response.investDate());
        assertEquals(0L, response.cumulativeProfitAmount());
        assertEquals(new BigDecimal("0.0"), response.cumulativeProfitRate());
        assertEquals(7, response.graph().size());
        assertEquals(TODAY.minusDays(6), response.graph().get(0).date());
        assertEquals(TODAY, response.graph().get(6).date());
        assertTrue(response.graph().stream()
                .allMatch(point -> point.cumulativeProfitRate().equals(new BigDecimal("0.0"))));
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

    @Test
    @DisplayName("누적 수익 내역: 요약 손익률은 기간 매수금 대비이고 date/기간이 응답에 echo된다")
    void getHistory_summaryRateOverInvestment() {
        StubRepo repo = new StubRepo();
        repo.periodSummary = new PeriodProfitProjection(1_000_000L, 132_000L);
        ProfitHistoryResponse response =
                service(repo).getHistory(1L, StatsPeriod.MONTH, "2026-06", HistorySort.RATE_DESC);

        assertEquals("MONTH", response.period());
        assertEquals("2026-06", response.date());
        assertEquals("RATE_DESC", response.sort());
        assertEquals(132_000L, response.summary().profitAmount());
        assertEquals(1_000_000L, response.summary().totalInvestment());
        assertEquals(new BigDecimal("13.2"), response.summary().profitRate());
    }

    @Test
    @DisplayName("누적 수익 내역: 종목은 정렬 기준(RATE_DESC/AMOUNT_DESC)에 따라 정렬된다")
    void getHistory_sectorsSorted() {
        StubRepo repo = new StubRepo();
        repo.periodSummary = new PeriodProfitProjection(660_000L, 118_000L);
        repo.periodSectors = List.of(
                new SectorHistoryProjection("A", "에이", 360_000L, 80_000L), // 22.2%
                new SectorHistoryProjection("B", "비", 200_000L, -12_000L), // -6.0%
                new SectorHistoryProjection("C", "씨", 100_000L, 50_000L)); // 50.0%

        List<SectorHistoryResponse> byRate = service(repo)
                .getHistory(1L, StatsPeriod.MONTH, "2026-06", HistorySort.RATE_DESC)
                .sectors();
        assertEquals(
                List.of("C", "A", "B"),
                byRate.stream().map(SectorHistoryResponse::sectorCode).toList());
        assertEquals(new BigDecimal("50.0"), byRate.getFirst().profitRate());

        List<SectorHistoryResponse> byAmount = service(repo)
                .getHistory(1L, StatsPeriod.MONTH, "2026-06", HistorySort.AMOUNT_DESC)
                .sectors();
        assertEquals(
                List.of("A", "C", "B"),
                byAmount.stream().map(SectorHistoryResponse::sectorCode).toList());
    }

    @Test
    @DisplayName("누적 수익 내역: 기간에 데이터가 없으면 요약은 0(0.0), 종목은 빈 배열이다")
    void getHistory_emptyWindow() {
        StubRepo repo = new StubRepo();
        repo.periodSummary = new PeriodProfitProjection(null, null); // SUM 결과 없음
        repo.periodSectors = List.of();
        ProfitHistoryResponse response =
                service(repo).getHistory(1L, StatsPeriod.MONTH, "2026-06", HistorySort.RATE_DESC);

        assertEquals(0L, response.summary().profitAmount());
        assertEquals(0L, response.summary().totalInvestment());
        assertEquals(new BigDecimal("0.0"), response.summary().profitRate());
        assertTrue(response.sectors().isEmpty());
    }

    @Test
    @DisplayName("누적 수익 내역: hasPrev/hasNext는 윈도우 밖 데이터 존재로 판단하고, ALL은 date null에 둘 다 false다")
    void getHistory_navigationFlags() {
        StubRepo bounded = new StubRepo();
        bounded.periodSummary = new PeriodProfitProjection(100_000L, 10_000L);
        bounded.settledBefore = true;
        bounded.settledAfter = false;
        ProfitHistoryResponse month =
                service(bounded).getHistory(1L, StatsPeriod.MONTH, "2026-06", HistorySort.RATE_DESC);
        assertTrue(month.hasPrev());
        assertFalse(month.hasNext());

        StubRepo all = new StubRepo();
        all.periodSummary = new PeriodProfitProjection(100_000L, 10_000L);
        all.settledBefore = true; // 무시되어야 함(경계 없음)
        all.settledAfter = true;
        ProfitHistoryResponse allResponse = service(all).getHistory(1L, StatsPeriod.ALL, null, HistorySort.RATE_DESC);
        assertNull(allResponse.date());
        assertFalse(allResponse.hasPrev());
        assertFalse(allResponse.hasNext());
    }

    @Test
    @DisplayName("누적 수익 내역: 탭과 date 형식이 맞지 않으면 INVALID_DATE_FORMAT 예외가 서비스에서 그대로 전파된다")
    void getHistory_dateFormatMismatchThrows() {
        StatsQueryService service = service(new StubRepo());

        StatsException e = assertThrows(
                StatsException.class,
                () -> service.getHistory(1L, StatsPeriod.MONTH, "2026-06-21", HistorySort.RATE_DESC));
        assertEquals(StatsErrorCode.INVALID_DATE_FORMAT, e.getErrorCode());
    }

    @Test
    @DisplayName("최근 투자 상세: 정산 이력이 없으면 date null, 금액 0, 섹터는 빈 배열이다")
    void getRecentDetail_emptyWhenNoSettlement() {
        RecentDetailResponse response = service(new StubRepo()).getRecentDetail(1L);

        assertNull(response.date());
        assertEquals(0L, response.totalInvestment());
        assertEquals(0L, response.profitAmount());
        assertTrue(response.sectors().isEmpty());
    }

    @Test
    @DisplayName("최근 투자 상세: 섹터별 손익률을 매수금 대비로 계산하고 폴백 섹터는 isFallback=true로 내려준다")
    void getRecentDetail_mapsSectorsWithFallbackAndRate() {
        StubRepo repo = new StubRepo();
        repo.latestSettled = new RecentInvestmentProjection(7L, DAY_8, 500_000L, 18_000L);
        repo.recentSectors = List.of(
                new RecentSectorProjection("SEMICONDUCTOR", "반도체", 300_000L, 18_000L, PriceDataSource.NORMAL),
                new RecentSectorProjection("GOLD", "금", 200_000L, 0L, PriceDataSource.FALLBACK_ZERO));

        RecentDetailResponse response = service(repo).getRecentDetail(1L);

        assertEquals(DAY_8, response.date());
        assertEquals(500_000L, response.totalInvestment());
        assertEquals(18_000L, response.profitAmount());
        assertEquals(2, response.sectors().size());

        SectorDetailResponse semiconductor = response.sectors().get(0);
        assertEquals("SEMICONDUCTOR", semiconductor.sectorCode());
        assertEquals(18_000L, semiconductor.profitAmount());
        assertEquals(300_000L, semiconductor.totalInvestment());
        assertEquals(new BigDecimal("6.0"), semiconductor.profitRate()); // 18,000 / 300,000
        assertFalse(semiconductor.isFallback());

        SectorDetailResponse gold = response.sectors().get(1);
        assertEquals("GOLD", gold.sectorCode());
        assertEquals(0L, gold.profitAmount());
        assertEquals(new BigDecimal("0.0"), gold.profitRate());
        assertTrue(gold.isFallback());
    }

    private StatsQueryService serviceWith(
            List<DailyProfitProjection> dailyProfits, List<SectorStatProjection> sectorStats) {
        StubRepo repo = new StubRepo();
        repo.dailyProfits = dailyProfits;
        repo.sectorStats = sectorStats;
        return service(repo);
    }

    private StatsQueryService service(StatsQueryRepository repo) {
        return new StatsQueryService(repo, FIXED_CLOCK);
    }

    /** 필요한 반환값만 필드로 채워 쓰는 저장소 stub. */
    private static final class StubRepo implements StatsQueryRepository {
        private List<DailyProfitProjection> dailyProfits = List.of();
        private List<SectorStatProjection> sectorStats = List.of();
        private PeriodProfitProjection periodSummary = new PeriodProfitProjection(null, null);
        private List<SectorHistoryProjection> periodSectors = List.of();
        private boolean settledBefore = false;
        private boolean settledAfter = false;
        private RecentInvestmentProjection latestSettled = null;
        private List<RecentSectorProjection> recentSectors = List.of();

        @Override
        public List<DailyProfitProjection> findSettledDailyProfits(Long userId) {
            return dailyProfits;
        }

        @Override
        public List<SectorStatProjection> findSettledSectorStats(Long userId) {
            return sectorStats;
        }

        @Override
        public PeriodProfitProjection findPeriodSummary(Long userId, LocalDate start, LocalDate end) {
            return periodSummary;
        }

        @Override
        public List<SectorHistoryProjection> findPeriodSectorStats(Long userId, LocalDate start, LocalDate end) {
            return periodSectors;
        }

        @Override
        public boolean existsSettledBefore(Long userId, LocalDate date) {
            return settledBefore;
        }

        @Override
        public boolean existsSettledAfter(Long userId, LocalDate date) {
            return settledAfter;
        }

        @Override
        public RecentInvestmentProjection findLatestSettledInvestment(Long userId) {
            return latestSettled;
        }

        @Override
        public List<RecentSectorProjection> findSettledSectors(Long investmentId) {
            return recentSectors;
        }
    }
}
