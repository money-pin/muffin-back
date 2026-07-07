package com.muffin.news.domain.term;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(
        name = "user_saved_term",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_user_saved_term_user_term",
                    columnNames = {"user_id", "term_id"})
        })
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSavedTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_saved_term_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    @CreatedDate
    @Column(name = "saved_at", nullable = false, updatable = false)
    private LocalDateTime savedAt;

    private UserSavedTerm(Long userId, Long termId) {
        this.userId = userId;
        this.termId = termId;
    }

    /** 사용자가 용어를 저장했을 때 저장 기록을 생성한다. */
    public static UserSavedTerm create(Long userId, Long termId) {
        return new UserSavedTerm(userId, termId);
    }
}
