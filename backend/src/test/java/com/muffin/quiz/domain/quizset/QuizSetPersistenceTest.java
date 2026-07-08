package com.muffin.quiz.domain.quizset;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.global.config.JpaAuditingConfig;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** QuizSet 유니크 제약 검증. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QuizSetPersistenceTest {

    @Autowired
    private QuizSetRepository quizSetRepository;

    @Test
    @DisplayName("같은 날짜의 퀴즈 세트를 두 번 저장하면 유니크 제약 위반이 발생한다")
    void quizSet_duplicateDate_violatesUnique() {
        quizSetRepository.saveAndFlush(QuizSet.create(LocalDate.of(2026, 5, 7)));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> quizSetRepository.saveAndFlush(QuizSet.create(LocalDate.of(2026, 5, 7))));
    }
}
