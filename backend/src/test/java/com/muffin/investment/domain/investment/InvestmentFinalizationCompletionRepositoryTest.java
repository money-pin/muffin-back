package com.muffin.investment.domain.investment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.muffin.global.config.JpaAuditingConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InvestmentFinalizationCompletionRepositoryTest {

    private static final LocalDate INVEST_DATE = LocalDate.of(2026, 7, 13);
    private static final LocalDateTime FINALIZED_AT = LocalDateTime.of(2026, 7, 14, 0, 0);

    @Autowired
    private InvestmentRepository investmentRepository;

    @Test
    @DisplayName("NO_INVEST와 매수가가 모두 채워진 확정 투자만 마감 완료 건수에 포함한다")
    void countCompletedFinalizations_countsOnlyCompleteRows() {
        Investment complete = Investment.confirm(1L, 11L, INVEST_DATE);
        complete.addSector(101L, 1, 100_000L, null);
        complete.finalizeInvestment(Map.of(101L, BigDecimal.valueOf(12_000)), FINALIZED_AT);

        Investment missingPrice = Investment.confirm(2L, 12L, INVEST_DATE);
        missingPrice.addSector(102L, 1, 100_000L, null);
        missingPrice.finalizeInvestment(Map.of(), FINALIZED_AT);

        Investment noInvest = Investment.noInvest(3L, 13L, INVEST_DATE);
        noInvest.finalizeNoInvest(FINALIZED_AT);

        investmentRepository.saveAllAndFlush(java.util.List.of(complete, missingPrice, noInvest));

        assertEquals(2L, investmentRepository.countCompletedFinalizationsByInvestDate(INVEST_DATE));
    }
}
