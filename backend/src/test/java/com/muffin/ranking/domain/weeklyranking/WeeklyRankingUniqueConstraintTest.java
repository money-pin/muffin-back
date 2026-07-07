package com.muffin.ranking.domain.weeklyranking;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.global.config.JpaAuditingConfig;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** 같은 사용자-주차 조합의 주간 랭킹 UNIQUE 제약을 실제 저장 시도로 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WeeklyRankingUniqueConstraintTest {

    @Autowired
    private WeeklyRankingRepository weeklyRankingRepository;

    @Test
    @DisplayName("같은 사용자-주차로 주간 랭킹을 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void duplicateUserAndWeekOfYear_violatesUnique() {
        weeklyRankingRepository.saveAndFlush(
                WeeklyRanking.create(1L, "muffin", 3, 120_000L, BigDecimal.valueOf(4.5), 10, 27));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> weeklyRankingRepository.saveAndFlush(
                        WeeklyRanking.create(1L, "muffin", 5, 80_000L, BigDecimal.valueOf(2.0), 20, 27)));
    }
}
