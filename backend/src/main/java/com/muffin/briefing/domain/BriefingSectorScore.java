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

    /** 변동 이유 한 줄의 최대 길이. 프롬프트가 요구하는 100자와 같은 값이며, 넘으면 이유 없이 등락률만 남긴다. */
    private static final int MAX_CHANGE_REASON_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "briefing_sector_score_id")
    private Long id;

    @Column(name = "sector_id", nullable = false)
    private Long sectorId;

    /** 소수점 둘째 자리까지의 등락률(%). 상승이면 양수, 하락이면 음수다. */
    @Column(name = "change_rate", nullable = false, precision = 7, scale = 2)
    private BigDecimal changeRate;

    /**
     * 이 섹터가 왜 그렇게 움직였는지 설명하는 1~2문장. AI가 그날의 뉴스 후보를 근거로 쓴다.
     *
     * <p>설명을 못 붙일 수 있어 nullable이다. 성적표의 본체는 등락률이고 설명은 덧붙는 맥락이라, 문장 하나 때문에 성적표
     * 블록을 통째로 비우는 쪽이 더 큰 손해다.
     */
    @Column(name = "change_reason", length = 255)
    private String changeReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "rank_type", nullable = false, length = 20)
    private SectorRankType rankType;

    @Column(name = "score_order", nullable = false)
    private int scoreOrder;

    private BriefingSectorScore(
            Long sectorId, BigDecimal changeRate, String changeReason, SectorRankType rankType, int scoreOrder) {
        this.sectorId = sectorId;
        this.changeRate = changeRate;
        this.changeReason = changeReason;
        this.rankType = rankType;
        this.scoreOrder = scoreOrder;
    }

    /**
     * 계산이 끝난 섹터 등락률을 브리핑 스냅샷으로 만든다.
     *
     * @param changeReason 변동 이유 한 줄. 비었거나 너무 길거나 줄바꿈이 섞이면 버리고 null로 저장한다
     */
    static BriefingSectorScore create(
            Long sectorId, BigDecimal changeRate, String changeReason, SectorRankType rankType, int scoreOrder) {
        if (sectorId == null) {
            throw new IllegalArgumentException("sectorId는 필수입니다.");
        }
        if (changeRate == null) {
            throw new IllegalArgumentException("changeRate는 필수입니다.");
        }
        if (rankType == null) {
            throw new IllegalArgumentException("rankType은 필수입니다.");
        }
        return new BriefingSectorScore(sectorId, changeRate, sanitizeChangeReason(changeReason), rankType, scoreOrder);
    }

    /**
     * 쓸 수 있는 변동 이유만 남기고 나머지는 버린다.
     *
     * <p>길이를 넘겼다고 잘라 붙이지 않는 이유는, 중간에서 끊긴 문장이 화면에 그대로 나가면 설명이 없는 것보다 나쁘기 때문이다.
     * 줄바꿈도 같은 이유로 거른다. 성적표 한 줄은 한 줄로 렌더링된다.
     */
    private static String sanitizeChangeReason(String changeReason) {
        if (changeReason == null || changeReason.isBlank()) {
            return null;
        }
        String stripped = changeReason.strip();
        if (stripped.length() > MAX_CHANGE_REASON_LENGTH) {
            return null;
        }
        if (stripped.contains("\n") || stripped.contains("\r")) {
            return null;
        }
        return stripped;
    }
}
