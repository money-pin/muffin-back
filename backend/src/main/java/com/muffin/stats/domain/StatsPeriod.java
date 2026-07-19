package com.muffin.stats.domain;

import com.muffin.stats.domain.exception.StatsException;
import com.muffin.stats.domain.exception.code.StatsErrorCode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.IsoFields;
import java.util.Locale;

/**
 * 누적 수익 내역 조회의 기간 탭. 각 탭은 조회 기준 시점(date)을 자신의 형식으로 해석해 집계 대상 윈도우 {@code [start, end]}를 만든다.
 */
public enum StatsPeriod {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ALL;

    /**
     * 집계 대상 윈도우. {@code start}/{@code end}가 null이면 해당 방향 경계가 없음을 뜻한다(ALL).
     * {@code label}은 응답에 되돌려줄 정규화된 date 문자열이며 ALL이면 null이다.
     */
    public record Window(LocalDate start, LocalDate end, String label) {}

    /** 허용되는 기간 탭 목록 안내 문구(에러 detail용). */
    private static final String ALLOWED = "허용값: DAY, WEEK, MONTH, YEAR, ALL";

    /**
     * 요청 문자열을 기간 탭으로 해석한다. 대소문자는 무시한다.
     *
     * @throws StatsException 값이 비어 있거나 허용되지 않는 경우({@link StatsErrorCode#INVALID_PERIOD})
     */
    public static StatsPeriod from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new StatsException(StatsErrorCode.INVALID_PERIOD, "period는 필수입니다. " + ALLOWED);
        }
        try {
            return StatsPeriod.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new StatsException(StatsErrorCode.INVALID_PERIOD, ALLOWED + " (입력: " + raw + ")");
        }
    }

    /**
     * 조회 기준 시점 문자열을 이 탭의 형식으로 해석해 윈도우를 만든다. {@code rawDate}가 null/blank면 {@code today} 기준 현재 윈도우를 쓴다.
     *
     * @throws StatsException date 형식이 이 탭의 규격과 맞지 않는 경우({@link StatsErrorCode#INVALID_DATE_FORMAT})
     */
    public Window resolve(String rawDate, LocalDate today) {
        boolean useCurrent = rawDate == null || rawDate.isBlank();
        return switch (this) {
            case DAY -> {
                LocalDate day = useCurrent ? today : parseDay(rawDate);
                yield new Window(day, day, day.toString());
            }
            case WEEK -> {
                LocalDate monday = useCurrent ? mondayOf(today) : parseWeekMonday(rawDate);
                yield new Window(monday, monday.plusDays(6), formatWeek(monday));
            }
            case MONTH -> {
                YearMonth month = useCurrent ? YearMonth.from(today) : parseMonth(rawDate);
                yield new Window(month.atDay(1), month.atEndOfMonth(), month.toString());
            }
            case YEAR -> {
                int year = useCurrent ? today.getYear() : parseYear(rawDate);
                yield new Window(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), String.valueOf(year));
            }
            case ALL -> new Window(null, null, null);
        };
    }

    private static LocalDate parseDay(String rawDate) {
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException e) {
            throw invalidDate("YYYY-MM-DD", rawDate);
        }
    }

    private static YearMonth parseMonth(String rawDate) {
        try {
            return YearMonth.parse(rawDate);
        } catch (DateTimeParseException e) {
            throw invalidDate("YYYY-MM", rawDate);
        }
    }

    private static int parseYear(String rawDate) {
        try {
            return Year.parse(rawDate).getValue();
        } catch (DateTimeParseException e) {
            throw invalidDate("YYYY", rawDate);
        }
    }

    /** {@code YYYY-Www}(ISO 주차)를 그 주의 월요일로 해석한다. */
    private static LocalDate parseWeekMonday(String rawDate) {
        int separator = rawDate.indexOf("-W");
        if (separator < 0) {
            throw invalidDate("YYYY-Www", rawDate);
        }
        try {
            int weekYear = Integer.parseInt(rawDate.substring(0, separator));
            int week = Integer.parseInt(rawDate.substring(separator + 2));
            // 1월 4일은 항상 그 주 기준 연도의 ISO 1주차에 속한다. 여기서 목표 주차/월요일로 이동한다.
            LocalDate monday = LocalDate.of(weekYear, 1, 4)
                    .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                    .with(ChronoField.DAY_OF_WEEK, DayOfWeek.MONDAY.getValue());
            if (monday.get(IsoFields.WEEK_BASED_YEAR) != weekYear
                    || monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != week) {
                throw invalidDate("YYYY-Www", rawDate);
            }
            return monday;
        } catch (NumberFormatException | java.time.DateTimeException e) {
            throw invalidDate("YYYY-Www", rawDate);
        }
    }

    private static StatsException invalidDate(String expectedFormat, String rawDate) {
        return new StatsException(
                StatsErrorCode.INVALID_DATE_FORMAT, "기대 형식: " + expectedFormat + " (입력: " + rawDate + ")");
    }

    private static LocalDate mondayOf(LocalDate date) {
        return date.with(ChronoField.DAY_OF_WEEK, DayOfWeek.MONDAY.getValue());
    }

    private static String formatWeek(LocalDate monday) {
        int weekYear = monday.get(IsoFields.WEEK_BASED_YEAR);
        int week = monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        return "%04d-W%02d".formatted(weekYear, week);
    }
}
