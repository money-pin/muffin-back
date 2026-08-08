package com.muffin.character.domain.characterrecommendedsector;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CharacterRecommendedSectorTest {

    @Test
    @DisplayName("생성하면 전달한 characterId·sectorId가 그대로 채워진다")
    void create_fillsCharacterIdAndSectorId() {
        CharacterRecommendedSector recommendedSector = CharacterRecommendedSector.create(1L, 2L);

        assertEquals(1L, recommendedSector.getCharacterId());
        assertEquals(2L, recommendedSector.getSectorId());
    }
}
