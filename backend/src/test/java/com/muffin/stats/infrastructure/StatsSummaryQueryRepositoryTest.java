package com.muffin.stats.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.global.config.QueryDslConfig;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.PriceDataSource;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.sector.domain.sectorgroup.SectorGroup;
import com.muffin.sector.domain.sectorgroup.SectorGroupRepository;
import com.muffin.stats.application.StatsQueryRepository;
import com.muffin.stats.application.projection.DailyProfitProjection;
import com.muffin.stats.application.projection.PeriodProfitProjection;
import com.muffin.stats.application.projection.SectorHistoryProjection;
import com.muffin.stats.application.projection.SectorStatProjection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 통계 집계 쿼리가 정산 완료(SETTLED) 투자만, 섹터/그룹까지 조인해 집계하는지 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import({QueryDslConfig.class, StatsQueryRepositoryImpl.class, JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StatsSummaryQueryRepositoryTest {

    private static final long USER_ID = 1L;
    private static final long DUMMY_ASSET_ID = 99L;

    @Autowired
    private StatsQueryRepository statsSummaryQueryRepository;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @Autowired
    private SectorGroupRepository sectorGroupRepository;

    @Test
    @DisplayName("정산 완료된 일자별 손익만 오름차순으로 조회하고 미정산 투자는 제외한다")
    void findSettledDailyProfits_returnsSettledOnlyAscending() {
        Long gold = seedSector("GOLD", "금", "BASE_ASSET");
        LocalDate base = LocalDate.of(2026, 5, 1);
        persistSettledInvestment(base.plusDays(2), Map.of(gold, sector(100_000L, 5_000L)));
        persistSettledInvestment(base.plusDays(4), Map.of(gold, sector(200_000L, 8_000L)));
        persistPendingInvestment(base.plusDays(5), gold); // 미정산 → 제외

        List<DailyProfitProjection> daily = statsSummaryQueryRepository.findSettledDailyProfits(USER_ID);

        assertEquals(2, daily.size());
        assertEquals(base.plusDays(2), daily.get(0).investDate());
        assertEquals(5_000L, daily.get(0).dailyProfitLoss());
        assertEquals(base.plusDays(4), daily.get(1).investDate());
        assertEquals(8_000L, daily.get(1).dailyProfitLoss());
    }

    @Test
    @DisplayName("섹터별 누적 매수금/손익을 그룹 name과 함께 여러 투자에 걸쳐 합산한다")
    void findSettledSectorStats_aggregatesAcrossInvestments() {
        Long gold = seedSector("GOLD", "금", "BASE_ASSET");
        Long tech = seedSector("TECH", "테크", "FUTURE_TECH");
        LocalDate base = LocalDate.of(2026, 5, 1);
        persistSettledInvestment(base.plusDays(1), Map.of(gold, sector(100_000L, 5_000L)));
        persistSettledInvestment(
                base.plusDays(2), Map.of(gold, sector(200_000L, 8_000L), tech, sector(100_000L, 2_000L)));

        Map<String, SectorStatProjection> byCode = statsSummaryQueryRepository.findSettledSectorStats(USER_ID).stream()
                .collect(Collectors.toMap(SectorStatProjection::sectorCode, Function.identity()));

        assertEquals(2, byCode.size());
        SectorStatProjection goldStat = byCode.get("GOLD");
        assertEquals(300_000L, goldStat.totalAmount());
        assertEquals(13_000L, goldStat.totalProfitLoss());
        assertEquals("BASE_ASSET", goldStat.groupCode());
        SectorStatProjection techStat = byCode.get("TECH");
        assertEquals(100_000L, techStat.totalAmount());
        assertEquals("FUTURE_TECH", techStat.groupCode());
    }

    @Test
    @DisplayName("정산 완료 이력이 없으면 빈 목록을 반환한다")
    void findSettledDailyProfits_emptyWhenNoSettlement() {
        assertTrue(statsSummaryQueryRepository.findSettledDailyProfits(USER_ID).isEmpty());
    }

    @Test
    @DisplayName("기간 요약은 윈도우 안의 정산 완료 투자만 합산하고, 경계가 null이면 전체를 합산한다")
    void findPeriodSummary_windowFilteringAndSums() {
        Long gold = seedSector("GOLD", "금", "BASE_ASSET");
        persistSettledInvestment(LocalDate.of(2026, 4, 30), Map.of(gold, sector(100_000L, 1_000L))); // 윈도우 이전
        persistSettledInvestment(LocalDate.of(2026, 5, 2), Map.of(gold, sector(100_000L, 5_000L)));
        persistSettledInvestment(LocalDate.of(2026, 5, 20), Map.of(gold, sector(200_000L, 8_000L)));
        persistSettledInvestment(LocalDate.of(2026, 6, 1), Map.of(gold, sector(100_000L, 2_000L))); // 윈도우 이후
        persistPendingInvestment(LocalDate.of(2026, 5, 10), gold); // 미정산 → 제외

        PeriodProfitProjection may = statsSummaryQueryRepository.findPeriodSummary(
                USER_ID, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31));
        assertEquals(300_000L, may.totalInvestment());
        assertEquals(13_000L, may.totalProfitLoss());

        PeriodProfitProjection all = statsSummaryQueryRepository.findPeriodSummary(USER_ID, null, null);
        assertEquals(500_000L, all.totalInvestment());
        assertEquals(16_000L, all.totalProfitLoss());
    }

    @Test
    @DisplayName("기간 섹터 집계는 윈도우 안의 정산 완료 투자만 섹터별로 합산한다")
    void findPeriodSectorStats_windowGrouping() {
        Long gold = seedSector("GOLD", "금", "BASE_ASSET");
        Long tech = seedSector("TECH", "테크", "FUTURE_TECH");
        persistSettledInvestment(LocalDate.of(2026, 5, 2), Map.of(gold, sector(100_000L, 5_000L)));
        persistSettledInvestment(
                LocalDate.of(2026, 5, 20), Map.of(gold, sector(200_000L, 8_000L), tech, sector(100_000L, 2_000L)));
        persistSettledInvestment(LocalDate.of(2026, 6, 1), Map.of(gold, sector(100_000L, 2_000L))); // 윈도우 이후 → 제외

        Map<String, SectorHistoryProjection> byCode =
                statsSummaryQueryRepository
                        .findPeriodSectorStats(USER_ID, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31))
                        .stream()
                        .collect(Collectors.toMap(SectorHistoryProjection::sectorCode, Function.identity()));

        assertEquals(2, byCode.size());
        assertEquals(300_000L, byCode.get("GOLD").totalInvestment());
        assertEquals(13_000L, byCode.get("GOLD").totalProfitLoss());
        assertEquals(100_000L, byCode.get("TECH").totalInvestment());
        assertEquals(2_000L, byCode.get("TECH").totalProfitLoss());
    }

    @Test
    @DisplayName("existsSettledBefore/After는 경계 밖 정산 완료 데이터 유무를 반환한다")
    void existsSettledBeforeAfter() {
        Long gold = seedSector("GOLD", "금", "BASE_ASSET");
        persistSettledInvestment(LocalDate.of(2026, 5, 10), Map.of(gold, sector(100_000L, 5_000L)));
        persistSettledInvestment(LocalDate.of(2026, 5, 20), Map.of(gold, sector(100_000L, 3_000L)));

        assertTrue(statsSummaryQueryRepository.existsSettledBefore(USER_ID, LocalDate.of(2026, 5, 15)));
        assertFalse(statsSummaryQueryRepository.existsSettledBefore(USER_ID, LocalDate.of(2026, 5, 10)));
        assertTrue(statsSummaryQueryRepository.existsSettledAfter(USER_ID, LocalDate.of(2026, 5, 15)));
        assertFalse(statsSummaryQueryRepository.existsSettledAfter(USER_ID, LocalDate.of(2026, 5, 20)));
    }

    private record SectorSpec(long amount, long profitLoss) {}

    private SectorSpec sector(long amount, long profitLoss) {
        return new SectorSpec(amount, profitLoss);
    }

    private Long seedSector(String sectorCode, String name, String groupCode) {
        SectorGroup group = sectorGroupRepository.save(SectorGroup.create(groupCode, name + "그룹", null, 1));
        Sector saved = sectorRepository.save(Sector.create(group.getId(), 1L, name, null, sectorCode, 1));
        return saved.getId();
    }

    private void persistSettledInvestment(LocalDate investDate, Map<Long, SectorSpec> sectors) {
        Investment investment = Investment.confirm(USER_ID, DUMMY_ASSET_ID, investDate);
        sectors.forEach((sectorId, spec) -> investment.addSector(sectorId, 1, spec.amount(), BigDecimal.valueOf(100)));
        sectors.forEach((sectorId, spec) -> investment.applySectorResult(
                sectorId, BigDecimal.valueOf(110), spec.profitLoss(), BigDecimal.ZERO, PriceDataSource.NORMAL));
        investment.settle(LocalDateTime.now());
        investmentRepository.save(investment);
    }

    private void persistPendingInvestment(LocalDate investDate, Long sectorId) {
        Investment investment = Investment.confirm(USER_ID, DUMMY_ASSET_ID, investDate);
        investment.addSector(sectorId, 1, 100_000L, BigDecimal.valueOf(100));
        investmentRepository.save(investment);
    }
}
