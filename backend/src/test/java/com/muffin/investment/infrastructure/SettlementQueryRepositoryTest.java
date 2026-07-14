package com.muffin.investment.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.global.config.QueryDslConfig;
import com.muffin.investment.application.SettlementQueryRepository;
import com.muffin.investment.application.projection.SettlementResultProjection;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.SettlementStatus;
import com.muffin.investment.domain.userasset.UserAsset;
import com.muffin.investment.domain.userasset.UserAssetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 정산 결과 조회 쿼리가 "정산일이 지난 최근 투자"만 골라오는지 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import({QueryDslConfig.class, SettlementQueryRepositoryImpl.class, JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SettlementQueryRepositoryTest {

    @Autowired
    private SettlementQueryRepository settlementQueryRepository;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private UserAssetRepository userAssetRepository;

    @Test
    @DisplayName("오늘 확정한 투자는 제외하고 정산일이 지난 최근 투자와 총자산을 함께 조회한다")
    void findRecentDueSettlement_returnsLatestDueInvestment() {
        UserAsset asset = userAssetRepository.save(UserAsset.create(1L, 1_045_000L));
        LocalDate today = LocalDate.now();
        persistSettledInvestment(1L, asset.getId(), today.minusDays(1));
        persistConfirmedInvestment(1L, asset.getId(), today); // 오늘 확정(아직 정산 대상 아님) → 제외

        Optional<SettlementResultProjection> result = settlementQueryRepository.findRecentDueSettlement(1L, today);

        assertTrue(result.isPresent());
        SettlementResultProjection projection = result.get();
        assertEquals(SettlementStatus.SETTLED, projection.settlementStatus());
        assertEquals(today.minusDays(1), projection.investDate());
        assertEquals(1_000_000L, projection.totalAmount());
        assertEquals(45_000L, projection.totalProfitLoss());
        assertEquals(1_045_000L, projection.totalAsset());
    }

    @Test
    @DisplayName("정산일이 지난 투자가 없으면(오늘 확정만 있으면) 빈 결과를 반환한다")
    void findRecentDueSettlement_emptyWhenOnlyTodayInvestment() {
        UserAsset asset = userAssetRepository.save(UserAsset.create(2L, 1_000_000L));
        LocalDate today = LocalDate.now();
        persistConfirmedInvestment(2L, asset.getId(), today);

        Optional<SettlementResultProjection> result = settlementQueryRepository.findRecentDueSettlement(2L, today);

        assertTrue(result.isEmpty());
    }

    private void persistSettledInvestment(long userId, Long userAssetId, LocalDate investDate) {
        Investment investment = Investment.confirm(userId, userAssetId, investDate);
        investment.addSector(100L, 10, 1_000_000L, BigDecimal.valueOf(20_000));
        investment.settleSector(100L, BigDecimal.valueOf(20_900)); // +4.5% → +45,000
        investment.settle(LocalDateTime.now());
        investmentRepository.save(investment);
    }

    private void persistConfirmedInvestment(long userId, Long userAssetId, LocalDate investDate) {
        Investment investment = Investment.confirm(userId, userAssetId, investDate);
        investment.addSector(100L, 10, 500_000L, BigDecimal.valueOf(10_000));
        investmentRepository.save(investment);
    }
}
