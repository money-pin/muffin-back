package com.muffin.briefing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 브리핑 열람 기록. 재열람 시 {@code viewed_at}만 갱신한다(upsert). {@code read_history}가 쓰는 방식과 같다. */
@Entity
@Getter
@Table(
        name = "briefing_view",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_briefing_view_user_date",
                        columnNames = {"user_id", "briefing_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BriefingView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "briefing_view_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "briefing_date", nullable = false)
    private LocalDate briefingDate;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    private BriefingView(Long userId, LocalDate briefingDate, LocalDateTime viewedAt) {
        this.userId = userId;
        this.briefingDate = briefingDate;
        this.viewedAt = viewedAt;
    }

    /** 사용자가 브리핑을 처음 열었을 때의 기록을 만든다. */
    public static BriefingView create(Long userId, LocalDate briefingDate, LocalDateTime viewedAt) {
        if (userId == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        if (briefingDate == null) {
            throw new IllegalArgumentException("briefingDate는 필수입니다.");
        }
        if (viewedAt == null) {
            throw new IllegalArgumentException("viewedAt은 필수입니다.");
        }
        return new BriefingView(userId, briefingDate, viewedAt);
    }

    /** 다시 열람하면 마지막 열람 시각만 갱신한다. */
    public void renew(LocalDateTime viewedAt) {
        if (viewedAt == null) {
            throw new IllegalArgumentException("viewedAt은 필수입니다.");
        }
        this.viewedAt = viewedAt;
    }
}
