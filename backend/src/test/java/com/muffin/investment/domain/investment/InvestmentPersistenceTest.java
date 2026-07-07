package com.muffin.investment.domain.investment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.muffin.global.config.JpaAuditingConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** 섹터를 포함한 Investment 애그리거트가 자식(InvestmentSector)까지 함께 저장/조회되는지 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InvestmentPersistenceTest {

    @Autowired
    private InvestmentRepository investmentRepository;

    @Test
    @DisplayName("섹터를 포함한 투자를 저장하면 자식 섹터까지 함께 영속화된다")
    void saveInvestmentWithSectors_persistsChildren() {
        Investment investment = Investment.confirm(1L, 10L, LocalDate.of(2026, 5, 7));
        investment.addSector(100L, 10, 300_000L, BigDecimal.valueOf(30_000));
        investment.addSector(200L, 5, 200_000L, BigDecimal.valueOf(40_000));

        Investment saved = investmentRepository.saveAndFlush(investment);

        assertNotNull(saved.getId());
        assertEquals(2, saved.getSectors().size());
        assertEquals(500_000L, saved.getTotalAmount());
    }
}
