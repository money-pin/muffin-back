package com.muffin.sector.domain.sector;

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

/** 섹터 코드 UNIQUE 제약을 실제 저장 시도로 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SectorUniqueConstraintTest {

    @Autowired
    private SectorRepository sectorRepository;

    @Test
    @DisplayName("같은 섹터 코드를 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void duplicateSectorCode_violatesUnique() {
        sectorRepository.saveAndFlush(Sector.create(1L, 10L, "반도체", null, "SEMICONDUCTOR", 1));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> sectorRepository.saveAndFlush(Sector.create(2L, 20L, "다른섹터", null, "SEMICONDUCTOR", 1)));
    }
}
