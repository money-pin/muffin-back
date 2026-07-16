package com.muffin.sector.domain.sectorgroup;

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

/** 섹터 그룹 코드 UNIQUE 제약을 실제 저장 시도로 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SectorGroupUniqueConstraintTest {

    @Autowired
    private SectorGroupRepository sectorGroupRepository;

    @Test
    @DisplayName("같은 그룹 코드를 두 번 저장하면 UNIQUE 제약 위반이 발생한다")
    void duplicateGroupCode_violatesUnique() {
        sectorGroupRepository.saveAndFlush(SectorGroup.create("BASE_ASSET", "기초자산", null, 1));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> sectorGroupRepository.saveAndFlush(SectorGroup.create("BASE_ASSET", "다른그룹", null, 2)));
    }
}
