package com.muffin.news.domain.news;

import com.muffin.global.entity.BaseEntity;
import com.muffin.news.domain.news.enums.NewsStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "news",
        uniqueConstraints = {@UniqueConstraint(name = "uk_news_original_url", columnNames = "original_url")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class News extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_id")
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "publisher", nullable = false, length = 20)
    private String publisher;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "original_url", nullable = false, length = 1000)
    private String originalUrl;

    @Lob
    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NewsStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "news_id", nullable = false)
    private final List<NewsTerm> terms = new ArrayList<>();

    private News(
            Long categoryId,
            String title,
            String publisher,
            LocalDateTime publishedAt,
            String thumbnailUrl,
            String originalUrl,
            String content,
            NewsStatus status) {
        this.categoryId = categoryId;
        this.title = title;
        this.publisher = publisher;
        this.publishedAt = publishedAt;
        this.thumbnailUrl = thumbnailUrl;
        this.originalUrl = originalUrl;
        this.content = content;
        this.status = status;
    }

    /** 발행 대기 상태의 뉴스 레코드를 생성한다. */
    public static News create(
            Long categoryId,
            String title,
            String publisher,
            LocalDateTime publishedAt,
            String thumbnailUrl,
            String originalUrl,
            String content) {
        return pending(categoryId, title, publisher, publishedAt, thumbnailUrl, originalUrl, content);
    }

    /** 외부 뉴스 수집 후 아직 발행 처리되지 않은 뉴스 레코드를 생성한다. */
    public static News pending(
            Long categoryId,
            String title,
            String publisher,
            LocalDateTime publishedAt,
            String thumbnailUrl,
            String originalUrl,
            String content) {
        return new News(
                categoryId, title, publisher, publishedAt, thumbnailUrl, originalUrl, content, NewsStatus.PENDING);
    }

    /** 뉴스 발행 처리가 완료되었을 때 상태를 변경한다. */
    public void publish() {
        this.status = NewsStatus.PUBLISHED;
    }

    /** 뉴스 발행 또는 처리 과정이 실패했을 때 상태를 변경한다. */
    public void fail() {
        this.status = NewsStatus.FAILED;
    }

    /** 뉴스에 포함된 용어를 루트를 통해 추가한다. */
    public void addTerm(Long termId) {
        this.terms.add(NewsTerm.create(termId));
    }

    /** 뉴스에 연결된 용어 목록을 읽기 전용으로 반환한다. */
    public List<NewsTerm> getTerms() {
        return Collections.unmodifiableList(terms);
    }

    /** 뉴스 조회 시 조회수를 1 증가시킨다. */
    public void increaseViewCount() {
        this.viewCount++;
    }

    /** 뉴스를 소프트 삭제 처리한다. 이미 삭제된 뉴스라면 삭제 시간을 유지한다. */
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = LocalDateTime.now();
        }
    }
}
