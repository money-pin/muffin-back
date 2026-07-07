package com.muffin.news.domain.news;

import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "news_term",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_news_term_news_term",
                    columnNames = {"news_id", "term_id"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsTerm extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_term_id")
    private Long id;

    @Column(name = "term_id", nullable = false)
    private Long termId;

    private NewsTerm(Long termId) {
        this.termId = termId;
    }

    /** News 애그리거트 내부에서 뉴스와 용어의 연결 레코드를 생성한다. */
    public static NewsTerm create(Long termId) {
        return new NewsTerm(termId);
    }
}
