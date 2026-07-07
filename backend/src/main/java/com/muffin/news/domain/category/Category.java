package com.muffin.news.domain.category;

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
        name = "category",
        uniqueConstraints = {@UniqueConstraint(name = "uk_category_name", columnNames = "name")})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "fallback_thumbnail_url", length = 500)
    private String fallbackThumbnailUrl;

    private Category(String name, String fallbackThumbnailUrl) {
        this.name = name;
        this.fallbackThumbnailUrl = fallbackThumbnailUrl;
    }

    /** 뉴스 분류에 사용할 카테고리 레코드를 생성한다. */
    public static Category create(String name, String fallbackThumbnailUrl) {
        return new Category(name, fallbackThumbnailUrl);
    }
}
