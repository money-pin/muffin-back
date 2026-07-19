package com.muffin.news.domain.explanation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertEquals("금리", explanation.getTitle());
        assertEquals("금리 해설", explanation.getContent());
        assertEquals("금리", explanation.getKeyTerm());
    }

    @Test
    @DisplayName("해설카드 생성 시 필수값이 없거나 카드 순서가 음수면 예외가 발생한다")
    void create_throwsWhenInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> NewsExplanation.create(null, 1, "금리", "금리 해설", "금리"));
        assertThrows(IllegalArgumentException.class, () -> NewsExplanation.create(1L, -1, "금리", "금리 해설", "금리"));
        assertThrows(IllegalArgumentException.class, () -> NewsExplanation.create(1L, 1, null, "금리 해설", "금리"));
        assertThrows(IllegalArgumentException.class, () -> NewsExplanation.create(1L, 1, " ", "금리 해설", "금리"));
        assertThrows(IllegalArgumentException.class, () -> NewsExplanation.create(1L, 1, "금리", null, "금리"));
        assertThrows(IllegalArgumentException.class, () -> NewsExplanation.create(1L, 1, "금리", " ", "금리"));
        assertThrows(IllegalArgumentException.class, () -> NewsExplanation.create(1L, 1, "금리", "금리 해설", null));
        assertThrows(IllegalArgumentException.class, () -> NewsExplanation.create(1L, 1, "금리", "금리 해설", " "));
    }

    @Test
    @DisplayName("해설카드 생성을 완료하면 DONE 상태가 된다")
    void complete_changesStatusToDone() {
        NewsExplanation explanation = NewsExplanation.create(1L, 1, "금리", "금리 해설", "금리");

        explanation.complete();

        assertEquals(NewsExplanationStatus.DONE, explanation.getStatus());
    }

    @Test
    @DisplayName("해설카드 생성이 실패하면 FAILED 상태가 된다")
    void fail_changesStatusToFailed() {
        NewsExplanation explanation = NewsExplanation.create(1L, 1, "금리", "금리 해설", "금리");

        explanation.fail();

        assertEquals(NewsExplanationStatus.FAILED, explanation.getStatus());
    }

    @Test
    @DisplayName("해설카드 생성 실패 마커는 FAILED 상태로 생성된다")
    void failed_createsFailedMarker() {
        NewsExplanation explanation = NewsExplanation.failed(1L);

        assertEquals(NewsExplanationStatus.FAILED, explanation.getStatus());
        assertEquals(1L, explanation.getNewsId());
        assertEquals(0, explanation.getCardOrder());
        assertEquals("해설카드 생성 실패", explanation.getTitle());
        assertEquals("해설카드 생성에 실패했습니다.", explanation.getContent());
        assertEquals("해설카드", explanation.getKeyTerm());
    }
}
