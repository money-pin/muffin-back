package com.muffin.investment.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.investment.domain.investment.Investment;
import com.muffin.investment.domain.investment.InvestmentRepository;
import com.muffin.investment.domain.profitsummary.ProfitSummary;
import com.muffin.investment.domain.profitsummary.ProfitSummaryRepository;
import com.muffin.investment.domain.userAsset.UserAsset;
import com.muffin.investment.domain.userAsset.UserAssetRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * 사용자 단위 유일성(사용자-투자일자, 사용자-요약일자, 사용자 1:1) DB 유니크 제약 검증. 도메인 순수 단위 테스트로는 확인할 수 없어 실제 저장을 시도한다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UniqueConstraintTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 7);

    @Autowired
    private InvestmentRepository investmentRepository;

    @Autowired
    private ProfitSummaryRepository profitSummaryRepository;

    @Autowired
    private UserAssetRepository userAssetRepository;

    @Test
    @DisplayName("같은 사용자-투자일자로 투자를 두 번 저장하면 유니크 제약 위반이 발생한다")
    void investment_duplicateUserAndInvestDate_violatesUnique() {
        investmentRepository.saveAndFlush(Investment.confirm(1L, 10L, DATE));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> investmentRepository.saveAndFlush(Investment.confirm(1L, 11L, DATE)));
    }

    @Test
    @DisplayName("같은 사용자-요약일자로 손익 요약을 두 번 저장하면 유니크 제약 위반이 발생한다")
    void profitSummary_duplicateUserAndSummaryDate_violatesUnique() {
        profitSummaryRepository.saveAndFlush(ProfitSummary.create(1L, DATE, 0L, BigDecimal.ZERO, 0L));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> profitSummaryRepository.saveAndFlush(ProfitSummary.create(1L, DATE, 100L, BigDecimal.ONE, 100L)));
    }

    @Test
    @DisplayName("같은 사용자로 자산을 두 번 저장하면 유니크 제약 위반이 발생한다")
    void userAsset_duplicateUser_violatesUnique() {
        userAssetRepository.saveAndFlush(UserAsset.create(1L, 1_000_000L));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> userAssetRepository.saveAndFlush(UserAsset.create(1L, 2_000_000L)));
    }
}
