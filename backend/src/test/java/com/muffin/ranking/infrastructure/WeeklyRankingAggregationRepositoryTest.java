package com.muffin.ranking.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.global.config.QueryDslConfig;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.PriceDataSource;
import com.muffin.ranking.application.WeeklyRankingAggregationRepository;
import com.muffin.ranking.domain.weeklyranking.WeeklyRanking;
import com.muffin.ranking.domain.weeklyranking.WeeklyRankingCandidate;
import com.muffin.ranking.domain.weeklyranking.WeeklyRankingRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({QueryDslConfig.class, WeeklyRankingAggregationRepositoryImpl.class, JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WeeklyRankingAggregationRepositoryTest {

    private static final LocalDate WEEK_START_DATE = LocalDate.of(2026, 7, 6);
    private static final LocalDate WEEK_END_DATE = WEEK_START_DATE.plusDays(6);

    @Autowired
    private WeeklyRankingAggregationRepository aggregationRepository;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WeeklyRankingRepository weeklyRankingRepository;

    @Test
    @DisplayName("정산 완료된 활성 사용자 투자만 사용자별로 합산한다")
    void findSettledCandidates_aggregatesActiveSettledInvestmentsOnly() {
        User activeUser = saveUser("active", "11111111-2222-4333-8444-555555555555");
        User suspendedUser = saveUser("banned", "22222222-3333-4444-8555-666666666666");
        suspendedUser.suspend();
        persistSettledInvestment(activeUser, WEEK_START_DATE, 100_000L, 5_000L);
        persistSettledInvestment(activeUser, WEEK_START_DATE.plusDays(1), 200_000L, 8_000L);
        persistSettledInvestment(suspendedUser, WEEK_START_DATE, 500_000L, 50_000L);

        List<WeeklyRankingCandidate> candidates =
                aggregationRepository.findSettledCandidates(WEEK_START_DATE, WEEK_END_DATE);

        assertEquals(1, candidates.size());
        WeeklyRankingCandidate candidate = candidates.getFirst();
        assertEquals(activeUser.getUserId(), candidate.userId());
        assertEquals("active", candidate.nickname());
        assertEquals(300_000L, candidate.totalInvestment());
        assertEquals(13_000L, candidate.weeklyProfit());
    }

    @Test
    @DisplayName("확정 투자 중 PENDING 또는 FAILED가 있으면 랭킹 집계를 보류하고, 스냅샷 존재 여부를 조회한다")
    void hasUnsettledConfirmedInvestment_andExistsByWeekStartDate() {
        User user = saveUser("ranker", "33333333-4444-4555-8666-777777777777");
        Investment pending = Investment.confirm(user.getUserId(), 1L, WEEK_START_DATE);
        pending.addSector(1L, 1, 100_000L, BigDecimal.valueOf(100));
        investmentRepository.saveAndFlush(pending);

        assertTrue(aggregationRepository.hasUnsettledConfirmedInvestment(WEEK_START_DATE, WEEK_END_DATE));
        assertFalse(aggregationRepository.existsByWeekStartDate(WEEK_START_DATE));

        weeklyRankingRepository.saveAndFlush(
                WeeklyRanking.create(user.getUserId(), "ranker", 1, 0L, BigDecimal.ZERO, 100, WEEK_START_DATE, 28));

        assertTrue(aggregationRepository.existsByWeekStartDate(WEEK_START_DATE));
    }

    private User saveUser(String nickname, String uuid) {
        return userRepository.saveAndFlush(User.register(1L, uuid, "name", nickname));
    }

    private void persistSettledInvestment(User user, LocalDate investDate, long totalAmount, long totalProfitLoss) {
        Investment investment = Investment.confirm(user.getUserId(), 1L, investDate);
        investment.addSector(1L, 1, totalAmount, BigDecimal.valueOf(100));
        investment.applySectorResult(
                1L, BigDecimal.valueOf(110), totalProfitLoss, BigDecimal.ZERO, PriceDataSource.NORMAL);
        investment.settle(LocalDateTime.of(2026, 7, 14, 9, 30));
        investmentRepository.saveAndFlush(investment);
    }
}
