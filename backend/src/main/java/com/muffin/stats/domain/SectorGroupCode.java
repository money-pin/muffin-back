package com.muffin.stats.domain;

/**
 * 섹터 그룹(자산군) 코드. sector_group.group_code 값과 enum 이름이 일치하며, {@code displayName}은 투자 성향 카드 불릿에 노출되는 표시명이다.
 */
public enum SectorGroupCode {
    BASE_ASSET("기초 자산"),
    FUTURE_TECH("기술주"),
    REAL_ECONOMY("실물 경제");

    private final String displayName;

    SectorGroupCode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 저장된 sector_group.group_code를 코드로 해석한다. 알 수 없는 그룹이면 예외를 던져 매핑 누락을 조기에 드러낸다. */
    public static SectorGroupCode fromCode(String groupCode) {
        try {
            return valueOf(groupCode);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 섹터 그룹입니다: " + groupCode, e);
        }
    }
}
