package com.muffin.quiz.domain.quizsession;

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

/** QuizSession 사용자-퀴즈세트 유니크 제약 검증. */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QuizSessionPersistenceTest {

    @Autowired
    private QuizSessionRepository quizSessionRepository;

    @Test
    @DisplayName("같은 사용자-퀴즈세트로 세션을 두 번 저장하면 유니크 제약 위반이 발생한다")
    void quizSession_duplicateUserAndQuizSet_violatesUnique() {
        quizSessionRepository.saveAndFlush(QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 3));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> quizSessionRepository.saveAndFlush(QuizSession.start(1L, 10L, LocalDate.of(2026, 5, 7), 3)));
    }
}
