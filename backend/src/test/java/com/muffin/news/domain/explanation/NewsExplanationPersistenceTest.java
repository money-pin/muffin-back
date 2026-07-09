package com.muffin.news.domain.explanation;

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

/** 뉴스별 같은 순서의 해설카드는 한 건만 저장되는지 유니크 제약으로 검증한다. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NewsExplanationPersistenceTest {

    @Autowired
    private NewsExplanationRepository newsExplanationRepository;

    @Test
    @DisplayName("같은 뉴스-카드순서 해설카드를 두 번 저장하면 유니크 제약 위반이 발생한다")
    void newsExplanation_duplicateNewsAndOrder_violatesUnique() {
        newsExplanationRepository.saveAndFlush(NewsExplanation.create(1L, 1, "금리", "금리 해설", "금리"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> newsExplanationRepository.saveAndFlush(NewsExplanation.create(1L, 1, "환율", "환율 해설", "환율")));
    }
}
