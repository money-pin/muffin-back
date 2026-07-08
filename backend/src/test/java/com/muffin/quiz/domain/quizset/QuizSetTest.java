package com.muffin.quiz.domain.quizset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuizSetTest {

    @Test
    @DisplayName("퀴즈 세트를 생성하면 GENERATING 상태가 된다")
    void create_setsGeneratingStatus() {
        QuizSet quizSet = QuizSet.create(LocalDate.of(2026, 5, 7));

        assertEquals(QuizSetStatus.GENERATING, quizSet.getStatus());
        assertEquals(LocalDate.of(2026, 5, 7), quizSet.getQuizDate());
    }

    @Test
    @DisplayName("퀴즈 목록은 외부에서 직접 수정할 수 없다")
    void getQuizzes_returnsUnmodifiableView() {
        QuizSet quizSet = QuizSet.create(LocalDate.of(2026, 5, 7));

        List<Quiz> quizzes = quizSet.getQuizzes();

        assertThrows(UnsupportedOperationException.class, () -> quizzes.add(null));
    }
}
