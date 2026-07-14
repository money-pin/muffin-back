package com.muffin.sector.domain.sectorgroup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SectorGroupTest {

    @Test
    @DisplayName("생성하면 전달한 코드·이름·설명·순서가 그대로 채워진다")
    void create_fillsNameAndDescription() {
        SectorGroup sectorGroup = SectorGroup.create("FUTURE_TECH", "미래 기술&혁신", "미래 기술 관련 섹터 그룹", 2);

        assertEquals("FUTURE_TECH", sectorGroup.getGroupCode());
        assertEquals("미래 기술&혁신", sectorGroup.getName());
        assertEquals("미래 기술 관련 섹터 그룹", sectorGroup.getDescription());
        assertEquals(2, sectorGroup.getGroupOrder());
    }
}
