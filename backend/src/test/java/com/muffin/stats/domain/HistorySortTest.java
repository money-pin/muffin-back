package com.muffin.stats.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.stats.domain.exception.StatsException;
import com.muffin.stats.domain.exception.code.StatsErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 정렬 기준 파싱(기본값/대소문자/허용값)을 검증한다. */
class HistorySortTest {

    @Test
    @DisplayName("from은 값이 없으면 기본값(RATE_DESC)을 쓰고 대소문자를 무시한다")
    void from_defaultsAndCaseInsensitive() {
        assertEquals(HistorySort.RATE_DESC, HistorySort.from(null));
        assertEquals(HistorySort.RATE_DESC, HistorySort.from("  "));
        assertEquals(HistorySort.AMOUNT_ASC, HistorySort.from("amount_asc"));
    }

    @Test
    @DisplayName("from은 허용되지 않는 값이면 INVALID_SORT 예외를 던진다")
    void from_unknownThrows() {
        StatsException e = assertThrows(StatsException.class, () -> HistorySort.from("PRICE_DESC"));
        assertEquals(StatsErrorCode.INVALID_SORT, e.getErrorCode());
    }
}
