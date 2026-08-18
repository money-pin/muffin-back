package com.muffin.sector.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.muffin.global.config.CacheConfig;
import com.muffin.sector.application.TradingCalendarService.TradingCalendar;
import com.muffin.sector.domain.exception.SectorException;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.BusinessDay;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.Result;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.Session;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.Sessions;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.time.LocalDate;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 거래일 캘린더 캐시 동작 검증. 캐시는 프록시가 적용해 주는 것이라 순수 Mockito 테스트({@link TradingCalendarServiceTest})로는
 * 확인되지 않는다. 스프링 컨텍스트를 띄워 실제 호출 횟수를 센다.
 */
@SpringBootTest
@ActiveProfiles("test")
class TradingCalendarCacheTest {

    private static final LocalDate DATE = LocalDate.of(2026, 7, 10);
    private static final LocalDate OTHER_DATE = LocalDate.of(2026, 7, 13);
    private static final LocalDate PREVIOUS_DATE = LocalDate.of(2026, 7, 9);
    private static final LocalDate NEXT_DATE = LocalDate.of(2026, 7, 13);

    @MockitoBean
    private TossMarketDataClient tossMarketDataClient;

    @Autowired
    private TradingCalendarService tradingCalendarService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        Objects.requireNonNull(cacheManager.getCache(CacheConfig.TRADING_CALENDAR))
                .clear();
        reset(tossMarketDataClient);
    }

    @Test
    @DisplayName("같은 날짜를 여러 번 조회해도 외부 API는 한 번만 호출한다")
    void getCalendar_callsExternalApiOnlyOnceForSameDate() {
        when(tossMarketDataClient.getMarketCalendar(DATE)).thenReturn(calendar(tradingBusinessDay(DATE)));

        TradingCalendar first = tradingCalendarService.getCalendar(DATE);
        TradingCalendar second = tradingCalendarService.getCalendar(DATE);
        TradingCalendar third = tradingCalendarService.getCalendar(DATE);

        verify(tossMarketDataClient, times(1)).getMarketCalendar(DATE);
        assertEquals(first, second);
        assertEquals(first, third);
    }

    @Test
    @DisplayName("날짜가 다르면 캐시가 갈라져 각각 외부 API를 호출한다")
    void getCalendar_callsExternalApiPerDistinctDate() {
        when(tossMarketDataClient.getMarketCalendar(DATE)).thenReturn(calendar(tradingBusinessDay(DATE)));
        when(tossMarketDataClient.getMarketCalendar(OTHER_DATE))
                .thenReturn(calendar(tradingBusinessDay(OTHER_DATE), OTHER_DATE));

        tradingCalendarService.getCalendar(DATE);
        tradingCalendarService.getCalendar(OTHER_DATE);
        tradingCalendarService.getCalendar(DATE);

        verify(tossMarketDataClient, times(1)).getMarketCalendar(DATE);
        verify(tossMarketDataClient, times(1)).getMarketCalendar(OTHER_DATE);
    }

    @Test
    @DisplayName("외부 API 실패는 캐시되지 않아 다음 호출이 곧바로 재시도한다")
    void getCalendar_doesNotCacheFailure() {
        when(tossMarketDataClient.getMarketCalendar(DATE))
                .thenThrow(new TossApiException(
                        "request", "SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "provider failure"))
                .thenReturn(calendar(tradingBusinessDay(DATE)));

        assertThrows(SectorException.class, () -> tradingCalendarService.getCalendar(DATE));
        TradingCalendar recovered = tradingCalendarService.getCalendar(DATE);

        verify(tossMarketDataClient, times(2)).getMarketCalendar(DATE);
        assertEquals(DATE, recovered.date());
    }

    private Result calendar(BusinessDay today) {
        return calendar(today, DATE);
    }

    private Result calendar(BusinessDay today, LocalDate baseDate) {
        return new Result(today, tradingBusinessDay(PREVIOUS_DATE), tradingBusinessDay(nextOf(baseDate)));
    }

    private LocalDate nextOf(LocalDate baseDate) {
        return baseDate.equals(DATE) ? NEXT_DATE : baseDate.plusDays(1);
    }

    private BusinessDay tradingBusinessDay(LocalDate date) {
        Session regular = new Session(date + "T09:00:00+09:00", date + "T15:30:00+09:00");
        return new BusinessDay(date, new Sessions(null, regular, null));
    }
}
