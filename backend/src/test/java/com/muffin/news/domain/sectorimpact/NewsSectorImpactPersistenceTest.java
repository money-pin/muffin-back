package com.muffin.news.domain.sectorimpact;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.news.domain.sectorimpact.enums.ImpactType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** 뉴스-섹터별 영향도는 한 건만 저장되는지 유니크 제약으로 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NewsSectorImpactPersistenceTest {

    @Autowired
    private NewsSectorImpactRepository newsSectorImpactRepository;

    @Test
    @DisplayName("같은 뉴스-섹터 영향도를 두 번 저장하면 유니크 제약 위반이 발생한다")
    void newsSectorImpact_duplicateNewsAndSector_violatesUnique() {
        newsSectorImpactRepository.saveAndFlush(NewsSectorImpact.create(1L, 10L, ImpactType.POSITIVE));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> newsSectorImpactRepository.saveAndFlush(NewsSectorImpact.create(1L, 10L, ImpactType.NEGATIVE)));
    }
}
