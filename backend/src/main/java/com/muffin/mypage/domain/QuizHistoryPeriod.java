package com.muffin.mypage.domain;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 연/월로부터 퀴즈 참여 내역 조회 범위를 계산하는 순수 로직.
 *
 * <p>month가 1~12 범위를 벗어나면 {@link YearMonth#of(int, int)}가 던지는
 * {@link java.time.DateTimeException}을 그대로 전파한다.
 */
public final class QuizHistoryPeriod {

    private QuizHistoryPeriod() {}

    public record Range(LocalDate start, LocalDate end) {}

    public static Range resolve(int year, int month) {
        YearMonth yearMonth = YearMonth.of(year, month);
        return new Range(yearMonth.atDay(1), yearMonth.atEndOfMonth());
    }
}
