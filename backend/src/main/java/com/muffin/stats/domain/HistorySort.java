package com.muffin.stats.domain;

import com.muffin.stats.domain.exception.StatsException;
import com.muffin.stats.domain.exception.code.StatsErrorCode;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.ToLongFunction;

/**
 * 누적 수익 내역의 섹터별 정렬 기준. 손익금(AMOUNT) 또는 손익률(RATE)을 오름/내림차순으로 정렬한다.
 *
 * <p>정렬 대상 타입에 의존하지 않도록, 정렬 키(손익금/손익률)를 뽑아내는 함수를 받아 {@link Comparator}를 만든다.
 */
public enum HistorySort {
    AMOUNT_DESC,
    AMOUNT_ASC,
    RATE_DESC,
    RATE_ASC;

    /** 값을 지정하지 않으면 수익률 높은순으로 본다. */
    public static final HistorySort DEFAULT = RATE_DESC;

    private static final String ALLOWED = "허용값: AMOUNT_DESC, AMOUNT_ASC, RATE_DESC, RATE_ASC";

    /**
     * 요청 문자열을 정렬 기준으로 해석한다. null/blank면 기본값({@link #DEFAULT})을 쓰고, 대소문자는 무시한다.
     *
     * @throws StatsException 허용되지 않는 값인 경우({@link StatsErrorCode#INVALID_SORT})
     */
    public static HistorySort from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        try {
            return HistorySort.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new StatsException(StatsErrorCode.INVALID_SORT, ALLOWED + " (입력: " + raw + ")");
        }
    }

    public <T> Comparator<T> comparator(ToLongFunction<T> amount, Function<T, BigDecimal> rate) {
        Comparator<T> byAmount = Comparator.comparingLong(amount);
        Comparator<T> byRate = Comparator.comparing(rate);
        return switch (this) {
            case AMOUNT_DESC -> byAmount.reversed();
            case AMOUNT_ASC -> byAmount;
            case RATE_DESC -> byRate.reversed();
            case RATE_ASC -> byRate;
        };
    }
}
