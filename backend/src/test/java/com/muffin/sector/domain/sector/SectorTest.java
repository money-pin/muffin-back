package com.muffin.sector.domain.sector;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SectorTest {

    private static final Long SECTOR_GROUP_ID = 1L;
    private static final Long ETF_ID = 10L;

    @Test
    @DisplayName("생성하면 활성 상태로 초기화된다")
    void create_initializesActive() {
        Sector sector = createSector();

        assertTrue(sector.isActive());
    }

    @Test
    @DisplayName("비활성화하면 활성 상태가 false가 된다")
    void deactivate_setsActiveFalse() {
        Sector sector = createSector();

        sector.deactivate();

        assertFalse(sector.isActive());
    }

    @Test
    @DisplayName("이미 비활성 상태인 섹터를 다시 비활성화하면 예외가 발생한다")
    void deactivate_throwsWhenAlreadyInactive() {
        Sector sector = createSector();
        sector.deactivate();

        assertThrows(IllegalStateException.class, sector::deactivate);
    }

    @Test
    @DisplayName("비활성 상태인 섹터를 활성화하면 활성 상태가 true가 된다")
    void activate_setsActiveTrue() {
        Sector sector = createSector();
        sector.deactivate();

        sector.activate();

        assertTrue(sector.isActive());
    }

    @Test
    @DisplayName("이미 활성 상태인 섹터를 다시 활성화하면 예외가 발생한다")
    void activate_throwsWhenAlreadyActive() {
        Sector sector = createSector();

        assertThrows(IllegalStateException.class, sector::activate);
    }

    private Sector createSector() {
        return Sector.create(SECTOR_GROUP_ID, ETF_ID, "반도체", "반도체 섹터", "SEC-001");
    }
}
