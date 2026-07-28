package com.muffin.ranking.infrastructure;

import com.muffin.investment.domain.investment.QInvestment;
import com.muffin.investment.domain.investment.enums.InvestmentStatus;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.ranking.application.WeeklyRankingAggregationRepository;
import com.muffin.ranking.domain.weeklyranking.QWeeklyRanking;
import com.muffin.ranking.domain.weeklyranking.WeeklyRankingCandidate;
import com.muffin.user.domain.QUser;
import com.muffin.user.domain.enums.UserStatus;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** 주간 랭킹 배치의 대상 확인 및 사용자별 투자 집계를 QueryDSL로 수행한다. */
@Repository
@RequiredArgsConstructor
public class WeeklyRankingAggregationRepositoryImpl implements WeeklyRankingAggregationRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public boolean existsByWeekStartDate(LocalDate weekStartDate) {
        QWeeklyRanking weeklyRanking = QWeeklyRanking.weeklyRanking;
        return queryFactory
                        .selectOne()
                        .from(weeklyRanking)
                        .where(weeklyRanking.weekStartDate.eq(weekStartDate))
                        .fetchFirst()
                != null;
    }

    @Override
    public boolean hasUnsettledConfirmedInvestment(LocalDate weekStartDate, LocalDate weekEndDate) {
        QInvestment investment = QInvestment.investment;
        return queryFactory
                        .selectOne()
                        .from(investment)
                        .where(
                                investment.status.eq(InvestmentStatus.CONFIRMED),
                                investment.settlementStatus.in(SettlementStatus.PENDING, SettlementStatus.FAILED),
                                investment.investDate.between(weekStartDate, weekEndDate))
                        .fetchFirst()
                != null;
    }

    @Override
    public List<WeeklyRankingCandidate> findSettledCandidates(LocalDate weekStartDate, LocalDate weekEndDate) {
        QInvestment investment = QInvestment.investment;
        QUser user = QUser.user;

        return queryFactory
                .select(Projections.constructor(
                        WeeklyRankingCandidate.class,
                        investment.userId,
                        user.nickname,
                        user.userUuid,
                        investment.totalAmount.sum(),
                        investment.totalProfitLoss.sum()))
                .from(investment)
                .join(user)
                .on(user.userId.eq(investment.userId))
                .where(
                        investment.status.eq(InvestmentStatus.CONFIRMED),
                        investment.settlementStatus.eq(SettlementStatus.SETTLED),
                        investment.investDate.between(weekStartDate, weekEndDate),
                        user.status.eq(UserStatus.ACTIVE))
                .groupBy(investment.userId, user.nickname, user.userUuid)
                .having(investment.totalAmount.sum().gt(0L))
                .fetch();
    }
}
