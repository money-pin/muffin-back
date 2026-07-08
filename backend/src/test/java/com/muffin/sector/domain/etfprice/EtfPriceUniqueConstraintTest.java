package com.muffin.sector.domain.etfprice;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** 같은 ETF의 같은 날짜 시세 UNIQUE 제약을 실제 저장 시도로 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EtfPriceUniqueConstraintTest {

    @Autowired
    private EtfPriceRepository etfPriceRepository;

    @Test
    @DisplayName("같은 ETF의 같은 날짜 시세를 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void duplicateEtfAndPriceDate_violatesUnique() {
        LocalDate priceDate = LocalDate.of(2026, 7, 8);
        etfPriceRepository.saveAndFlush(EtfPrice.create(1L, priceDate, 10_000L, 10_500L));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> etfPriceRepository.saveAndFlush(EtfPrice.create(1L, priceDate, 10_500L, 10_800L)));
    }
}
