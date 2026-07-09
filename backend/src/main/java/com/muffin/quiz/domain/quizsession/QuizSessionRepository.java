package com.muffin.quiz.domain.quizsession;

import org.springframework.data.jpa.repository.JpaRepository;

/** QuizSession 애그리거트 리포지토리 */
public interface QuizSessionRepository extends JpaRepository<QuizSession, Long> {}
