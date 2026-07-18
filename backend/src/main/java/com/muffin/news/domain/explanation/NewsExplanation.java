package com.muffin.news.domain.explanation;

import com.muffin.global.entity.BaseEntity;
import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 뉴스 경제 상식 해설카드. News는 다른 애그리거트이므로 ID로만 참조한다. */
@Entity
@Getter
@Table(
        name = "news_explanation",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_news_explanation_news_order",
                        columnNames = {"news_id", "card_order"}))
// JPA가 엔티티를 조회할 때 기본 생성자가 필요해서 protected로 열어둔다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsExplanation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_explanation_id")
    private Long id;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "news_id", nullable = false)
    private Long newsId;

    @Column(name = "card_order", nullable = false)
    private int cardOrder;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "key_term", nullable = false)
    private String keyTerm;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NewsExplanationStatus status;

    // 새 해설카드를 만들 때 필요한 값만 받는 생성자다.
    private NewsExplanation(Long newsId, int cardOrder, String title, String content, String keyTerm) {
        validate(newsId, cardOrder, title, content, keyTerm);
        this.newsId = newsId;
        this.cardOrder = cardOrder;
        this.title = title;
        this.content = content;
        this.keyTerm = keyTerm;
        this.status = NewsExplanationStatus.PROCESSING;
    }

    /** 뉴스 상세 화면에 노출할 경제 상식 해설카드를 생성한다. */
    public static NewsExplanation create(Long newsId, int cardOrder, String title, String content, String keyTerm) {
        return new NewsExplanation(newsId, cardOrder, title, content, keyTerm);
    }

    /** AI 해설카드 생성이 정상 완료되었을 때 공개 가능한 상태로 변경한다. */
    public void complete() {
        if (status == NewsExplanationStatus.FAILED) {
            throw new IllegalStateException("실패한 해설카드는 완료 상태로 변경할 수 없습니다.");
        }
        this.status = NewsExplanationStatus.DONE;
    }

    /** AI 해설카드 생성 또는 저장 과정이 실패했을 때 실패 상태로 변경한다. */
    public void fail() {
        if (status == NewsExplanationStatus.DONE) {
            throw new IllegalStateException("완료된 해설카드는 실패 상태로 변경할 수 없습니다.");
        }
        this.status = NewsExplanationStatus.FAILED;
    }

    private static void validate(Long newsId, int cardOrder, String title, String content, String keyTerm) {
        if (newsId == null) {
            throw new IllegalArgumentException("newsId는 필수입니다.");
        }
        if (cardOrder < 0) {
            throw new IllegalArgumentException("cardOrder는 음수일 수 없습니다.");
        }
        if (isBlank(title)) {
            throw new IllegalArgumentException("title은 필수입니다.");
        }
        if (isBlank(content)) {
            throw new IllegalArgumentException("content는 필수입니다.");
        }
        if (isBlank(keyTerm)) {
            throw new IllegalArgumentException("keyTerm은 필수입니다.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
