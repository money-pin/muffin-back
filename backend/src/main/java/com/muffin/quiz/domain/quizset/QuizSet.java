package com.muffin.quiz.domain.quizset;

import com.muffin.global.entity.BaseEntity;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 일일 퀴즈 세트 애그리거트 루트. 하루치 문제와 선택지를 함께 생성/발행한다. */
@Entity
@Getter
@Table(name = "quiz_set", uniqueConstraints = @UniqueConstraint(name = "uk_quiz_set_date", columnNames = "quiz_date"))
// JPA가 엔티티를 조회할 때 기본 생성자가 필요해서 protected로 열어둔다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_quiz_set_id")
    private Long id;

    @Column(name = "quiz_date", nullable = false)
    private LocalDate quizDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private QuizSetStatus status;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "daily_quiz_set_id", nullable = false)
    private final List<Quiz> quizzes = new ArrayList<>();

    // 새 퀴즈 세트를 만들 때 필요한 값만 받는 생성자다.
    private QuizSet(LocalDate quizDate) {
        this.quizDate = quizDate;
        this.status = QuizSetStatus.GENERATING;
    }

    /** 일일 퀴즈 세트 생성. 처음에는 AI 생성 중 상태(GENERATING)로 시작한다. */
    public static QuizSet create(LocalDate quizDate) {
        return new QuizSet(quizDate);
    }
}
