package com.muffin.quiz.domain.quizsession;

import com.muffin.global.entity.BaseEntity;
import com.muffin.quiz.domain.quizsession.enums.QuizSessionStatus;
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

/** 사용자별 일일 퀴즈 풀이 세션 애그리거트 루트. 제출 기록과 세션 결과를 함께 관리한다. */
@Entity
@Getter
@Table(
        name = "quiz_session",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_quiz_session_user_quiz_set",
                        columnNames = {"user_id", "daily_quiz_set_id"}))
// JPA가 엔티티를 조회할 때 기본 생성자가 필요해서 protected로 열어둔다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_session_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 다른 애그리거트(QuizSet)는 ID로만 참조한다.
    @Column(name = "daily_quiz_set_id", nullable = false)
    private Long dailyQuizSetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private QuizSessionStatus status;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "solved_count", nullable = false)
    private int solvedCount;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    @Column(name = "reward_money", nullable = false)
    private Long rewardMoney;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "reward_claimed", nullable = false)
    private boolean rewardClaimed;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "quizSession", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<QuizAttempt> attempts = new ArrayList<>();

    // 세션 시작 시 필요한 값과 기본 진행 상태를 한 번에 세팅한다.
    private QuizSession(Long userId, Long dailyQuizSetId, LocalDate date, int totalCount, LocalDateTime startedAt) {
        this.userId = userId;
        this.dailyQuizSetId = dailyQuizSetId;
        this.date = date;
        this.totalCount = totalCount;
        this.status = QuizSessionStatus.PROGRESS;
        this.solvedCount = 0;
        this.correctCount = 0;
        this.rewardMoney = 0L;
        this.rewardClaimed = false;
        this.startedAt = startedAt;
    }

    /** 사용자가 당일 퀴즈를 시작할 때 풀이 세션을 생성한다. */
    public static QuizSession start(Long userId, Long dailyQuizSetId, LocalDate date, int totalCount) {
        if (userId == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        if (dailyQuizSetId == null) {
            throw new IllegalArgumentException("dailyQuizSetId는 필수입니다.");
        }
        if (date == null) {
            throw new IllegalArgumentException("date는 필수입니다.");
        }
        if (totalCount <= 0) {
            throw new IllegalArgumentException("totalCount는 1 이상이어야 합니다.");
        }
        return new QuizSession(userId, dailyQuizSetId, date, totalCount, LocalDateTime.now());
    }

    /** 같은 세션에서 해당 문제에 이미 제출한 기록이 있으면 반환한다. */
    public Optional<QuizAttempt> findAttemptByQuizId(Long quizId) {
        return attempts.stream()
                .filter(attempt -> attempt.getQuizId().equals(quizId))
                .findFirst();
    }

    /** 제출 기록을 추가하고 풀이 수, 정답 수, 보상 합계를 함께 갱신한다. */
    public QuizAttempt recordAttempt(
            Long quizId, Long optionId, boolean correct, Long rewardMoney, LocalDateTime submittedAt) {
        if (this.solvedCount >= this.totalCount) {
            throw new IllegalStateException("이미 모든 문제를 풀었습니다.");
        }
        if (correct && rewardMoney == null) {
            throw new IllegalArgumentException("정답인 경우 rewardMoney는 필수입니다.");
        }

        QuizAttempt attempt = new QuizAttempt(this, quizId, optionId, correct, submittedAt);

        this.attempts.add(attempt);
        this.solvedCount++;
        if (correct) {
            this.correctCount++;
            this.rewardMoney += rewardMoney;
        }

        if (this.solvedCount >= this.totalCount) {
            this.status = QuizSessionStatus.FINISHED;
            this.completedAt = submittedAt;
        }

        return attempt;
    }

    /** 완료된 세션의 보상을 1회만 확정한다. 1문항 이하 정답은 명세에 따라 보상을 0원으로 처리한다. */
    public Long claimReward() {
        if (this.status != QuizSessionStatus.FINISHED) {
            throw new IllegalStateException("완료된 퀴즈 세션만 보상을 지급할 수 있습니다.");
        }
        if (this.rewardClaimed) {
            return 0L;
        }

        if (this.correctCount <= 1) {
            this.rewardMoney = 0L;
        }
        this.rewardClaimed = true;
        return this.rewardMoney;
    }

    /** 내부 리스트가 외부에서 직접 수정되지 않도록 읽기 전용 뷰를 반환한다. */
    public List<QuizAttempt> getAttempts() {
        return Collections.unmodifiableList(attempts);
    }
}
