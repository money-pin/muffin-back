package com.muffin.news.domain.sectorimpact;

import com.muffin.global.entity.BaseEntity;
import com.muffin.news.domain.sectorimpact.enums.ImpactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 뉴스별 섹터 영향도. News와 Sector는 다른 애그리거트이므로 ID로만 참조한다. */
@Entity
@Getter
@Table(
        name = "news_sector_impact",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_news_sector_impact_news_sector",
                        columnNames = {"news_id", "sector_id"}))
// JPA가 엔티티를 조회할 때 기본 생성자가 필요해서 protected로 열어둔다.
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsSectorImpact extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_sector_impact_id")
    private Long id;

    @Column(name = "news_id", nullable = false)
    private Long newsId;

    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "impact", nullable = false, length = 20)
    private ImpactType impact;

    // 새 영향도 레코드를 만들 때 필요한 값만 받는 생성자다.
    private NewsSectorImpact(Long newsId, Long sectorId, ImpactType impact) {
        validate(newsId, sectorId, impact);
        this.newsId = newsId;
        this.sectorId = sectorId;
        this.impact = impact;
    }

    /** 뉴스 1건과 섹터 1건에 대한 영향도 결과를 생성한다. */
    public static NewsSectorImpact create(Long newsId, Long sectorId, ImpactType impact) {
        return new NewsSectorImpact(newsId, sectorId, impact);
    }

    private static void validate(Long newsId, Long sectorId, ImpactType impact) {
        if (newsId == null) {
            throw new IllegalArgumentException("newsId는 필수입니다.");
        }
        if (sectorId == null) {
            throw new IllegalArgumentException("sectorId는 필수입니다.");
        }
        if (impact == null) {
            throw new IllegalArgumentException("impact는 필수입니다.");
        }
    }
}
