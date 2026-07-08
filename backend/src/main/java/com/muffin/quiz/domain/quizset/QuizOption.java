package com.muffin.quiz.domain.quizset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 퀴즈 선택지. QuizSet 애그리거트 내부에서 Quiz를 통해 생성된다. */
@Entity
@Getter
@Table(name = "quiz_option")
@EntityListeners(AuditingEntityListener.class)
// JPA 전용 기본 생성자. 선택지는 퀴즈에 속한 값이라 직접 생성을 막는다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long id;

    @Column(name = "option_no", nullable = false)
    private int optionNo;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 같은 패키지의 Quiz에서 선택지를 만들 때 쓰는 생성자다.
    QuizOption(int optionNo, String content, boolean correct) {
        this.optionNo = optionNo;
        this.content = content;
        this.correct = correct;
    }
}
