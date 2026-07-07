package com.muffin.news.domain.term;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TermDictionaryTest {

    @Test
    @DisplayName("용어 사전 레코드를 생성한다")
    void create_createsTermDictionary() {
        TermDictionary termDictionary = TermDictionary.create("기준금리", "중앙은행이 정하는 정책 금리");

        assertEquals("기준금리", termDictionary.getTerm());
        assertEquals("중앙은행이 정하는 정책 금리", termDictionary.getContent());
    }
}
