package com.muffin.scrap.domain;

import com.muffin.global.entity.BaseEntity;
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
 * 뉴스 스크랩 애그리거트 루트. 사용자가 특정 뉴스를 저장한 기록.
 */
@Entity
@Getter
@Table(
        name = "scrap",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_scrap_user_news",
                        columnNames = {"user_id", "news_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scrap extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scrap_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "news_id", nullable = false)
    private Long newsId;

    @Column(name = "saved_at", nullable = false)
    private LocalDateTime savedAt;

    private Scrap(Long userId, Long newsId) {
        this.userId = userId;
        this.newsId = newsId;
        this.savedAt = LocalDateTime.now();
    }

    /** 스크랩 생성. 저장 시각을 현재로 기록한다. */
    public static Scrap create(Long userId, Long newsId) {
        return new Scrap(userId, newsId);
    }
}
