package com.muffin.stats.domain;

import java.util.List;

/**
 * 투자 성향(STATS-05). 사용자의 섹터 그룹별 누적 매수 비중으로 판정한다.
 *
 * <p>판정 우선순위: 공격투자형 → 성장추구형 → 안정추구형 → (나머지) 균형형. label/description/불릿2·3은 성향별 고정 문구이고, 불릿1(자산군 비중)은 조회 시점에
 * 조립한다.
 */
public enum InvestmentType {
    STABLE("안정추구형 투자자", "안정성을 최우선으로 하는 투자 스타일", "원금 보전을 중시하는 신중한 접근", "단기 변동에 흔들리지 않는 투자 습관"),
    BALANCED("균형형 투자자", "안정성과 수익성을 적절히 조합하는 투자 스타일", "리스크 관리를 중시하는 신중한 접근", "중장기적 관점의 포트폴리오 구성"),
    GROWTH("성장추구형 투자자", "성장 가능성에 주목하는 투자 스타일", "높은 수익률을 추구하는 적극적 접근", "변동성을 감내할 수 있는 투자 습관"),
    AGGRESSIVE("공격투자형 투자자", "높은 리스크를 감수하고 고수익을 추구하는 투자 스타일", "단기 고수익 기회를 적극적으로 포착", "높은 변동성을 감내하는 투자 습관");

    private final String label;
    private final String description;
    private final String bullet2;
    private final String bullet3;

    InvestmentType(String label, String description, String bullet2, String bullet3) {
        this.label = label;
        this.description = description;
        this.bullet2 = bullet2;
        this.bullet3 = bullet3;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** 불릿1(자산군 비중, 런타임 조립)을 앞에 붙여 성향 카드의 불릿 3개를 만든다. */
    public List<String> bullets(String assetRatioBullet) {
        return List.of(assetRatioBullet, bullet2, bullet3);
    }

    /**
     * 자산군 비중(정수 %)과 단일 섹터 최대 비중으로 성향을 판정한다.
     *
     * @param techRatio 기술주(FUTURE_TECH) 비중(%)
     * @param baseRatio 기초자산(BASE_ASSET) 비중(%)
     * @param maxSingleSectorRatio 12개 섹터 중 단일 섹터 최대 비중(%)
     */
    public static InvestmentType classify(int techRatio, int baseRatio, int maxSingleSectorRatio) {
        if (techRatio >= 70 || maxSingleSectorRatio >= 50) {
            return AGGRESSIVE;
        }
        if (techRatio >= 50) {
            return GROWTH;
        }
        if (baseRatio >= 60) {
            return STABLE;
        }
        return BALANCED;
    }
}
