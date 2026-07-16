package com.muffin.stats.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 저장된 sector_group.group_code를 코드로 해석하는 매핑을 검증한다. */
class SectorGroupCodeTest {

    @Test
    @DisplayName("group_code로 그룹 코드를 해석한다")
    void fromCode_resolvesCode() {
        assertEquals(SectorGroupCode.BASE_ASSET, SectorGroupCode.fromCode("BASE_ASSET"));
        assertEquals(SectorGroupCode.FUTURE_TECH, SectorGroupCode.fromCode("FUTURE_TECH"));
        assertEquals(SectorGroupCode.REAL_ECONOMY, SectorGroupCode.fromCode("REAL_ECONOMY"));
    }

    @Test
    @DisplayName("불릿에 노출할 표시명을 제공한다")
    void displayName_forBullet() {
        assertEquals("기초 자산", SectorGroupCode.BASE_ASSET.displayName());
        assertEquals("기술주", SectorGroupCode.FUTURE_TECH.displayName());
        assertEquals("실물 경제", SectorGroupCode.REAL_ECONOMY.displayName());
    }

    @Test
    @DisplayName("알 수 없는 group_code면 예외를 던져 매핑 누락을 드러낸다")
    void fromCode_throwsOnUnknown() {
        assertThrows(IllegalArgumentException.class, () -> SectorGroupCode.fromCode("CRYPTO_GROUP"));
    }
}
