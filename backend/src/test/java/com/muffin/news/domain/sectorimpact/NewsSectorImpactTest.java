package com.muffin.news.domain.sectorimpact;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.muffin.news.domain.sectorimpact.enums.ImpactType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewsSectorImpactTest {

    @Test
    @DisplayName("뉴스 섹터 영향도를 생성할 수 있다")
    void create() {
        NewsSectorImpact impact = NewsSectorImpact.create(1L, 10L, ImpactType.POSITIVE);

        assertEquals(1L, impact.getNewsId());
        assertEquals(10L, impact.getSectorId());
        assertEquals(ImpactType.POSITIVE, impact.getImpact());
    }
}
