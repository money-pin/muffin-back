package com.muffin.quiz.domain.quizset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.quiz.domain.quizset.enums.QuizDifficulty;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Test
    @DisplayName("3문항이 모두 생성된 퀴즈 세트는 발행 대기 상태가 된다")
    void ready_setsReadyStatusWhenThreeQuizzesExist() {
        QuizSet quizSet = createQuizSetWithThreeQuizzes();

        quizSet.ready();

        assertEquals(QuizSetStatus.READY, quizSet.getStatus());
    }

    @Test
    @DisplayName("3문항이 모두 생성되지 않으면 발행 대기 상태로 변경할 수 없다")
    void ready_throwsWhenQuizCountIsNotThree() {
        QuizSet quizSet = QuizSet.create(LocalDate.of(2026, 5, 7));
        quizSet.addQuiz(1L, "질문", "해설", 100L, 1, "근거 문장", QuizDifficulty.EASY);

        assertThrows(IllegalStateException.class, quizSet::ready);
    }

    @Test
    @DisplayName("발행 대기 상태의 퀴즈 세트는 공개 상태가 되고 공개 시각이 기록된다")
    void publish_setsPublishedStatusAndPublishedAt() {
        QuizSet quizSet = createQuizSetWithThreeQuizzes();
        quizSet.ready();
        LocalDateTime publishedAt = LocalDateTime.of(2026, 5, 7, 10, 0);

        quizSet.publish(publishedAt);

        assertEquals(QuizSetStatus.PUBLISHED, quizSet.getStatus());
        assertEquals(publishedAt, quizSet.getPublishedAt());
    }

    @Test
    @DisplayName("생성 실패 시 이용 불가 상태로 변경한다")
    void unavailable_setsUnavailableStatus() {
        QuizSet quizSet = QuizSet.create(LocalDate.of(2026, 5, 7));

        quizSet.unavailable();

        assertEquals(QuizSetStatus.UNAVAILABLE, quizSet.getStatus());
    }

    @Test
    @DisplayName("이미 공개된 퀴즈 세트는 이용 불가 상태로 변경할 수 없다")
    void unavailable_throwsWhenAlreadyPublished() {
        QuizSet quizSet = createQuizSetWithThreeQuizzes();
        quizSet.ready();
        quizSet.publish(LocalDateTime.of(2026, 5, 7, 10, 0));

        assertThrows(IllegalStateException.class, quizSet::unavailable);
    }

    private QuizSet createQuizSetWithThreeQuizzes() {
        QuizSet quizSet = QuizSet.create(LocalDate.of(2026, 5, 7));
        quizSet.addQuiz(1L, "질문1", "해설1", 100L, 1, "근거 문장1", QuizDifficulty.EASY);
        quizSet.addQuiz(2L, "질문2", "해설2", 100L, 2, "근거 문장2", QuizDifficulty.EASY);
        quizSet.addQuiz(3L, "질문3", "해설3", 100L, 3, "근거 문장3", QuizDifficulty.MEDIUM);
        return quizSet;
    }
}
