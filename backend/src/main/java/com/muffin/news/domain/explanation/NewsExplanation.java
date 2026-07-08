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
}
