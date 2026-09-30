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

    /**
     * RSS로 수집한 매경 원문 제목. 재구성 단계에서 {@link #title}은 AI 생성 제목으로 바뀌지만 이 값은 변하지 않는다. AI가 사실을
     * 왜곡한 제목을 만들었을 때 원문과 대조하고, 프롬프트를 고친 뒤 다시 생성할 때 원본 입력으로 쓰기 위해 남긴다. API 응답에는 노출하지 않는다.
     */
    @Column(name = "original_title", length = 255)
    private String originalTitle;

    @Column(name = "summary", length = 255)
    private String summary;

    @Column(name = "publisher", nullable = false, length = 20)
    private String publisher;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "original_url", nullable = false, length = 500)
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
            String summary,
            String publisher,
            LocalDateTime publishedAt,
            String thumbnailUrl,
            String originalUrl,
            String content,
            NewsStatus status) {
        this.categoryId = categoryId;
        this.title = title;
        // 수집 시점에는 title이 곧 원문 제목이다. 재구성이 title만 덮어쓰고 이 값은 그대로 둔다.
        this.originalTitle = title;
        this.summary = summary;
        this.publisher = publisher;
        this.publishedAt = publishedAt;
        this.thumbnailUrl = thumbnailUrl;
        this.originalUrl = originalUrl;
        this.content = content;
        this.status = status;
    }

    /** RSS로 수집한 뉴스를 AI 처리 중 상태로 생성한다. */
    public static News processing(
            Long categoryId,
            String title,
            String publisher,
            LocalDateTime publishedAt,
            String thumbnailUrl,
            String originalUrl) {
        return new News(
                categoryId,
                title,
                null,
                publisher,
                publishedAt,
                thumbnailUrl,
                originalUrl,
                null,
                NewsStatus.PROCESSING);
    }

    /** 발행 대기 중인 뉴스를 사용자에게 공개한다. */
    public void publish() {
        if (status != NewsStatus.PENDING) {
            throw new IllegalStateException("Only pending news can be published");
        }
        if (!hasReconstructionResult()) {
            throw new IllegalStateException("News cannot be published without reconstruction result");
        }
        this.status = NewsStatus.PUBLISHED;
    }

    /** 뉴스 요약과 재구성된 본문이 모두 저장되었는지 확인한다. */
    public boolean hasReconstructionResult() {
        return summary != null && !summary.isBlank() && content != null && !content.isBlank();
    }

    /** 뉴스 발행 또는 처리 과정이 실패했을 때 상태를 변경한다. */
    public void fail() {
        if (status == NewsStatus.PUBLISHED) {
            throw new IllegalStateException("Published news cannot be marked as failed");
        }
        this.status = NewsStatus.FAILED;
    }

    /** 뉴스에 포함된 용어를 루트를 통해 추가한다. 이미 연결된 용어라면 건너뛴다. */
    public boolean addTerm(Long termId) {
        if (termId == null) {
            throw new IllegalArgumentException("termId는 필수입니다.");
        }
        if (terms.stream().anyMatch(term -> term.getTermId().equals(termId))) {
            return false;
        }
        this.terms.add(NewsTerm.create(termId));
        return true;
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

    /** AI 생성 제목의 최대 길이. 재구성 프롬프트가 요구하는 30자와 같은 값이며, 넘으면 원문 제목을 유지한다. */
    private static final int MAX_AI_TITLE_LENGTH = 30;

    /**
     * AI 재구성 결과를 저장하고 정해진 발행 시각을 기다리는 상태로 변경한다.
     *
     * <p>제목은 쓸 수 있을 때만 교체한다. 제목 하나 때문에 뉴스를 실패 처리하면 그날 뉴스·해설카드·퀴즈·브리핑이 연쇄로 비는 손해가 더 크므로,
     * 제목이 비정상이면 원문 제목을 그대로 두고 재구성 자체는 정상 완료시킨다. 요약과 본문은 기존대로 엄격하게 검증한다.
     */
    public void completeReconstruction(String title, String summary, String reconstructedContent) {
        if (status != NewsStatus.PROCESSING) {
            throw new IllegalStateException("Only processing news can complete reconstruction");
        }

        validateSummary(summary);
        validateContent(reconstructedContent);

        if (isUsableAiTitle(title)) {
            this.title = title.strip();
        }
        this.summary = summary;
        this.content = reconstructedContent;
        this.status = NewsStatus.PENDING;
    }

    /** AI가 생성한 제목을 쓸 수 있는지 확인한다. 비었거나 길이를 넘거나 줄바꿈이 섞이면 원문 제목을 유지한다. */
    private static boolean isUsableAiTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        String stripped = title.strip();
        return stripped.length() <= MAX_AI_TITLE_LENGTH && !stripped.contains("\n") && !stripped.contains("\r");
    }

    private static void validateSummary(String summary) {
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("News summary must not be blank");
        }
        if (summary.length() > 255) {
            throw new IllegalArgumentException("News summary must be 255 characters or fewer");
        }
        if (summary.contains("\n") || summary.contains("\r")) {
            throw new IllegalArgumentException("News summary must be a single line");
        }
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("News content must not be blank");
        }
        if (content.length() > 1_000) {
            throw new IllegalArgumentException("News content must be 1000 characters or fewer");
        }
    }
}
