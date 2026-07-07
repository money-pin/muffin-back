package com.muffin.news.domain.term;

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
        name = "term_dictionary",
        uniqueConstraints = {@UniqueConstraint(name = "uk_term_dictionary_term", columnNames = "term")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermDictionary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "term_id")
    private Long id;

    @Column(name = "term", nullable = false)
    private String term;

    @Column(name = "content", nullable = false)
    private String content;

    private TermDictionary(String term, String content) {
        this.term = term;
        this.content = content;
    }

    /** 서비스에서 설명할 금융 용어 사전 레코드를 생성한다. */
    public static TermDictionary create(String term, String content) {
        return new TermDictionary(term, content);
    }
}
