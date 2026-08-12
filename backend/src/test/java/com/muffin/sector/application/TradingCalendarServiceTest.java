package com.muffin.sector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.exception.SectorException;
import com.muffin.sector.domain.exception.code.SectorErrorCode;
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
import org.springframework.http.HttpStatus;

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

        SectorException exception = assertThrows(SectorException.class, () -> tradingCalendarService.getCalendar(DATE));

        assertEquals(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    @DisplayName("토스 캘린더 API 대기 중단은 MARKET_CALENDAR_UNAVAILABLE 503으로 변환한다")
    void getCalendar_mapsInterruptedProviderFailureToServiceUnavailable() {
        when(tossMarketDataClient.getMarketCalendar(DATE))
                .thenThrow(TossApiException.interrupted("provider wait interrupted", new InterruptedException()));

        SectorException exception = assertThrows(SectorException.class, () -> tradingCalendarService.getCalendar(DATE));

        assertEquals(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE, exception.getErrorCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getErrorCode().getHttpStatus());
        assertEquals("SECTOR_503_001", exception.getErrorCode().getCode());
    }

    @Test
    @DisplayName("직전 거래일의 정규장 정보가 누락되면 fail-closed 한다")
    void getCalendar_rejectsIncompletePreviousTradingDay() {
        Result result = new Result(
                tradingBusinessDay(DATE), new BusinessDay(PREVIOUS_DATE, null), tradingBusinessDay(NEXT_DATE));
        when(tossMarketDataClient.getMarketCalendar(DATE)).thenReturn(result);

        SectorException exception = assertThrows(SectorException.class, () -> tradingCalendarService.getCalendar(DATE));

        assertEquals(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE, exception.getErrorCode());
    }

    @Test
    @DisplayName("토스 캘린더 API 장애는 MARKET_CALENDAR_UNAVAILABLE로 변환한다")
    void getCalendar_mapsProviderFailure() {
        when(tossMarketDataClient.getMarketCalendar(DATE))
                .thenThrow(new TossApiException(
                        "request", "SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "provider failure"));

        SectorException exception = assertThrows(SectorException.class, () -> tradingCalendarService.getCalendar(DATE));

        assertEquals(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE, exception.getErrorCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getErrorCode().getHttpStatus());
        assertEquals("SECTOR_503_001", exception.getErrorCode().getCode());
    }

    private static Result calendar(BusinessDay today) {
        return new Result(today, tradingBusinessDay(PREVIOUS_DATE), tradingBusinessDay(NEXT_DATE));
    }

    private static BusinessDay tradingBusinessDay(LocalDate date) {
        Session regular = new Session(date + "T09:00:00+09:00", date + "T15:30:00+09:00");
        return new BusinessDay(date, new Sessions(null, regular, null));
    }
}
