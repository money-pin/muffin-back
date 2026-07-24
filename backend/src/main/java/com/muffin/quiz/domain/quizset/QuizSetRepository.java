package com.muffin.quiz.domain.quizset;

import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** QuizSet 애그리거트 리포지토리 */
public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {

    Optional<QuizSet> findByQuizDate(LocalDate quizDate);

    Optional<QuizSet> findByQuizDateAndStatus(LocalDate quizDate, QuizSetStatus status);
}
