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

    /** 영향 이유 한 줄의 최대 길이. 프롬프트가 요구하는 60자와 같은 값이며, 넘으면 이유 없이 등급만 남긴다. */
    private static final int MAX_REASON_LENGTH = 60;

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

    /**
     * 이 기사의 어떤 내용 때문에 해당 섹터가 오를/내릴 것으로 보는지 설명하는 한 문장.
     *
     * <p>NEUTRAL이면 항상 null이다. 영향이 없다고 판단한 섹터에 이유를 붙이면 그 자체가 지어낸 말이 된다.
     */
    @Column(name = "reason", length = 255)
    private String reason;

    // 새 영향도 레코드를 만들 때 필요한 값만 받는 생성자다.
    private NewsSectorImpact(Long newsId, Long sectorId, ImpactType impact, String reason) {
        validate(newsId, sectorId, impact);
        this.newsId = newsId;
        this.sectorId = sectorId;
        this.impact = impact;
        this.reason = sanitizeReason(impact, reason);
    }

    /**
     * 뉴스 1건과 섹터 1건에 대한 영향도 결과를 생성한다.
     *
     * @param reason 영향 이유 한 줄. NEUTRAL이거나 비었거나 너무 길거나 줄바꿈이 섞이면 버리고 null로 저장한다
     */
    public static NewsSectorImpact create(Long newsId, Long sectorId, ImpactType impact, String reason) {
        return new NewsSectorImpact(newsId, sectorId, impact, reason);
    }

    /**
     * 쓸 수 있는 이유만 남기고 나머지는 버린다.
     *
     * <p>NEUTRAL을 먼저 걸러내는 이유는, 영향이 없다고 판단해 놓고 이유를 적는 것이 모순이기 때문이다. 모델이 습관적으로 채워
     * 넣어도 저장 단계에서 끊는다.
     *
     * <p>길이를 넘겼다고 잘라 붙이지 않는다. 중간에서 끊긴 문장이 화면에 나가면 설명이 없는 것보다 나쁘다.
     */
    private static String sanitizeReason(ImpactType impact, String reason) {
        if (impact == ImpactType.NEUTRAL || reason == null || reason.isBlank()) {
            return null;
        }
        String stripped = reason.strip();
        if (stripped.length() > MAX_REASON_LENGTH) {
            return null;
        }
        if (stripped.indexOf('\n') >= 0 || stripped.indexOf('\r') >= 0) {
            return null;
        }
        return stripped;
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
