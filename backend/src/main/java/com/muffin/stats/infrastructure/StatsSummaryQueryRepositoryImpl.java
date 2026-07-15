package com.muffin.stats.infrastructure;

import com.muffin.investment.domain.investment.QInvestment;
import com.muffin.investment.domain.investment.QInvestmentSector;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.sector.domain.sector.QSector;
import com.muffin.sector.domain.sectorgroup.QSectorGroup;
import com.muffin.stats.application.StatsSummaryQueryRepository;
import com.muffin.stats.application.projection.DailyProfitProjection;
import com.muffin.stats.application.projection.SectorStatProjection;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 수익 통계 조회 QueryDSL 구현. 정산 완료(SETTLED) 투자만 집계한다. */
@Repository
@RequiredArgsConstructor
public class StatsSummaryQueryRepositoryImpl implements StatsSummaryQueryRepository {

    private final JPAQueryFactory queryFactory;

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
}
