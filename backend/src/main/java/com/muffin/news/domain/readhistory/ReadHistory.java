package com.muffin.news.domain.readhistory;

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

/**
 * 뉴스 열람 기록 애그리거트 루트. 사용자가 뉴스를 읽은 시점을 남긴다. 재열람 시 read_at 만 갱신한다(upsert).
 */
@Entity
@Getter
@Table(
        name = "read_history",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_read_history_user_news",
                        columnNames = {"user_id", "news_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "read_history_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "news_id", nullable = false)
    private Long newsId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt;

    private ReadHistory(Long userId, Long newsId) {
        this.userId = userId;
        this.newsId = newsId;
        this.readAt = LocalDateTime.now();
    }

    /** 열람 기록 생성. 열람 시각을 현재로 기록한다. */
    public static ReadHistory create(Long userId, Long newsId) {
        return new ReadHistory(userId, newsId);
    }

    /** 재열람 시 열람 시각을 현재로 갱신한다(뉴스별 1건 유지). */
    public void updateReadAt() {
        this.readAt = LocalDateTime.now();
    }
}
