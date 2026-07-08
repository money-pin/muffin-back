package com.muffin.quiz.domain.quizsession;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자 퀴즈 답안 제출 기록. QuizSession 애그리거트 내부에서 생성된다. */
@Entity
@Getter
@Table(
        name = "quiz_attempt",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_quiz_attempt_session_quiz",
                        columnNames = {"quiz_session_id", "quiz_id"}))
// JPA 전용 기본 생성자. 제출 기록은 세션에 속한 값이라 직접 생성을 막는다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id")
    private Long id;

    // 다른 애그리거트(Quiz, QuizOption)는 ID로만 참조한다.
    @Column(name = "quiz_id", nullable = false)
    private Long quizId;

    @Column(name = "option_id", nullable = false)
    private Long optionId;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    // 같은 패키지의 QuizSession에서 제출 기록을 만들 때 쓰는 생성자다.
    QuizAttempt(Long quizId, Long optionId, boolean correct, LocalDateTime submittedAt) {
        this.quizId = quizId;
        this.optionId = optionId;
        this.correct = correct;
        this.submittedAt = submittedAt;
    }
}
