package com.muffin.sector.domain.etf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EtfTest {

    @Test
    @DisplayName("생성하면 전달한 코드와 이름이 그대로 채워진다")
    void create_fillsCodeAndName() {
        Etf etf = Etf.create("091160", "KODEX 반도체");

        assertEquals("091160", etf.getEtfCode());
        assertEquals("KODEX 반도체", etf.getEtfName());
    }
}
