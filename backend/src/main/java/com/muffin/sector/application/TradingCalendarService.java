package com.muffin.sector.application;

import com.muffin.global.config.CacheConfig;
import com.muffin.sector.domain.exception.SectorException;
import com.muffin.sector.domain.exception.code.SectorErrorCode;
import com.muffin.sector.infrastructure.toss.TossMarketDataClient;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.BusinessDay;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.Result;
import com.muffin.sector.infrastructure.toss.dto.TossMarketCalendarResponse.Session;
import com.muffin.sector.infrastructure.toss.exception.TossApiException;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** 토스증권 국내 마켓 캘린더를 검증하고 모의투자에서 사용할 거래일 정보로 변환한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingCalendarService {

    private final TossMarketDataClient tossMarketDataClient;

    /**
     * 요청 날짜의 거래일 여부와 직전·다음 거래일을 한 번의 외부 API 호출로 조회한다.
     *
     * <p>날짜별로 하루 종일 불변이고 모든 사용자에게 같은 값이라 캐시한다. 캐시 전에는 조회 API가 요청마다 토스를 호출해, 화면을 열 때마다
     * 외부 왕복이 응답시간을 지배했다. 호출 지점이 조회 2곳과 배치 5곳 이상이라 이 메서드 한 곳에 붙이면 전부 혜택을 본다.
     *
     * <p><b>예외는 캐시되지 않는다</b>: 스프링 캐시는 메서드가 예외를 던지면 저장하지 않으므로, 토스의 일시 장애가 TTL 동안 고정되지
     * 않는다. 다음 호출이 곧바로 재시도한다.
     */
    @Cacheable(cacheNames = CacheConfig.TRADING_CALENDAR, key = "#date")
    public TradingCalendar getCalendar(LocalDate date) {
        Result result;
        try {
            result = tossMarketDataClient.getMarketCalendar(date);
        } catch (TossApiException exception) {
            log.warn(
                    "거래일 API 호출에 실패했습니다. date={}, requestId={}, tossCode={}, httpStatus={}",
                    date,
                    exception.getRequestId(),
                    exception.getTossCode(),
                    exception.getHttpStatus());
            throw unavailable();
        }

        if (result == null || !isRequestedDay(result.today(), date)) {
            throw unavailable(date, "requested date mismatch");
        }

        BusinessDay previous = result.previousBusinessDay();
        BusinessDay next = result.nextBusinessDay();
        if (!isCompleteTradingDay(previous)
                || !isCompleteTradingDay(next)
                || !previous.date().isBefore(date)
                || !next.date().isAfter(date)) {
            throw unavailable(date, "previous or next trading day is incomplete");
        }

        BusinessDay today = result.today();
        boolean tradingDay = hasRegularMarket(today);
        if (tradingDay && !hasCompleteRegularMarket(today)) {
            throw unavailable(date, "regular market session is incomplete");
        }

        return new TradingCalendar(date, tradingDay, previous.date(), next.date());
    }

    private boolean isRequestedDay(BusinessDay businessDay, LocalDate requestedDate) {
        return businessDay != null && requestedDate != null && requestedDate.equals(businessDay.date());
    }

    private boolean isCompleteTradingDay(BusinessDay businessDay) {
        return businessDay != null && businessDay.date() != null && hasCompleteRegularMarket(businessDay);
    }

    private boolean hasRegularMarket(BusinessDay businessDay) {
        return businessDay.integrated() != null && businessDay.integrated().regularMarket() != null;
    }

    private boolean hasCompleteRegularMarket(BusinessDay businessDay) {
        if (!hasRegularMarket(businessDay)) {
            return false;
        }
        Session regularMarket = businessDay.integrated().regularMarket();
        return regularMarket.startTime() != null
                && !regularMarket.startTime().isBlank()
                && regularMarket.endTime() != null
                && !regularMarket.endTime().isBlank();
    }

    private SectorException unavailable() {
        return new SectorException(SectorErrorCode.MARKET_CALENDAR_UNAVAILABLE);
    }

    private SectorException unavailable(LocalDate date, String reason) {
        log.warn("거래일 API 응답을 사용할 수 없습니다. date={}, reason={}", date, reason);
        return unavailable();
    }

    public record TradingCalendar(
            LocalDate date, boolean tradingDay, LocalDate previousTradingDay, LocalDate nextTradingDay) {}
}
