package com.muffin.character.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.muffin.character.domain.enums.MuffinType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CharacterProfileTest {

    @Test
    @DisplayName("생성하면 전달한 타입·이름·설명·이미지URL이 그대로 채워진다")
    void create_fillsAllFields() {
        CharacterProfile character =
                CharacterProfile.create(MuffinType.PLAIN, "플레인 머핀", "기본 플레인 머핀 캐릭터", "https://example.com/plain.png");

        assertEquals(MuffinType.PLAIN, character.getMuffinType());
        assertEquals("플레인 머핀", character.getName());
        assertEquals("기본 플레인 머핀 캐릭터", character.getDescription());
        assertEquals("https://example.com/plain.png", character.getImageUrl());
    }
}
