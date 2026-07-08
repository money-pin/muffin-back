package com.muffin.sector.domain.etf;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** ETF 코드 UNIQUE 제약을 실제 저장 시도로 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EtfUniqueConstraintTest {

    @Autowired
    private EtfRepository etfRepository;

    @Test
    @DisplayName("같은 ETF 코드를 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void duplicateEtfCode_violatesUnique() {
        etfRepository.saveAndFlush(Etf.create("091160", "KODEX 반도체"));

        assertThrows(
                DataIntegrityViolationException.class, () -> etfRepository.saveAndFlush(Etf.create("091160", "다른 이름")));
    }
}
