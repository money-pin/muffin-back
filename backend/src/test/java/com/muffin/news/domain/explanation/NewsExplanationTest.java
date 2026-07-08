package com.muffin.news.domain.explanation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.muffin.news.domain.explanation.enums.NewsExplanationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewsExplanationTest {

    @Test
    @DisplayName("해설카드를 생성하면 PROCESSING 상태가 된다")
    void create_setsProcessingStatus() {
        NewsExplanation explanation = NewsExplanation.create(1L, 1, "금리", "금리 해설", "금리");

        assertEquals(NewsExplanationStatus.PROCESSING, explanation.getStatus());
        assertEquals(1L, explanation.getNewsId());
        assertEquals(1, explanation.getCardOrder());
    }
}
