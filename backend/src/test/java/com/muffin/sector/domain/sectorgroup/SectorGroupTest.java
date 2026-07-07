package com.muffin.sector.domain.sectorgroup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SectorGroupTest {

    @Test
    @DisplayName("생성하면 전달한 이름과 설명이 그대로 채워진다")
    void create_fillsNameAndDescription() {
        SectorGroup sectorGroup = SectorGroup.create("반도체", "반도체 관련 섹터 그룹");

        assertEquals("반도체", sectorGroup.getName());
        assertEquals("반도체 관련 섹터 그룹", sectorGroup.getDescription());
    }
}
