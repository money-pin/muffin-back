package com.muffin.stats.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.muffin.stats.domain.StatsPeriod.Window;
import com.muffin.stats.domain.exception.StatsException;
import com.muffin.stats.domain.exception.code.StatsErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 기간 탭이 date 문자열을 형식에 맞게 해석해 집계 윈도우를 만드는지 검증한다. */
class StatsPeriodTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 19); // 일요일

    @Test
    @DisplayName("DAY는 그 하루를 윈도우로 하고 date를 그대로 되돌려준다")
    void day_singleDayWindow() {
        Window window = StatsPeriod.DAY.resolve("2026-06-21", TODAY);

        assertEquals(LocalDate.of(2026, 6, 21), window.start());
        assertEquals(LocalDate.of(2026, 6, 21), window.end());
        assertEquals("2026-06-21", window.label());
    }

    @Test
    @DisplayName("WEEK은 ISO 주차의 월요일~일요일을 윈도우로 하고 YYYY-Www로 정규화한다")
    void week_isoWeekWindow() {
        Window window = StatsPeriod.WEEK.resolve("2026-W25", TODAY);

        assertEquals(DayOfWeek.MONDAY, window.start().getDayOfWeek());
        assertEquals(2026, window.start().get(IsoFields.WEEK_BASED_YEAR));
        assertEquals(25, window.start().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        assertEquals(window.start().plusDays(6), window.end());
        assertEquals("2026-W25", window.label());
    }

    @Test
    @DisplayName("MONTH는 그 달 1일~말일을 윈도우로 한다")
    void month_monthWindow() {
        Window window = StatsPeriod.MONTH.resolve("2026-06", TODAY);

        assertEquals(LocalDate.of(2026, 6, 1), window.start());
        assertEquals(LocalDate.of(2026, 6, 30), window.end());
        assertEquals("2026-06", window.label());
    }

    @Test
    @DisplayName("YEAR는 그 해 1/1~12/31을 윈도우로 한다")
    void year_yearWindow() {
        Window window = StatsPeriod.YEAR.resolve("2026", TODAY);

        assertEquals(LocalDate.of(2026, 1, 1), window.start());
        assertEquals(LocalDate.of(2026, 12, 31), window.end());
        assertEquals("2026", window.label());
    }

    @Test
    @DisplayName("ALL은 경계가 없고 date는 무시되어 label이 null이다")
    void all_unboundedWindow() {
        Window window = StatsPeriod.ALL.resolve("무시됨", TODAY);

        assertNull(window.start());
        assertNull(window.end());
        assertNull(window.label());
    }

    @Test
    @DisplayName("date가 없으면 각 탭의 현재(오늘 기준)로 처리한다")
    void resolve_defaultsToCurrentWindow() {
        assertEquals("2026-07-19", StatsPeriod.DAY.resolve(null, TODAY).label());
        assertEquals("2026-07", StatsPeriod.MONTH.resolve("  ", TODAY).label());
        assertEquals("2026", StatsPeriod.YEAR.resolve(null, TODAY).label());
        // 2026-07-19은 일요일이라 그 ISO 주(2026-W29)의 월요일은 7/13이다.
        Window week = StatsPeriod.WEEK.resolve(null, TODAY);
        assertEquals(LocalDate.of(2026, 7, 13), week.start());
        assertEquals(LocalDate.of(2026, 7, 19), week.end());
    }

    @Test
    @DisplayName("date 형식이 탭 규격과 맞지 않으면 INVALID_DATE_FORMAT 예외를 던진다")
    void resolve_invalidFormatThrows() {
        assertThrows(StatsException.class, () -> StatsPeriod.DAY.resolve("2026-06", TODAY));
        assertThrows(StatsException.class, () -> StatsPeriod.MONTH.resolve("2026-13", TODAY));
        assertThrows(StatsException.class, () -> StatsPeriod.YEAR.resolve("abc", TODAY));
        assertThrows(StatsException.class, () -> StatsPeriod.WEEK.resolve("2026-W99", TODAY));
        StatsException e = assertThrows(StatsException.class, () -> StatsPeriod.WEEK.resolve("2026-25", TODAY));
        assertEquals(StatsErrorCode.INVALID_DATE_FORMAT, e.getErrorCode());
    }

    @Test
    @DisplayName("탭과 date 형식이 서로 어긋나면(다른 탭 형식을 넣어도) INVALID_DATE_FORMAT을 던진다")
    void resolve_wrongTabFormatThrows() {
        assertThrows(StatsException.class, () -> StatsPeriod.DAY.resolve("2026-06", TODAY)); // 월 형식
        assertThrows(StatsException.class, () -> StatsPeriod.DAY.resolve("2026-W25", TODAY)); // 주 형식
        assertThrows(StatsException.class, () -> StatsPeriod.MONTH.resolve("2026", TODAY)); // 연 형식
        assertThrows(StatsException.class, () -> StatsPeriod.MONTH.resolve("2026-06-21", TODAY)); // 일 형식
        assertThrows(StatsException.class, () -> StatsPeriod.YEAR.resolve("2026-06", TODAY)); // 월 형식
        assertThrows(StatsException.class, () -> StatsPeriod.WEEK.resolve("2026-06", TODAY)); // 월 형식
    }

    @Test
    @DisplayName("from은 대소문자를 무시해 기간 탭으로 변환한다")
    void from_parsesCaseInsensitive() {
        assertEquals(StatsPeriod.MONTH, StatsPeriod.from("month"));
        assertEquals(StatsPeriod.ALL, StatsPeriod.from("  ALL "));
    }

    @Test
    @DisplayName("from은 값이 비어 있거나 허용되지 않으면 INVALID_PERIOD 예외를 던진다")
    void from_nullOrUnknownThrows() {
        StatsException missing = assertThrows(StatsException.class, () -> StatsPeriod.from(null));
        assertEquals(StatsErrorCode.INVALID_PERIOD, missing.getErrorCode());
        assertThrows(StatsException.class, () -> StatsPeriod.from("  "));
        assertThrows(StatsException.class, () -> StatsPeriod.from("MONTHLY"));
    }
}
