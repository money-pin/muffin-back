package com.muffin.sector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.muffin.global.apiPayload.exception.GeneralException;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.exception.SectorErrorCode;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.BusinessDay;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.Result;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.Session;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.Sessions;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TradingCalendarServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 10);
    private static final LocalDate PREVIOUS_DATE = LocalDate.of(2026, 7, 9);
    private static final LocalDate NEXT_DATE = LocalDate.of(2026, 7, 13);

    @Mock
    private TossMarketDataClient tossMarketDataClient;

    private TradingCalendarService tradingCalendarService;

    @BeforeEach
    void setUp() {
        tradingCalendarService = new TradingCalendarService(tossMarketDataClient);
    }

    @Test
    @DisplayName("정규장 세션이 있는 날짜는 거래일이며 직전·다음 거래일을 함께 반환한다")
    void getCalendar_returnsTradingDayInformation() {
        when(tossMarketDataClient.getMarketCalendar(DATE)).thenReturn(calendar(tradingBusinessDay(DATE)));

        TradingCalendar calendar = tradingCalendarService.getCalendar(DATE);

        assertTrue(calendar.tradingDay());
        assertEquals(PREVIOUS_DATE, calendar.previousTradingDay());
        assertEquals(NEXT_DATE, calendar.nextTradingDay());
    }

    @Test
    @DisplayName("정규장 세션이 없는 날짜는 휴장일로 반환한다")
    void getCalendar_returnsClosedDayInformation() {
        when(tossMarketDataClient.getMarketCalendar(DATE)).thenReturn(calendar(new BusinessDay(DATE, null)));

        TradingCalendar calendar = tradingCalendarService.getCalendar(DATE);

        assertFalse(calendar.tradingDay());
        assertEquals(NEXT_DATE, calendar.nextTradingDay());
    }

    @Test
    @DisplayName("응답의 today가 요청 날짜와 다르면 fail-closed 한다")
    void getCalendar_rejectsMismatchedDate() {
        when(tossMarketDataClient.getMarketCalendar(DATE)).thenReturn(calendar(tradingBusinessDay(DATE.minusDays(1))));

        GeneralException exception =
                assertThrows(GeneralException.class, () -> tradingCalendarService.getCalendar(DATE));

        assertEquals(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    @DisplayName("직전 거래일의 정규장 정보가 누락되면 fail-closed 한다")
    void getCalendar_rejectsIncompletePreviousTradingDay() {
        Result result = new Result(
                tradingBusinessDay(DATE), new BusinessDay(PREVIOUS_DATE, null), tradingBusinessDay(NEXT_DATE));
        when(tossMarketDataClient.getMarketCalendar(DATE)).thenReturn(result);

        GeneralException exception =
                assertThrows(GeneralException.class, () -> tradingCalendarService.getCalendar(DATE));

        assertEquals(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    @DisplayName("토스 캘린더 API 장애는 MARKET_CALENDAR_UNAVAILABLE로 변환한다")
    void getCalendar_mapsProviderFailure() {
        when(tossMarketDataClient.getMarketCalendar(DATE))
                .thenThrow(new TossApiException("request", "SERVER_ERROR", null, "provider failure"));

        GeneralException exception =
                assertThrows(GeneralException.class, () -> tradingCalendarService.getCalendar(DATE));

        assertEquals(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE, exception.getErrorCode());
    }

    private static Result calendar(BusinessDay today) {
        return new Result(today, tradingBusinessDay(PREVIOUS_DATE), tradingBusinessDay(NEXT_DATE));
    }

    private static BusinessDay tradingBusinessDay(LocalDate date) {
        Session regular = new Session(date + "T09:00:00+09:00", date + "T15:30:00+09:00");
        return new BusinessDay(date, new Sessions(null, regular, null));
    }
}
