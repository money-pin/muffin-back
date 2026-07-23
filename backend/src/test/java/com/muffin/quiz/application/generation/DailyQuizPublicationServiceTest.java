package com.muffin.quiz.application.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.muffin.quiz.domain.quizset.QuizSet;
import com.muffin.quiz.domain.quizset.QuizSetRepository;
import com.muffin.quiz.domain.quizset.enums.QuizDifficulty;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DailyQuizPublicationServiceTest {

    private final QuizSetRepository quizSetRepository = mock(QuizSetRepository.class);
    private final DailyQuizPublicationService publicationService = new DailyQuizPublicationService(quizSetRepository);

    @Test
    @DisplayName("READY 상태의 퀴즈 세트를 PUBLISHED 상태로 전환한다")
    void publish_setsPublishedStatus() {
        LocalDate quizDate = LocalDate.of(2026, 7, 19);
        LocalDateTime publishedAt = LocalDateTime.of(2026, 7, 19, 10, 0);
        QuizSet quizSet = readyQuizSet(quizDate);

        when(quizSetRepository.findByQuizDateAndStatus(quizDate, QuizSetStatus.READY))
                .thenReturn(Optional.of(quizSet));

        boolean published = publicationService.publish(quizDate, publishedAt);

        assertThat(published).isTrue();
        assertThat(quizSet.getStatus()).isEqualTo(QuizSetStatus.PUBLISHED);
        assertThat(quizSet.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    @DisplayName("READY 상태의 퀴즈 세트가 없으면 발행하지 않는다")
    void publish_returnsFalseWhenReadyQuizSetDoesNotExist() {
        LocalDate quizDate = LocalDate.of(2026, 7, 19);
        when(quizSetRepository.findByQuizDateAndStatus(quizDate, QuizSetStatus.READY))
                .thenReturn(Optional.empty());

        boolean published = publicationService.publish(quizDate, LocalDateTime.of(2026, 7, 19, 10, 0));

        assertThat(published).isFalse();
    }

    private static QuizSet readyQuizSet(LocalDate quizDate) {
        QuizSet quizSet = QuizSet.create(quizDate);
        ReflectionTestUtils.setField(quizSet, "id", 1L);
        quizSet.addQuiz(1L, "질문1", "해설1", 100L, 1, "근거 문장1", QuizDifficulty.EASY);
        quizSet.addQuiz(2L, "질문2", "해설2", 100L, 2, "근거 문장2", QuizDifficulty.EASY);
        quizSet.addQuiz(3L, "질문3", "해설3", 100L, 3, "근거 문장3", QuizDifficulty.MEDIUM);
        quizSet.ready();
        return quizSet;
    }
}
