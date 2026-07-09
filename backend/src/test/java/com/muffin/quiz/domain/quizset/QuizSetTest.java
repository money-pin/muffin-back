package com.muffin.quiz.domain.quizset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.quiz.domain.quizset.enums.QuizDifficulty;
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
    @DisplayName("퀴즈 세트에 문제를 추가하면 양방향 연관관계가 설정된다")
    void addQuiz_setsBidirectionalRelation() {
        QuizSet quizSet = QuizSet.create(LocalDate.of(2026, 5, 7));

        Quiz quiz = quizSet.addQuiz(1L, "질문", "해설", 100L, 1, "근거 문장", QuizDifficulty.EASY);

        assertEquals(1, quizSet.getQuizzes().size());
        assertEquals(quizSet, quiz.getQuizSet());
    }

    @Test
    @DisplayName("퀴즈 추가 시 필수값이 없거나 숫자 값이 음수면 예외가 발생한다")
    void addQuiz_throwsWhenInvalidInput() {
        QuizSet quizSet = QuizSet.create(LocalDate.of(2026, 5, 7));

        assertThrows(
                IllegalArgumentException.class,
                () -> quizSet.addQuiz(null, "질문", "해설", 100L, 1, "근거 문장", QuizDifficulty.EASY));
        assertThrows(
                IllegalArgumentException.class,
                () -> quizSet.addQuiz(1L, " ", "해설", 100L, 1, "근거 문장", QuizDifficulty.EASY));
        assertThrows(
                IllegalArgumentException.class,
                () -> quizSet.addQuiz(1L, "질문", " ", 100L, 1, "근거 문장", QuizDifficulty.EASY));
        assertThrows(
                IllegalArgumentException.class,
                () -> quizSet.addQuiz(1L, "질문", "해설", null, 1, "근거 문장", QuizDifficulty.EASY));
        assertThrows(
                IllegalArgumentException.class,
                () -> quizSet.addQuiz(1L, "질문", "해설", -1L, 1, "근거 문장", QuizDifficulty.EASY));
        assertThrows(
                IllegalArgumentException.class,
                () -> quizSet.addQuiz(1L, "질문", "해설", 100L, -1, "근거 문장", QuizDifficulty.EASY));
        assertThrows(
                IllegalArgumentException.class,
                () -> quizSet.addQuiz(1L, "질문", "해설", 100L, 1, " ", QuizDifficulty.EASY));
        assertThrows(IllegalArgumentException.class, () -> quizSet.addQuiz(1L, "질문", "해설", 100L, 1, "근거 문장", null));
    }

    @Test
    @DisplayName("퀴즈 목록은 외부에서 직접 수정할 수 없다")
    void getQuizzes_returnsUnmodifiableView() {
        QuizSet quizSet = QuizSet.create(LocalDate.of(2026, 5, 7));

        List<Quiz> quizzes = quizSet.getQuizzes();

        assertThrows(UnsupportedOperationException.class, () -> quizzes.add(null));
    }
}
