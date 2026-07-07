package com.muffin.investment.domain.investment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 섹터를 포함한 Investment 애그리거트가 자식(InvestmentSector)까지 함께 저장/조회되는지 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
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
