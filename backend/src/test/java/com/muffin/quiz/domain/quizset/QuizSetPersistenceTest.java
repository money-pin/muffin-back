package com.muffin.quiz.domain.quizset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.global.config.JpaAuditingConfig;
import com.muffin.quiz.domain.quizset.enums.QuizDifficulty;
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
    @DisplayName("퀴즈 세트를 저장하면 포함된 문제도 함께 영속화된다")
    void saveQuizSetWithQuiz_persistsChildQuiz() {
        QuizSet quizSet = QuizSet.create(LocalDate.of(2026, 5, 7));
        quizSet.addQuiz(1L, "질문", "해설", 100L, 1, "근거 문장", QuizDifficulty.EASY);

        QuizSet saved = quizSetRepository.saveAndFlush(quizSet);

        assertNotNull(saved.getId());
        assertEquals(1, saved.getQuizzes().size());
        assertNotNull(saved.getQuizzes().getFirst().getId());
    }

    @Test
    @DisplayName("같은 날짜의 퀴즈 세트를 두 번 저장하면 유니크 제약 위반이 발생한다")
    void quizSet_duplicateDate_violatesUnique() {
        quizSetRepository.saveAndFlush(QuizSet.create(LocalDate.of(2026, 5, 7)));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> quizSetRepository.saveAndFlush(QuizSet.create(LocalDate.of(2026, 5, 7))));
    }
}
