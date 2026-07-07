package com.muffin.news.domain.category;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CategoryTest {

    @Test
    @DisplayName("카테고리 레코드를 생성한다")
    void create_createsCategory() {
        Category category = Category.create("경제", "https://example.com/fallback.png");

        assertEquals("경제", category.getName());
        assertEquals("https://example.com/fallback.png", category.getFallbackThumbnailUrl());
    }
}
