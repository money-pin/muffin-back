package com.muffin.stats.infrastructure;

import com.muffin.investment.domain.investment.QInvestment;
import com.muffin.investment.domain.investment.QInvestmentSector;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.sector.domain.sector.QSector;
import com.muffin.sector.domain.sectorgroup.QSectorGroup;
import com.muffin.stats.application.StatsQueryRepository;
import com.muffin.stats.application.projection.DailyProfitProjection;
import com.muffin.stats.application.projection.PeriodProfitProjection;
import com.muffin.stats.application.projection.SectorHistoryProjection;
import com.muffin.stats.application.projection.SectorStatProjection;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class StatsQueryRepositoryImpl implements StatsQueryRepository {

    private final JPAQueryFactory queryFactory;

    /** 수익 통계 조회. 정산 완료(SETTLED) 투자만 집계한다. */
    @Override
    public List<DailyProfitProjection> findSettledDailyProfits(Long userId) {
        QInvestment investment = QInvestment.investment;
        return queryFactory
                .select(Projections.constructor(
                        DailyProfitProjection.class, investment.investDate, investment.totalProfitLoss))
                .from(investment)
                .where(investment.userId.eq(userId).and(investment.settlementStatus.eq(SettlementStatus.SETTLED)))
                .orderBy(investment.investDate.asc())
                .fetch();
    }

    @Override
    public List<SectorStatProjection> findSettledSectorStats(Long userId) {
        QInvestment investment = QInvestment.investment;
        QInvestmentSector investmentSector = QInvestmentSector.investmentSector;
        QSector sector = QSector.sector;
        QSectorGroup sectorGroup = QSectorGroup.sectorGroup;
        return queryFactory
                .select(Projections.constructor(
                        SectorStatProjection.class,
                        sector.sectorCode,
                        sector.name,
                        sectorGroup.groupCode,
                        investmentSector.amount.sum(),
                        investmentSector.profitLoss.sum()))
                .from(investment)
                .join(investment.sectors, investmentSector)
                .join(sector)
                .on(sector.id.eq(investmentSector.sectorId))
                .join(sectorGroup)
                .on(sectorGroup.id.eq(sector.sectorGroupId))
                .where(investment.userId.eq(userId).and(investment.settlementStatus.eq(SettlementStatus.SETTLED)))
                .groupBy(sector.sectorCode, sector.name, sectorGroup.groupCode)
                .fetch();
    }

    @Override
    public PeriodProfitProjection findPeriodSummary(Long userId, LocalDate start, LocalDate end) {
        QInvestment investment = QInvestment.investment;
        return queryFactory
                .select(Projections.constructor(
                        PeriodProfitProjection.class, investment.totalAmount.sum(), investment.totalProfitLoss.sum()))
                .from(investment)
                .where(settledInWindow(investment, userId, start, end))
                .fetchOne();
    }

    @Override
    public List<SectorHistoryProjection> findPeriodSectorStats(Long userId, LocalDate start, LocalDate end) {
        QInvestment investment = QInvestment.investment;
        QInvestmentSector investmentSector = QInvestmentSector.investmentSector;
        QSector sector = QSector.sector;
        return queryFactory
                .select(Projections.constructor(
                        SectorHistoryProjection.class,
                        sector.sectorCode,
                        sector.name,
                        investmentSector.amount.sum(),
                        investmentSector.profitLoss.sum()))
                .from(investment)
                .join(investment.sectors, investmentSector)
                .join(sector)
                .on(sector.id.eq(investmentSector.sectorId))
                .where(settledInWindow(investment, userId, start, end))
                .groupBy(sector.sectorCode, sector.name)
                .fetch();
    }

    @Override
    public boolean existsSettledBefore(Long userId, LocalDate date) {
        QInvestment investment = QInvestment.investment;
        return queryFactory
                        .selectOne()
                        .from(investment)
                        .where(settled(investment, userId).and(investment.investDate.lt(date)))
                        .fetchFirst()
                != null;
    }

    @Override
    public boolean existsSettledAfter(Long userId, LocalDate date) {
        QInvestment investment = QInvestment.investment;
        return queryFactory
                        .selectOne()
                        .from(investment)
                        .where(settled(investment, userId).and(investment.investDate.gt(date)))
                        .fetchFirst()
                != null;
    }

    /** (userId, SETTLED) 기본 조건. */
    private static BooleanBuilder settled(QInvestment investment, Long userId) {
        return new BooleanBuilder(
                investment.userId.eq(userId).and(investment.settlementStatus.eq(SettlementStatus.SETTLED)));
    }

    /** (userId, SETTLED) + invest_date 윈도우. start/end가 null이면 그 방향 경계를 두지 않는다(ALL). */
    private static BooleanBuilder settledInWindow(QInvestment investment, Long userId, LocalDate start, LocalDate end) {
        BooleanBuilder where = settled(investment, userId);
        if (start != null) {
            where.and(investment.investDate.goe(start));
        }
        if (end != null) {
            where.and(investment.investDate.loe(end));
        }
        return where;
    }
}
