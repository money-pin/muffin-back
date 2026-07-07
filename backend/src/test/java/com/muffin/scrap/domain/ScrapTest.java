package com.muffin.scrap.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScrapTest {

    @Test
    @DisplayName("스크랩을 생성하면 사용자/뉴스가 설정된다")
    void create_setsUserAndNews() {
        Scrap scrap = Scrap.create(1L, 1024L);

        assertEquals(1L, scrap.getUserId());
        assertEquals(1024L, scrap.getNewsId());
    }
}
