package com.muffin.ranking.infrastructure;

import com.muffin.investment.domain.investment.QInvestment;
import com.muffin.investment.domain.investment.QInvestmentSector;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.ranking.application.WeeklyRankingQueryRepository;
import com.muffin.ranking.application.projection.WeeklyInvestmentProjection;
import com.muffin.ranking.application.projection.WeeklyRankingProjection;
import com.muffin.ranking.application.projection.WeeklySectorProjection;
import com.muffin.ranking.domain.weeklyranking.QWeeklyRanking;
import com.muffin.sector.domain.sector.QSector;
import com.muffin.user.domain.QUser;
import com.muffin.user.domain.enums.UserStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 주간 랭킹 화면에 필요한 스냅샷과 TOP 10 상세를 고정된 쿼리 수로 조회한다. */
@Repository
@RequiredArgsConstructor
public class WeeklyRankingQueryRepositoryImpl implements WeeklyRankingQueryRepository {

    private static final int TOP_10_LIMIT = 10;

    private final JPAQueryFactory queryFactory;

    @Override
    public boolean existsSnapshot(LocalDate weekStartDate) {
        QWeeklyRanking weeklyRanking = QWeeklyRanking.weeklyRanking;
        return queryFactory
                        .selectOne()
                        .from(weeklyRanking)
                        .where(weeklyRanking.weekStartDate.eq(weekStartDate))
                        .fetchFirst()
                != null;
    }

    @Override
    public boolean hasCalculatingTarget(LocalDate weekStartDate, LocalDate weekEndDate) {
        QInvestment investment = QInvestment.investment;
        QUser user = QUser.user;
        return queryFactory
                        .selectOne()
                        .from(investment)
                        .join(user)
                        .on(user.userId.eq(investment.userId))
                        .where(
                                investment.status.eq(InvestmentStatus.CONFIRMED),
                                investment.investDate.between(weekStartDate, weekEndDate),
                                user.status.eq(UserStatus.ACTIVE),
                                investment.totalAmount.gt(0L),
                                investment
                                        .settlementStatus
                                        .in(SettlementStatus.PENDING, SettlementStatus.FAILED)
                                        .or(investment.settlementStatus.eq(SettlementStatus.SETTLED)))
                        .fetchFirst()
                != null;
    }

    @Override
    public Optional<WeeklyRankingProjection> findMyRank(Long userId, LocalDate weekStartDate) {
        QWeeklyRanking weeklyRanking = QWeeklyRanking.weeklyRanking;
        return Optional.ofNullable(queryFactory
                .select(rankingProjection(weeklyRanking))
                .from(weeklyRanking)
                .where(weeklyRanking.userId.eq(userId), weeklyRanking.weekStartDate.eq(weekStartDate))
                .fetchOne());
    }

    @Override
    public List<WeeklyRankingProjection> findTop10(LocalDate weekStartDate) {
        QWeeklyRanking weeklyRanking = QWeeklyRanking.weeklyRanking;
        return queryFactory
                .select(rankingProjection(weeklyRanking))
                .from(weeklyRanking)
                .where(weeklyRanking.weekStartDate.eq(weekStartDate), weeklyRanking.rankingPosition.loe(TOP_10_LIMIT))
                .orderBy(weeklyRanking.rankingPosition.asc())
                .limit(TOP_10_LIMIT)
                .fetch();
    }

    @Override
    public List<WeeklyInvestmentProjection> findWeeklyInvestments(
            List<Long> userIds, LocalDate weekStartDate, LocalDate weekEndDate) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        QInvestment investment = QInvestment.investment;
        return queryFactory
                .select(Projections.constructor(
                        WeeklyInvestmentProjection.class, investment.userId, investment.totalAmount.sum()))
                .from(investment)
                .where(settledConfirmedInWeek(investment, userIds, weekStartDate, weekEndDate))
                .groupBy(investment.userId)
                .fetch();
    }

    @Override
    public List<WeeklySectorProjection> findWeeklySectors(
            List<Long> userIds, LocalDate weekStartDate, LocalDate weekEndDate) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        QInvestment investment = QInvestment.investment;
        QInvestmentSector investmentSector = QInvestmentSector.investmentSector;
        QSector sector = QSector.sector;
        return queryFactory
                .select(Projections.constructor(
                        WeeklySectorProjection.class,
                        investment.userId,
                        sector.sectorCode,
                        sector.name,
                        investmentSector.amount.sum(),
                        investmentSector.profitLoss.sum()))
                .from(investment)
                .join(investment.sectors, investmentSector)
                .join(sector)
                .on(sector.id.eq(investmentSector.sectorId))
                .where(settledConfirmedInWeek(investment, userIds, weekStartDate, weekEndDate))
                .groupBy(investment.userId, sector.sectorCode, sector.name)
                .fetch();
    }

    private static com.querydsl.core.types.dsl.BooleanExpression settledConfirmedInWeek(
            QInvestment investment, List<Long> userIds, LocalDate weekStartDate, LocalDate weekEndDate) {
        return investment
                .userId
                .in(userIds)
                .and(investment.status.eq(InvestmentStatus.CONFIRMED))
                .and(investment.settlementStatus.eq(SettlementStatus.SETTLED))
                .and(investment.investDate.between(weekStartDate, weekEndDate));
    }

    private static com.querydsl.core.types.ConstructorExpression<WeeklyRankingProjection> rankingProjection(
            QWeeklyRanking weeklyRanking) {
        return Projections.constructor(
                WeeklyRankingProjection.class,
                weeklyRanking.userId,
                weeklyRanking.nicknameSnapshot,
                weeklyRanking.rankingPosition,
                weeklyRanking.weeklyProfit,
                weeklyRanking.weeklyProfitRate,
                weeklyRanking.percentile);
    }
}
