package com.muffin.news.domain.readhistory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReadHistoryTest {

    @Test
    @DisplayName("열람 기록을 생성하면 사용자/뉴스가 설정되고 열람 시각이 채워진다")
    void create_setsUserNewsAndReadAt() {
        ReadHistory history = ReadHistory.create(1L, 1024L);

        assertEquals(1L, history.getUserId());
        assertEquals(1024L, history.getNewsId());
        assertNotNull(history.getReadAt());
    }

    @Test
    @DisplayName("재열람하면 열람 시각이 갱신된다")
    void updateReadAt_refreshesReadAt() {
        ReadHistory history = ReadHistory.create(1L, 1024L);
        LocalDateTime before = history.getReadAt();

        history.updateReadAt();

        assertNotNull(history.getReadAt());
        assertFalse(history.getReadAt().isBefore(before));
    }
}
