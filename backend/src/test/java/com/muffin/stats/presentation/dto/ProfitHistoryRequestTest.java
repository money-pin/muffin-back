package com.muffin.stats.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.muffin.stats.domain.HistorySort;
import com.muffin.stats.domain.StatsPeriod;
import com.muffin.stats.domain.exception.StatsException;
import com.muffin.stats.domain.exception.code.StatsErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** period/sort는 쿼리 파라미터 문자열 그대로 바인딩되고, toPeriod/toSort가 도메인 enum의 from()으로 파싱/검증한다. */
class ProfitHistoryRequestTest {

    @Test
    @DisplayName("period 문자열을 대소문자 무시하고 StatsPeriod로 변환한다")
    void toPeriod_parsesCaseInsensitively() {
        ProfitHistoryRequest request = new ProfitHistoryRequest("month", "2026-06", null);

        assertThat(request.toPeriod()).isEqualTo(StatsPeriod.MONTH);
    }

    @Test
    @DisplayName("period가 없으면(null) STATS_400_001 예외를 던진다")
    void toPeriod_throwsWhenMissing() {
        ProfitHistoryRequest request = new ProfitHistoryRequest(null, null, null);

        assertThatThrownBy(request::toPeriod)
                .isInstanceOf(StatsException.class)
                .extracting("errorCode")
                .isEqualTo(StatsErrorCode.INVALID_PERIOD);
    }

    @Test
    @DisplayName("허용되지 않는 period 값이면 STATS_400_001 예외를 던진다")
    void toPeriod_throwsWhenInvalid() {
        ProfitHistoryRequest request = new ProfitHistoryRequest("MONTHLY", null, null);

        assertThatThrownBy(request::toPeriod)
                .isInstanceOf(StatsException.class)
                .extracting("errorCode")
                .isEqualTo(StatsErrorCode.INVALID_PERIOD);
    }

    @Test
    @DisplayName("sort 문자열을 대소문자 무시하고 HistorySort로 변환한다")
    void toSort_parsesCaseInsensitively() {
        ProfitHistoryRequest request = new ProfitHistoryRequest("MONTH", null, "amount_asc");

        assertThat(request.toSort()).isEqualTo(HistorySort.AMOUNT_ASC);
    }

    @Test
    @DisplayName("sort가 없으면(null) 기본값 RATE_DESC를 반환한다")
    void toSort_defaultsWhenMissing() {
        ProfitHistoryRequest request = new ProfitHistoryRequest("MONTH", null, null);

        assertThat(request.toSort()).isEqualTo(HistorySort.DEFAULT);
    }

    @Test
    @DisplayName("허용되지 않는 sort 값이면 STATS_400_002 예외를 던진다")
    void toSort_throwsWhenInvalid() {
        ProfitHistoryRequest request = new ProfitHistoryRequest("MONTH", null, "BOGUS");

        assertThatThrownBy(request::toSort)
                .isInstanceOf(StatsException.class)
                .extracting("errorCode")
                .isEqualTo(StatsErrorCode.INVALID_SORT);
    }
}
