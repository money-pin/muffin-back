package com.muffin.quiz.domain.quizset;

import com.muffin.global.entity.BaseEntity;
import com.muffin.quiz.domain.quizset.enums.QuizDifficulty;
import com.muffin.quiz.domain.quizset.enums.QuizSetStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "quizSet", cascade = CascadeType.ALL, orphanRemoval = true)
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

    /** 퀴즈 세트에 문제를 추가하면서 양방향 연관관계를 함께 설정한다. */
    public Quiz addQuiz(
            Long newsId,
            String question,
            String explanation,
            Long rewardMoney,
            int quizOrder,
            String sourceSentence,
            QuizDifficulty difficulty) {
        validateQuiz(newsId, question, explanation, rewardMoney, quizOrder, sourceSentence, difficulty);
        Quiz quiz = new Quiz(this, newsId, question, explanation, rewardMoney, quizOrder, sourceSentence, difficulty);
        this.quizzes.add(quiz);
        return quiz;
    }

    /** 내부 리스트가 외부에서 직접 수정되지 않도록 읽기 전용 뷰를 반환한다. */
    public List<Quiz> getQuizzes() {
        return Collections.unmodifiableList(quizzes);
    }

    /** 오늘 퀴즈 세트에 속한 문제인지 확인하기 위해 내부 문제 목록에서 찾는다. */
    public Optional<Quiz> findQuiz(Long quizId) {
        return quizzes.stream().filter(quiz -> quiz.getId().equals(quizId)).findFirst();
    }

    /** AI가 하루치 3문항 생성을 마치면 발행 대기 상태로 전환한다. */
    public void ready() {
        if (status != QuizSetStatus.GENERATING) {
            throw new IllegalStateException("생성 중인 퀴즈 세트만 발행 대기 상태로 변경할 수 있습니다.");
        }
        if (quizzes.size() != 3) {
            throw new IllegalStateException("일일 퀴즈는 3문항이 모두 생성되어야 발행 대기 상태가 될 수 있습니다.");
        }
        this.status = QuizSetStatus.READY;
    }

    /** 발행 시각에 도달하면 사용자에게 공개되는 상태로 전환한다. */
    public void publish(LocalDateTime publishedAt) {
        if (status != QuizSetStatus.READY) {
            throw new IllegalStateException("발행 대기 상태의 퀴즈 세트만 공개할 수 있습니다.");
        }
        if (publishedAt == null) {
            throw new IllegalArgumentException("publishedAt은 필수입니다.");
        }
        this.status = QuizSetStatus.PUBLISHED;
        this.publishedAt = publishedAt;
    }

    /** 생성 실패 또는 발행 불가 상황이면 오늘의 퀴즈를 이용 불가 상태로 전환한다. */
    public void unavailable() {
        if (status == QuizSetStatus.PUBLISHED) {
            throw new IllegalStateException("이미 공개된 퀴즈 세트는 이용 불가 상태로 변경할 수 없습니다.");
        }
        this.status = QuizSetStatus.UNAVAILABLE;
    }

    private static void validateQuiz(
            Long newsId,
            String question,
            String explanation,
            Long rewardMoney,
            int quizOrder,
            String sourceSentence,
            QuizDifficulty difficulty) {
        if (newsId == null) {
            throw new IllegalArgumentException("newsId는 필수입니다.");
        }
        if (isBlank(question)) {
            throw new IllegalArgumentException("question은 필수입니다.");
        }
        if (isBlank(explanation)) {
            throw new IllegalArgumentException("explanation은 필수입니다.");
        }
        if (rewardMoney == null || rewardMoney < 0) {
            throw new IllegalArgumentException("rewardMoney는 0 이상이어야 합니다.");
        }
        if (quizOrder < 0) {
            throw new IllegalArgumentException("quizOrder는 음수일 수 없습니다.");
        }
        if (isBlank(sourceSentence)) {
            throw new IllegalArgumentException("sourceSentence는 필수입니다.");
        }
        if (difficulty == null) {
            throw new IllegalArgumentException("difficulty는 필수입니다.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
