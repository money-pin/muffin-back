package com.muffin.quiz.domain.quizset;

import org.springframework.data.jpa.repository.JpaRepository;

/** QuizSet 애그리거트 리포지토리 */
public interface QuizSetRepository extends JpaRepository<QuizSet, Long> {}
