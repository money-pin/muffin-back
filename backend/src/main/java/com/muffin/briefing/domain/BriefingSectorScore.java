package com.muffin.briefing.domain;

import com.muffin.briefing.domain.enums.SectorRankType;
import com.muffin.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 어제의 섹터 성적표 한 줄.
 *
 * <p>등락률을 계산해 두고 저장하는 이유는 발행된 브리핑이 불변이어야 하기 때문이다. 조회 시점에 {@code etf_price}로 다시 계산하면
 * 섹터가 비활성화되거나 ETF가 교체됐을 때 과거 브리핑의 내용이 나중에 달라진다.
 */
@Entity
@Getter
@Table(
        name = "briefing_sector_score",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_briefing_sector_score_briefing_sector",
                        columnNames = {"briefing_id", "sector_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BriefingSectorScore extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "briefing_sector_score_id")
    private Long id;

    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    /** 소수점 둘째 자리까지의 등락률(%). 상승이면 양수, 하락이면 음수다. */
    @Column(name = "change_rate", nullable = false, precision = 7, scale = 2)
    private BigDecimal changeRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "rank_type", nullable = false, length = 20)
    private SectorRankType rankType;

    @Column(name = "score_order", nullable = false)
    private int scoreOrder;

    private BriefingSectorScore(Long sectorId, BigDecimal changeRate, SectorRankType rankType, int scoreOrder) {
        this.sectorId = sectorId;
        this.changeRate = changeRate;
        this.rankType = rankType;
        this.scoreOrder = scoreOrder;
    }

    /** 계산이 끝난 섹터 등락률을 브리핑 스냅샷으로 만든다. */
    static BriefingSectorScore create(Long sectorId, BigDecimal changeRate, SectorRankType rankType, int scoreOrder) {
        if (sectorId == null) {
            throw new IllegalArgumentException("sectorId는 필수입니다.");
        }
        if (changeRate == null) {
            throw new IllegalArgumentException("changeRate는 필수입니다.");
        }
        if (rankType == null) {
            throw new IllegalArgumentException("rankType은 필수입니다.");
        }
        return new BriefingSectorScore(sectorId, changeRate, rankType, scoreOrder);
    }
}
