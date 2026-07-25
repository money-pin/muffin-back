package com.muffin.investment.infrastructure;

import com.muffin.investment.application.projection.SettlementResultProjection;
import com.muffin.investment.application.settlement.SettlementQueryRepository;
import com.muffin.investment.domain.investment.QInvestment;
import com.muffin.investment.domain.userasset.QUserAsset;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SettlementQueryRepositoryImpl implements SettlementQueryRepository {

    private final JPAQueryFactory queryFactory;

    // 정산 결과 팝업 조회
    @Override
    public Optional<SettlementResultProjection> findRecentDueSettlement(Long userId, LocalDate today) {
        QInvestment investment = QInvestment.investment;
        QUserAsset userAsset = QUserAsset.userAsset;
        return Optional.ofNullable(queryFactory
                .select(Projections.constructor(
                        SettlementResultProjection.class,
                        investment.settlementStatus,
                        investment.investDate,
                        investment.totalAmount,
                        investment.totalProfitLoss,
                        investment.totalProfitLossRate,
                        userAsset.totalAsset))
                .from(investment)
                .join(userAsset)
                .on(userAsset.id.eq(investment.userAssetId))
                .where(investment.userId.eq(userId).and(investment.investDate.lt(today)))
                .orderBy(investment.investDate.desc())
                .limit(1)
                .fetchOne());
    }
}
