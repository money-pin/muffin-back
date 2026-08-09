package com.muffin.ranking.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.muffin.character.domain.characterprofile.CharacterProfile;
import com.muffin.character.domain.characterprofile.CharacterRepository;
import com.muffin.character.domain.enums.MuffinType;
import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.global.config.QueryDslConfig;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.investment.enums.PriceDataSource;
import com.muffin.ranking.application.WeeklyRankingQueryRepository;
import com.muffin.ranking.application.projection.Top10RankingProjection;
import com.muffin.ranking.application.projection.WeeklyInvestmentProjection;
import com.muffin.ranking.application.projection.WeeklyRankingProjection;
import com.muffin.ranking.application.projection.WeeklySectorProjection;
import com.muffin.ranking.domain.weeklyranking.WeeklyRanking;
import com.muffin.ranking.domain.weeklyranking.WeeklyRankingRepository;
import com.muffin.sector.domain.sector.Sector;
import com.muffin.sector.domain.sector.SectorRepository;
import com.muffin.user.domain.User;
import com.muffin.user.domain.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({QueryDslConfig.class, WeeklyRankingQueryRepositoryImpl.class, JpaAuditingConfig.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WeeklyRankingQueryRepositoryTest {

    private static final LocalDate WEEK_START_DATE = LocalDate.of(2026, 7, 20);
    private static final LocalDate WEEK_END_DATE = WEEK_START_DATE.plusDays(6);

    @Autowired
    private WeeklyRankingQueryRepository weeklyRankingQueryRepository;

    @Autowired
    private WeeklyRankingRepository weeklyRankingRepository;

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private SectorRepository sectorRepository;

    @Test
    @DisplayName("지난주 스냅샷에서 TOP 10과 TOP 10 밖 사용자의 내 순위를 각각 조회한다")
    void findTop10AndMyRank_readsSnapshotByWeek() {
        CharacterProfile character = characterRepository.saveAndFlush(
                CharacterProfile.create(MuffinType.PLAIN, "플레인 머핀", "설명", "plain.png"));
        User first = saveUser(character.getCharacterId(), "first", "1111-2222-3333-4444");
        User second = saveUser(null, "second", "2222-2222-3333-4444");
        User outsideTop10 = saveUser("outer", "3333-2222-3333-4444");
        saveRanking(first, 1, 1_000L, 1);
        saveRanking(second, 2, 500L, 2);
        saveRanking(outsideTop10, 11, 100L, 11);

        List<Top10RankingProjection> top10 = weeklyRankingQueryRepository.findTop10(WEEK_START_DATE);
        WeeklyRankingProjection myRank = weeklyRankingQueryRepository
                .findMyRank(outsideTop10.getUserId(), WEEK_START_DATE)
                .orElseThrow();

        assertEquals(
                List.of(1, 2),
                top10.stream().map(Top10RankingProjection::rankingPosition).toList());
        assertEquals(character.getCharacterId(), top10.getFirst().characterId());
        assertEquals(MuffinType.PLAIN, top10.getFirst().characterType());
        assertEquals("plain.png", top10.getFirst().characterImageUrl());
        assertNull(top10.get(1).characterId());
        assertEquals(11, myRank.rankingPosition());
        assertTrue(weeklyRankingQueryRepository.existsSnapshot(WEEK_START_DATE));
    }

    @Test
    @DisplayName("TOP 10의 정산 완료 투자와 섹터 상세를 사용자별로 일괄 집계한다")
    void findWeeklyInvestmentsAndSectors_aggregatesSettledConfirmedOnly() {
        User first = saveUser("invest", "4444-2222-3333-4444");
        User second = saveUser("second", "5555-2222-3333-4444");
        Long gold = saveSector("GOLD", "금");
        Long tech = saveSector("TECH", "테크");
        persistSettledInvestment(
                first, WEEK_START_DATE, Map.of(gold, amount(100_000L, 5_000L), tech, amount(200_000L, 8_000L)));
        persistSettledInvestment(second, WEEK_START_DATE.plusDays(1), Map.of(gold, amount(150_000L, 3_000L)));
        persistPendingInvestment(first, WEEK_START_DATE.plusDays(2), gold);

        List<WeeklyInvestmentProjection> investments = weeklyRankingQueryRepository.findWeeklyInvestments(
                List.of(first.getUserId(), second.getUserId()), WEEK_START_DATE, WEEK_END_DATE);
        List<WeeklySectorProjection> sectors = weeklyRankingQueryRepository.findWeeklySectors(
                List.of(first.getUserId(), second.getUserId()), WEEK_START_DATE, WEEK_END_DATE);
        Map<Long, Long> investmentByUser = investments.stream()
                .collect(Collectors.toMap(
                        WeeklyInvestmentProjection::userId, WeeklyInvestmentProjection::totalInvestment));

        assertEquals(300_000L, investmentByUser.get(first.getUserId()));
        assertEquals(150_000L, investmentByUser.get(second.getUserId()));
        assertEquals(3, sectors.size());
        assertTrue(sectors.stream()
                .anyMatch(s -> s.userId().equals(first.getUserId())
                        && s.sectorCode().equals("TECH")
                        && s.profitAmount().equals(8_000L)));
    }

    @Test
    @DisplayName("스냅샷이 없고 미정산 확정 투자 또는 활성 사용자의 정산 완료 투자가 있으면 집계 중 대상이다")
    void hasCalculatingTarget_identifiesPendingTarget() {
        User user = saveUser("calcus", "6666-2222-3333-4444");
        Long gold = saveSector("GOLD", "금");

        assertFalse(weeklyRankingQueryRepository.hasCalculatingTarget(WEEK_START_DATE, WEEK_END_DATE));

        persistPendingInvestment(user, WEEK_START_DATE, gold);
        assertTrue(weeklyRankingQueryRepository.hasCalculatingTarget(WEEK_START_DATE, WEEK_END_DATE));
    }

    @Test
    @DisplayName("활성 사용자의 정산 완료 투자가 있으면 집계 중 대상이다")
    void hasCalculatingTarget_identifiesSettledTargetOfActiveUser() {
        User user = saveUser("settle", "7777-2222-3333-4444");
        Long gold = saveSector("GOLD", "금");

        persistSettledInvestment(user, WEEK_START_DATE, Map.of(gold, amount(100_000L, 5_000L)));

        assertTrue(weeklyRankingQueryRepository.hasCalculatingTarget(WEEK_START_DATE, WEEK_END_DATE));
    }

    @Test
    @DisplayName("탈퇴 사용자의 투자는 집계 중 대상으로 보지 않는다")
    void hasCalculatingTarget_ignoresWithdrawnUser() {
        User user = saveUser("wdrawn", "8888-2222-3333-4444");
        Long gold = saveSector("GOLD", "금");
        user.withdraw();
        userRepository.saveAndFlush(user);
        persistPendingInvestment(user, WEEK_START_DATE, gold);

        assertFalse(weeklyRankingQueryRepository.hasCalculatingTarget(WEEK_START_DATE, WEEK_END_DATE));
    }

    private User saveUser(String nickname, String uuid) {
        return saveUser(null, nickname, uuid);
    }

    private User saveUser(Long characterId, String nickname, String uuid) {
        return userRepository.saveAndFlush(User.register(characterId, uuid, "name", nickname));
    }

    private Long saveSector(String sectorCode, String name) {
        return sectorRepository
                .saveAndFlush(Sector.create(1L, 1L, name, null, sectorCode, 1))
                .getId();
    }

    private void saveRanking(User user, int position, long profit, int percentile) {
        weeklyRankingRepository.saveAndFlush(WeeklyRanking.create(
                user.getUserId(),
                user.getNickname(),
                position,
                profit,
                BigDecimal.ONE,
                percentile,
                WEEK_START_DATE,
                30));
    }

    private void persistSettledInvestment(User user, LocalDate investDate, Map<Long, SectorAmount> sectors) {
        Investment investment = Investment.confirm(user.getUserId(), investDate);
        sectors.forEach((sectorId, amount) ->
                investment.addSector(sectorId, 1, amount.totalInvestment(), BigDecimal.valueOf(100)));
        sectors.forEach((sectorId, amount) -> investment.applySectorResult(
                sectorId, BigDecimal.valueOf(110), amount.profitAmount(), BigDecimal.ZERO, PriceDataSource.NORMAL));
        investment.settle(LocalDateTime.of(2026, 7, 27, 9, 30));
        investmentRepository.saveAndFlush(investment);
    }

    private void persistPendingInvestment(User user, LocalDate investDate, Long sectorId) {
        Investment investment = Investment.confirm(user.getUserId(), investDate);
        investment.addSector(sectorId, 1, 100_000L, BigDecimal.valueOf(100));
        investmentRepository.saveAndFlush(investment);
    }

    private SectorAmount amount(long totalInvestment, long profitAmount) {
        return new SectorAmount(totalInvestment, profitAmount);
    }

    private record SectorAmount(long totalInvestment, long profitAmount) {}
}
