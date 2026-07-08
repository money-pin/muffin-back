package com.muffin.quiz.domain.quizset;

import com.muffin.quiz.domain.quizset.enums.QuizDifficulty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 일일 퀴즈 세트에 포함되는 개별 문제. QuizSet 애그리거트 내부 엔티티다. */
@Entity
@Getter
@Table(
        name = "quiz",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_quiz_set_order",
                        columnNames = {"daily_quiz_set_id", "quiz_order"}))
@EntityListeners(AuditingEntityListener.class)
// JPA 전용 기본 생성자. 외부에서 임의로 new Quiz() 하지 못하도록 protected로 제한한다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_id")
    private Long id;

    // 다른 애그리거트(News)는 ID로만 참조한다.
    @Column(name = "news_id", nullable = false)
    private Long newsId;

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question;

    @Column(name = "explanation", nullable = false, columnDefinition = "text")
    private String explanation;

    @Column(name = "reward_money", nullable = false)
    private Long rewardMoney;

    @Column(name = "quiz_order", nullable = false)
    private int quizOrder;

    @Column(name = "source_sentence", nullable = false, columnDefinition = "text")
    private String sourceSentence;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 10)
    private QuizDifficulty difficulty;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "quiz_id", nullable = false)
    private final List<QuizOption> options = new ArrayList<>();

    // 같은 패키지의 QuizSet에서 문제를 만들 때 쓰는 생성자다.
    Quiz(
            Long newsId,
            String question,
            String explanation,
            Long rewardMoney,
            int quizOrder,
            String sourceSentence,
            QuizDifficulty difficulty) {
        this.newsId = newsId;
        this.question = question;
        this.explanation = explanation;
        this.rewardMoney = rewardMoney;
        this.quizOrder = quizOrder;
        this.sourceSentence = sourceSentence;
        this.difficulty = difficulty;
    }
}
